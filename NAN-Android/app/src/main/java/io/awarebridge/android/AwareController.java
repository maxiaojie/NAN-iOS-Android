package io.awarebridge.android;

import android.annotation.SuppressLint;
import android.content.*;
import android.net.*;
import android.net.wifi.OuiKeyedData;
import android.net.wifi.aware.*;
import android.os.*;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.*;

/** Android publishes; iOS pairs and initiates the data path. Main-thread owned. */
@SuppressLint("MissingPermission")
final class AwareController implements AutoCloseable {
    static final String SERVICE = "_aware-bridge._udp";
    // Retained from the working ROM experiment. This is a demo, not a production secret.
    private static final String DEMO_PASSPHRASE = "1234567890";
    private static final int CIPHER = Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_128;
    interface Observer {
        void status(String status);
        void pin(String pin);
        void stats(long tx, long rx, long ageMs, String peer, String text);
        void stopped();
    }
    private final Context context;
    private final Observer observer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final WifiAwareManager manager;
    private final ConnectivityManager connectivity;
    private final Map<PeerHandle, String> pins = new HashMap<>();
    private final Map<PeerHandle, Runnable> pinTimeouts = new HashMap<>();
    private WifiAwareSession aware;
    private PublishDiscoverySession publisher;
    private ConnectivityManager.NetworkCallback networkCallback;
    private HeartbeatTransport transport;
    private boolean closed;
    private boolean networkReady;
    private boolean receiverRegistered;

    AwareController(Context context, Observer observer) {
        this.context = context;
        this.observer = observer;
        manager = context.getSystemService(WifiAwareManager.class);
        connectivity = context.getSystemService(ConnectivityManager.class);
    }
    private final BroadcastReceiver availability = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent intent) {
            if (!closed && !manager.isAvailable()) fail("Wi-Fi Aware 已不可用，请检查 Wi-Fi 和定位后重新开始");
        }
    };
    void start() {
        if (closed) return;
        try {
            if (manager == null || !manager.isAvailable()) {
                fail("Wi-Fi Aware 不可用，请开启 Wi-Fi、定位并检查设备支持情况");
                return;
            }
            if (!manager.getCharacteristics().isAwarePairingSupported()) {
                fail("设备不支持 Wi-Fi Aware 配对");
                return;
            }
            context.registerReceiver(availability, new IntentFilter(WifiAwareManager.ACTION_WIFI_AWARE_STATE_CHANGED), Context.RECEIVER_NOT_EXPORTED);
            receiverRegistered = true;
            observer.status("正在启动 Wi-Fi Aware…");
            manager.attach(new AttachCallback() {
                @Override public void onAttached(WifiAwareSession session) {
                    if (closed) { session.close(); return; }
                    aware = session;
                    publish();
                }
                @Override public void onAttachFailed() { fail("Aware attach 失败，请重试"); }
                @Override public void onAwareSessionTerminated() { fail("Aware 会话已终止，请重新开始"); }
            }, handler);
        } catch (RuntimeException | LinkageError e) {
            fail("无法启动：" + e.getMessage());
        }
    }
    private void publish() {
        try {
            AwarePairingConfig pairing = new AwarePairingConfig.Builder()
                    .setPairingSetupEnabled(true).setPairingVerificationEnabled(true)
                    .setPairingCacheEnabled(true)
                    .setBootstrappingMethods(AwarePairingConfig.PAIRING_BOOTSTRAPPING_PIN_CODE_DISPLAY)
                    .setSupportedCipherSuites(CIPHER).build();
            PublishConfig.Builder config = new PublishConfig.Builder().setServiceName(SERVICE)
                    .setPublishType(PublishConfig.PUBLISH_TYPE_SOLICITED)
                    .setTerminateNotificationEnabled(true)
                    .setServiceSpecificInfo(DeviceInfo.build(context)).setPairingConfig(pairing);
            if (manager.getCharacteristics().isInstantCommunicationModeSupported()) {
                config.setInstantCommunicationModeEnabled(true, android.net.wifi.ScanResult.WIFI_BAND_5_GHZ);
            }
            // Retained from the validated configuration; necessity has not been isolated by A/B testing.
            PersistableBundle data = new PersistableBundle();
            data.putBoolean("android", false);
            config.setVendorData(Collections.singletonList(new OuiKeyedData.Builder(0x506f9a, data).build()));
            aware.publish(config.build(), discovery, handler);
        } catch (RuntimeException | LinkageError e) {
            fail("服务发布失败；请核对 ROM 的 Aware 接口支持：" + e.getMessage());
        }
    }
    private final DiscoverySessionCallback discovery = new DiscoverySessionCallback() {
        @Override public void onPublishStarted(PublishDiscoverySession session) {
            if (closed) { session.close(); return; }
            publisher = session;
            requestNetwork();
        }
        @Override public void onSessionConfigFailed() { fail("服务配置失败"); }
        @Override public void onSessionTerminated() { fail("服务发布已终止，请重新开始"); }
        @Override public void onBootstrappingSucceeded(PeerHandle peer, int method) {
            if (closed) return;
            if ((method & AwarePairingConfig.PAIRING_BOOTSTRAPPING_PIN_CODE_DISPLAY) != 0) showPin(peer);
        }
        @Override public void onBootstrappingFailed(PeerHandle peer) {
            if (closed) return;
            clearPin(peer);
            observer.status("配对方式协商失败，请在 iOS 上重试");
        }
        @Override public void onPairingSetupRequestReceived(PeerHandle peer, int requestId) {
            if (closed) return;
            try {
                publisher.acceptPairingRequest(requestId, peer, "awarebridge-" + peer.hashCode(), CIPHER, showPin(peer));
            } catch (RuntimeException e) {
                clearPin(peer);
                observer.status("配对失败：" + e.getMessage());
            }
        }
        @Override public void onPairingSetupSucceeded(PeerHandle peer, String alias) {
            if (closed) return;
            clearPin(peer);
            observer.status("配对成功，请在 iOS 上点击连接");
        }
        @Override public void onPairingSetupFailed(PeerHandle peer) {
            if (closed) return;
            clearPin(peer);
            observer.status("配对失败，请核对代码后重试");
        }
    };
    private String showPin(PeerHandle peer) {
        if (!pins.containsKey(peer)) {
            pins.put(peer, String.format(Locale.ROOT, "%06d", new SecureRandom().nextInt(1000000)));
            Runnable timeout = () -> {
                clearPin(peer);
                if (!closed) observer.status("配对代码已超时，请从 iOS 重新配对");
            };
            pinTimeouts.put(peer, timeout);
            handler.postDelayed(timeout, 120_000);
        }
        observer.pin(pins.get(peer));
        return pins.get(peer);
    }
    private void clearPin(PeerHandle peer) {
        pins.remove(peer);
        Runnable timeout = pinTimeouts.remove(peer);
        if (timeout != null) handler.removeCallbacks(timeout);
        observer.pin(pins.isEmpty() ? "" : pins.values().iterator().next());
    }
    private void requestNetwork() {
        if (closed) return;
        try {
            transport = new HeartbeatTransport(new HeartbeatTransport.Observer() {
                @Override public void onStats(long tx, long rx, long age, String peer, String text) {
                    handler.post(() -> { if (!closed) observer.stats(tx, rx, age, peer, text); });
                }
                @Override public void onError(String message) {
                    handler.post(() -> { if (!closed) fail(message); });
                }
            });
            WifiAwareNetworkSpecifier specifier = new WifiAwareNetworkSpecifier.Builder(publisher)
                    .setPskPassphrase(DEMO_PASSPHRASE).setPort(transport.port()).setTransportProtocol(17).build();
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network network) {
                    if (closed || networkReady) return;
                    try {
                        transport.start(network);
                        networkReady = true;
                        observer.status("数据链路已建立，等待双向心跳");
                    } catch (IOException e) { fail("绑定 Aware 网络失败：" + e.getMessage()); }
                }
                @Override public void onLinkPropertiesChanged(Network network, LinkProperties properties) {
                    if (!closed) android.util.Log.i("AwareBridge", "Aware IP: " + properties.getLinkAddresses());
                }
                @Override public void onLost(Network network) { fail("数据链路已断开，请重新开始并连接"); }
                @Override public void onUnavailable() { fail("数据链路请求失败，请重新开始"); }
            };
            NetworkRequest request = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE).setNetworkSpecifier(specifier).build();
            connectivity.requestNetwork(request, networkCallback, handler);
            observer.status("服务已发布，请在 iOS 上添加设备\nUDP 端口 " + transport.port());
        } catch (IOException | RuntimeException e) { fail("网络请求失败：" + e.getMessage()); }
    }
    private void fail(String message) {
        if (closed) return;
        close();
        observer.status(message);
        observer.stopped();
    }
    @Override public void close() {
        if (closed) return;
        closed = true;
        handler.removeCallbacksAndMessages(null);
        pins.clear(); pinTimeouts.clear(); observer.pin("");
        if (networkCallback != null) {
            try { connectivity.unregisterNetworkCallback(networkCallback); } catch (IllegalArgumentException ignored) { }
            networkCallback = null;
        }
        if (transport != null) { transport.close(); transport = null; }
        if (publisher != null) { publisher.close(); publisher = null; }
        if (aware != null) { aware.close(); aware = null; }
        if (receiverRegistered) { context.unregisterReceiver(availability); receiverRegistered = false; }
    }
}
