package io.awarebridge.android;

import android.annotation.SuppressLint;
import android.content.*;
import android.net.*;
import android.net.wifi.aware.*;
import android.os.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import android.util.Log;
import java.util.*;

/** Explicitly selected peer, PIN keypad initiator, then NDP/UDP initiator. */
@SuppressLint("MissingPermission")
final class SubscriberController implements BridgeController {
    interface Observer extends AwareController.Observer {
        void peers(List<Peer> peers);
        void pinRequested(Peer peer);
    }

    static final class Peer {
        final PeerHandle handle;
        String name;
        boolean paired;
        private final Map<String, String> deviceInfo = new LinkedHashMap<>();

        Peer(PeerHandle handle, byte[] info) {
            this.handle = handle;
            updateInfo(info);
        }

        void updateInfo(byte[] info) {
            Map<String, String> received = PeerDeviceInfo.parse(info);
            deviceInfo.putAll(received);
            name = deviceInfo.getOrDefault("pairingName",
                    deviceInfo.getOrDefault("modelName", "iOS 设备（未提供名称）"));
            Log.d("AwareBridge", "Peer metadata: peer=" + handle.hashCode()
                    + ", SSI bytes=" + (info == null ? 0 : info.length)
                    + ", fields=" + received.keySet());
        }

        @Override public String toString() {
            StringBuilder label = new StringBuilder(name);
            for (String key : Arrays.asList("modelName", "vendorName")) {
                String value = deviceInfo.get(key);
                if (value != null && !value.equals(name)) label.append(" · ").append(value);
            }
            label.append(" #").append(handle.hashCode());
            if (paired) label.append(" · 已配对");
            return label.toString();
        }
    }

    // Shared application port; some ROMs omit the peer port from WifiAwareNetworkInfo.
    private static final int IOS_UDP_PORT = 45454;
    private static final int CIPHER = Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_128;
    private static final int KEYPAD = AwarePairingConfig.PAIRING_BOOTSTRAPPING_PIN_CODE_KEYPAD;
    private final Context context;
    private final Observer observer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final WifiAwareManager manager;
    private final ConnectivityManager connectivity;
    private final Map<PeerHandle, Peer> peers = new LinkedHashMap<>();
    private WifiAwareSession aware;
    private SubscribeDiscoverySession subscriber;
    private ConnectivityManager.NetworkCallback networkCallback;
    private HeartbeatTransport transport;
    private Network network;
    private WifiAwareNetworkInfo networkInfo;
    private Peer pendingPair;
    private boolean closed, registered, networkReady;
    private final Runnable pairingTimeout = () -> endPairing("配对超时，请重新打开 iOS 配对页面后重试");

    SubscriberController(Context context, Observer observer) {
        this.context = context;
        this.observer = observer;
        manager = context.getSystemService(WifiAwareManager.class);
        connectivity = context.getSystemService(ConnectivityManager.class);
    }

    private final BroadcastReceiver availability = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent intent) {
            if (!closed && !manager.isAvailable()) fail("Wi-Fi Aware 已不可用，请检查 Wi-Fi 和定位");
        }
    };

    @Override public void start() {
        if (closed) return;
        try {
            if (manager == null || !manager.isAvailable()) {
                fail("Wi-Fi Aware 不可用，请检查 Wi-Fi、定位与设备支持情况");
                return;
            }
            if (!manager.getCharacteristics().isAwarePairingSupported()) {
                fail("设备不支持 Wi-Fi Aware 配对");
                return;
            }
            context.registerReceiver(availability,
                    new IntentFilter(WifiAwareManager.ACTION_WIFI_AWARE_STATE_CHANGED),
                    Context.RECEIVER_NOT_EXPORTED);
            registered = true;
            observer.status("正在启动订阅…");
            manager.attach(new AttachCallback() {
                @Override public void onAttached(WifiAwareSession session) {
                    if (closed) { session.close(); return; }
                    aware = session;
                    try {
                        subscribe(pairingConfig());
                    } catch (RuntimeException | LinkageError e) {
                        fail("启动订阅失败：" + e.getMessage());
                    }
                }
                @Override public void onAttachFailed() { fail("Aware attach 失败"); }
                @Override public void onAwareSessionTerminated() { fail("Aware 会话已终止"); }
            }, handler);
        } catch (RuntimeException | LinkageError e) {
            fail("启动订阅失败：" + e.getMessage());
        }
    }

    private AwarePairingConfig pairingConfig() {
        return new AwarePairingConfig.Builder()
                .setPairingSetupEnabled(true)
                .setPairingVerificationEnabled(true)
                .setPairingCacheEnabled(true)
                .setBootstrappingMethods(KEYPAD)
                .setSupportedCipherSuites(CIPHER)
                .build();
    }

    private void subscribe(AwarePairingConfig pairing) {
        SubscribeConfig.Builder config = new SubscribeConfig.Builder()
                .setServiceName(AwareController.SERVICE)
                .setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE)
                .setPairingConfig(pairing)
                .setTerminateNotificationEnabled(true);
        // Ordinary Subscribe frames do not carry pairing device TXT metadata.
        // Subscriber identity belongs in the pairing bootstrap request.
        aware.subscribe(config.build(), discovery, handler);
    }

    private final DiscoverySessionCallback discovery = new DiscoverySessionCallback() {
        @Override public void onSubscribeStarted(SubscribeDiscoverySession session) {
            if (closed) { session.close(); return; }
            subscriber = session;
            observer.status("正在发现 iOS 服务；首次配对请在 iOS 打开允许配对");
        }
        @Override public void onServiceDiscovered(PeerHandle handle, byte[] info, List<byte[]> filter) {
            found(handle, info, null);
        }
        @Override public void onServiceDiscovered(ServiceDiscoveryInfo info) {
            found(info.getPeerHandle(), info.getServiceSpecificInfo(), info.getPairedAlias());
        }
        @Override public void onServiceLost(PeerHandle handle, int reason) {
            if (closed) return;
            Peer removed = peers.remove(handle);
            if (removed == pendingPair) endPairing("所选服务已离开，请重新发现");
            observer.peers(new ArrayList<>(peers.values()));
        }
        @Override public void onBootstrappingSucceeded(PeerHandle handle, int method) {
            if (closed || pendingPair == null || !pendingPair.handle.equals(handle)) return;
            if ((method & (KEYPAD | AwarePairingConfig.PAIRING_BOOTSTRAPPING_PIN_CODE_DISPLAY)) == 0) {
                endPairing("对端未选择 PIN 配对方式");
                return;
            }
            observer.status("请输入 iOS 显示的配对代码");
            observer.pinRequested(pendingPair);
        }
        @Override public void onBootstrappingFailed(PeerHandle handle) {
            if (pendingPair != null && pendingPair.handle.equals(handle)) endPairing("配对方式协商失败，请重试");
        }
        @Override public void onPairingSetupRequestReceived(PeerHandle handle, int id) {
            if (closed || subscriber == null) return;
            try { subscriber.rejectPairingRequest(id, handle); }
            catch (RuntimeException e) { observer.status("拒绝非主动配对请求失败：" + e.getMessage()); }
        }
        @Override public void onPairingSetupSucceeded(PeerHandle handle, String alias) {
            if (closed) return;
            Peer peer = peers.get(handle);
            if (peer != null) peer.paired = true;
            endPairing("配对成功，选择设备后点击连接");
            observer.peers(new ArrayList<>(peers.values()));
        }
        @Override public void onPairingSetupFailed(PeerHandle handle) {
            if (pendingPair != null && pendingPair.handle.equals(handle)) endPairing("配对失败，请核对代码后重试");
        }
        @Override public void onPairingVerificationSucceed(PeerHandle handle, String alias) {
            if (closed) return;
            Peer peer = peers.get(handle);
            if (peer != null) peer.paired = true;
            observer.peers(new ArrayList<>(peers.values()));
            observer.status("已验证缓存配对，可点击连接");
        }
        @Override public void onPairingVerificationFailed(PeerHandle handle) {
            if (closed) return;
            Peer peer = peers.get(handle);
            if (peer != null) peer.paired = false;
            observer.peers(new ArrayList<>(peers.values()));
            observer.status("缓存配对验证失败，请重新配对");
        }
        @Override public void onSessionConfigFailed() {
            if (closed) return;
            fail("订阅配置失败，请检查 ROM 的 Aware 支持");
        }
        @Override public void onSessionTerminated() { fail("订阅已终止，请重新开始"); }
    };

    private void found(PeerHandle handle, byte[] info, String alias) {
        if (closed) return;
        Peer peer = peers.get(handle);
        if (peer == null) {
            peer = new Peer(handle, info);
            peers.put(handle, peer);
        } else {
            peer.updateInfo(info);
        }
        if (alias != null && !alias.isEmpty()) peer.paired = true;
        observer.peers(new ArrayList<>(peers.values()));
    }

    void pair(Peer peer) {
        if (closed || subscriber == null || pendingPair != null || networkCallback != null
                || peers.get(peer.handle) != peer) return;
        try {
            pendingPair = peer;
            handler.postDelayed(pairingTimeout, 90_000);
            observer.status("正在协商配对方式，请保持 iOS 系统配对页面打开");
            // iOS expects the initiator's Keypad role in the bootstrap request.
            // On the tested ROM this value reaches the wire unchanged; Display is rejected.
            byte[] message = DeviceInfo.buildBootstrappingMessage(context);
            Log.d("AwareBridge", "Bootstrap request: API 37, TXT TLV bytes=" + message.length);
            subscriber.initiateBootstrappingRequest(peer.handle,
                    AwarePairingConfig.PAIRING_BOOTSTRAPPING_PIN_CODE_KEYPAD,
                    message);
        } catch (RuntimeException e) { endPairing("无法发起配对：" + e.getMessage()); }
    }

    void submitPin(Peer peer, String pin) {
        if (closed || pendingPair != peer || subscriber == null) return;
        try {
            subscriber.initiatePairingRequest(peer.handle, "awarebridge-ios-" + peer.handle.hashCode(), CIPHER, pin);
            observer.status("正在配对…");
        } catch (RuntimeException e) { endPairing("无法提交配对代码：" + e.getMessage()); }
    }

    void cancelPairing() { if (!closed) endPairing("已取消本次配对"); }

    private void endPairing(String status) {
        if (closed) return;
        pendingPair = null;
        handler.removeCallbacks(pairingTimeout);
        observer.pin("");
        observer.status(status);
    }

    void connect(Peer peer) {
        if (closed || subscriber == null || networkCallback != null || pendingPair != null
                || peers.get(peer.handle) != peer) return;
        if (!peer.paired) { observer.status("请先配对所选 iOS 设备"); return; }
        try {
            transport = new HeartbeatTransport(new HeartbeatTransport.Observer() {
                @Override public void onStats(long tx, long rx, long age, String remote, String text) {
                    handler.post(() -> { if (!closed) observer.stats(tx, rx, age, remote, text); });
                }
                @Override public void onError(String error) {
                    handler.post(() -> { if (!closed) fail(error); });
                }
            });
            WifiAwareNetworkSpecifier specifier = new WifiAwareNetworkSpecifier.Builder(subscriber, peer.handle)
                    .build();
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network value) {
                    if (closed) return;
                    network = value;
                    startTransport();
                }
                @Override public void onCapabilitiesChanged(Network value, NetworkCapabilities caps) {
                    if (closed) return;
                    if (caps.getTransportInfo() instanceof WifiAwareNetworkInfo) {
                        networkInfo = (WifiAwareNetworkInfo) caps.getTransportInfo();
                        startTransport();
                    }
                }
                @Override public void onLost(Network value) { fail("数据链路已断开，请重新订阅并连接"); }
                @Override public void onUnavailable() { fail("数据链路请求失败，请在 iOS 开始发布后重试"); }
            };
            NetworkRequest request = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
                    .setNetworkSpecifier(specifier)
                    .build();
            connectivity.requestNetwork(request, networkCallback, handler, 30_000);
            observer.status("正在建立数据链路并获取 iOS UDP 端口…");
            handler.postDelayed(() -> {
                if (!closed && !networkReady) fail("未获取到 iOS 数据链路或 UDP 地址/端口，请重新订阅");
            }, 35_000);
        } catch (IOException | RuntimeException e) { fail("连接请求失败：" + e.getMessage()); }
    }

    private void startTransport() {
        if (closed || networkReady || network == null || networkInfo == null
                || networkInfo.getPeerIpv6Addr() == null) return;
        if (networkInfo.getTransportProtocol() != -1 && networkInfo.getTransportProtocol() != 17) {
            fail("iOS 通知的服务不是 UDP，无法启动心跳");
            return;
        }
        try {
            transport.start(network, new InetSocketAddress(networkInfo.getPeerIpv6Addr(),
                    networkInfo.getPort() > 0 ? networkInfo.getPort() : IOS_UDP_PORT));
            networkReady = true;
            observer.status("数据链路已建立，正在向 iOS 发送心跳");
        } catch (IOException e) { fail("绑定 Aware 网络失败：" + e.getMessage()); }
    }

    private void fail(String status) {
        if (closed) return;
        close();
        observer.status(status);
        observer.stopped();
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        handler.removeCallbacksAndMessages(null);
        pendingPair = null;
        observer.pin("");
        observer.pairing(false, false);
        if (networkCallback != null) {
            try { connectivity.unregisterNetworkCallback(networkCallback); }
            catch (IllegalArgumentException ignored) { }
            networkCallback = null;
        }
        if (transport != null) transport.close();
        if (subscriber != null) subscriber.close();
        if (aware != null) aware.close();
        if (registered) context.unregisterReceiver(availability);
        peers.clear();
        observer.peers(Collections.emptyList());
    }
}
