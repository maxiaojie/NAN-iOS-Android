package io.awarebridge.android;

import android.annotation.SuppressLint;
import android.content.*;
import android.net.*;
import android.net.wifi.aware.*;
import android.os.*;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.*;

/** Android publishes; iOS pairs and initiates the data path. Main-thread owned. */
@SuppressLint("MissingPermission")
final class AwareController implements BridgeController {
    static final String SERVICE = "_aware-bridge._udp";
    // Retained from the working ROM experiment. This is a demo, not a production secret.
    private static final String DEMO_PASSPHRASE = "1234567890";
    private static final int CIPHER = Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_128;
    interface Observer {
        void status(String status);
        default void benchmark(String text) { }
        void pin(String pin);
        void stats(long tx, long rx, long ageMs, String peer, String text);
        void pairing(boolean enabled, boolean available);
        void stopped();
    }

    @Override public void benchmark(String protocol, int streams, boolean reverse, int rateMbps) {
        if (transport != null) transport.benchmark(protocol,streams,reverse,rateMbps);
        else observer.benchmark("请先建立数据链路");
    }

    private final Context context;
    private final Observer observer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final WifiAwareManager manager;
    private final ConnectivityManager connectivity;
    private final Map<PeerHandle, String> pins = new HashMap<>();
    private boolean closed;
    private boolean pairingEnabled, pairingRequested, pairingUpdatePending, pairingUpdateValue;
    private final Runnable pairingUpdateTimeout = () -> {
        if (!closed && pairingUpdatePending) fail("更新配对配置超时，已停止发布，请重新开始");
    };
    private final Runnable pairingTimeout = this::expirePairing;
    private WifiAwareSession aware;
    private PublishDiscoverySession publisher;
    private ConnectivityManager.NetworkCallback networkCallback;
    private HeartbeatTransport transport;
    private boolean networkReady;
    private int networkGeneration;
    private int publishGeneration;
    private int attachGeneration;
    private boolean receiverRegistered;

    AwareController(Context context, Observer observer) {
        this.context = context;
        this.observer = observer;
        manager = context.getSystemService(WifiAwareManager.class);
        connectivity = context.getSystemService(ConnectivityManager.class);
    }

    private final BroadcastReceiver availability = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            if (!closed && !manager.isAvailable()) fail("Wi-Fi Aware 已不可用，请检查 Wi-Fi 和定位后重新开始");
        }
    };

    public void start() {
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
            context.registerReceiver(
                    availability,
                    new IntentFilter(WifiAwareManager.ACTION_WIFI_AWARE_STATE_CHANGED),
                    Context.RECEIVER_NOT_EXPORTED);
            receiverRegistered = true;
            observer.status("正在启动 Wi-Fi Aware…");
            attach();
        } catch (RuntimeException | LinkageError e) {
            fail("无法启动：" + e.getMessage());
        }
    }

    private void attach() {
        final int generation = ++attachGeneration;
        try {
            manager.attach(new AttachCallback() {
                @Override
                public void onAttached(WifiAwareSession session) {
                    if (closed || generation != attachGeneration) {
                        session.close();
                        return;
                    }
                    aware = session;
                    publish();
                }

                @Override
                public void onAttachFailed() {
                    if (closed || generation != attachGeneration) return;
                    fail("Aware attach 失败，请重试");
                }

                @Override
                public void onAwareSessionTerminated() {
                    if (closed || generation != attachGeneration) return;
                    fail("Aware 会话已终止，请重新开始");
                }
            }, handler);
        } catch (RuntimeException | LinkageError e) {
            fail("Aware attach 失败：" + e.getMessage());
        }
    }

    private PublishConfig publishConfig(boolean setupEnabled) {
        AwarePairingConfig pairing = new AwarePairingConfig.Builder()
                .setPairingSetupEnabled(setupEnabled)
                .setPairingVerificationEnabled(true)
                .setPairingCacheEnabled(true)
                .setBootstrappingMethods(AwarePairingConfig.PAIRING_BOOTSTRAPPING_PIN_CODE_DISPLAY)
                .setSupportedCipherSuites(CIPHER)
                .build();
        PublishConfig.Builder config = new PublishConfig.Builder()
                .setServiceName(SERVICE)
                .setPublishType(PublishConfig.PUBLISH_TYPE_SOLICITED)
                .setTerminateNotificationEnabled(true)
                .setServiceSpecificInfo(DeviceInfo.build(context))
                .setPairingConfig(pairing);
        if (manager.getCharacteristics().isInstantCommunicationModeSupported()) {
            config.setInstantCommunicationModeEnabled(true, android.net.wifi.ScanResult.WIFI_BAND_5_GHZ);
        }
        return config.build();
    }

    private void publish() {
        try {
            aware.publish(publishConfig(pairingEnabled), discovery(++publishGeneration), handler);
        } catch (RuntimeException | LinkageError e) {
            fail("服务发布失败；请核对 ROM 的 Aware 接口支持：" + e.getMessage());
        }
    }

    private void expirePairing() {
        if (!closed) {
            observer.status("配对窗口已超时，正在关闭新配对");
            setPairingEnabled(false);
        }
    }

    public void setPairingEnabled(boolean enabled) {
        if (closed) return;
        pairingRequested = enabled;
        if (!enabled) {
            handler.removeCallbacks(pairingTimeout);
            pins.clear();
            observer.pin("");
        }
        updatePairing();
    }

    private void updatePairing() {
        if (closed || publisher == null || pairingUpdatePending) return;
        if (pairingEnabled == pairingRequested) {
            observer.pairing(pairingEnabled, true);
            return;
        }
        try {
            pairingUpdateValue = pairingRequested;
            pairingUpdatePending = true;
            observer.pairing(pairingEnabled, false);
            handler.postDelayed(pairingUpdateTimeout, 10_000);
            publisher.updatePublish(publishConfig(pairingUpdateValue));
        } catch (RuntimeException | LinkageError e) {
            fail("更新配对配置失败，请重新发布：" + e.getMessage());
        }
    }

    private DiscoverySessionCallback discovery(int generation) {
        return new DiscoverySessionCallback() {
            @Override
            public void onPublishStarted(PublishDiscoverySession session) {
                if (closed || generation != publishGeneration) {
                    session.close();
                    return;
                }
                publisher = session;
                observer.pairing(pairingEnabled, true);
                requestNetwork();
                updatePairing();
            }

            @Override
            public void onSessionConfigUpdated() {
                if (closed || generation != publishGeneration || !pairingUpdatePending) return;
                handler.removeCallbacks(pairingUpdateTimeout);
                pairingEnabled = pairingUpdateValue;
                pairingUpdatePending = false;
                handler.removeCallbacks(pairingTimeout);
                if (pairingEnabled && pairingRequested) handler.postDelayed(pairingTimeout, 60_000);
                observer.status(pairingEnabled ? "允许新设备配对，1 分钟后自动关闭" : "新配对已关闭，服务继续发布");
                updatePairing();
            }

            @Override
            public void onSessionConfigFailed() {
                if (closed || generation != publishGeneration) return;
                fail("服务配置失败，已停止发布，请重新开始");
            }

            @Override
            public void onSessionTerminated() {
                if (closed || generation != publishGeneration) return;
                fail("服务发布已终止，请重新开始");
            }

            @Override
            public void onBootstrappingSucceeded(PeerHandle peer, int method) {
                if (closed || generation != publishGeneration || !pairingEnabled || !pairingRequested) return;
                if ((method & AwarePairingConfig.PAIRING_BOOTSTRAPPING_PIN_CODE_DISPLAY) != 0) showPin(peer);
            }

            @Override
            public void onBootstrappingFailed(PeerHandle peer) {
                if (closed || generation != publishGeneration) return;
                clearPin(peer);
                observer.status("配对方式协商失败，请在 iOS 上重试");
            }

            @Override
            public void onPairingSetupRequestReceived(PeerHandle peer, int requestId) {
                if (closed || generation != publishGeneration) return;
                if (!pairingEnabled || !pairingRequested) {
                    try {
                        publisher.rejectPairingRequest(requestId, peer);
                    } catch (RuntimeException e) {
                        observer.status("拒绝新配对失败：" + e.getMessage());
                    }
                    return;
                }
                try {
                    publisher.acceptPairingRequest(
                            requestId, peer, "awarebridge-" + peer.hashCode(), CIPHER, showPin(peer));
                } catch (RuntimeException e) {
                    clearPin(peer);
                    observer.status("配对失败：" + e.getMessage());
                }
            }

            @Override
            public void onPairingSetupSucceeded(PeerHandle peer, String alias) {
                if (closed || generation != publishGeneration) return;
                observer.status("配对成功，正在关闭新配对；请在 iOS 上点击连接");
                setPairingEnabled(false);
            }

            @Override
            public void onPairingSetupFailed(PeerHandle peer) {
                if (closed || generation != publishGeneration) return;
                clearPin(peer);
                observer.status("配对失败，请核对代码后重试");
            }
        };
    }

    private String showPin(PeerHandle peer) {
        if (!pins.containsKey(peer)) {
            pins.put(peer, String.format(Locale.ROOT, "%06d", new SecureRandom().nextInt(1000000)));
        }
        observer.pin(pins.get(peer));
        return pins.get(peer);
    }

    private void clearPin(PeerHandle peer) {
        pins.remove(peer);
        observer.pin(pins.isEmpty() ? "" : pins.values().iterator().next());
    }

    private void requestNetwork() {
        if (closed) return;
        final int generation = ++networkGeneration;
        try {
            transport = new HeartbeatTransport(new HeartbeatTransport.Observer() {
                @Override public void onBenchmark(String text) {
                    handler.post(() -> { if (!closed) observer.benchmark(text); });
                }
                @Override
                public void onStats(long tx, long rx, long age, String peer, String text) {
                    handler.post(() -> {
                        if (!closed && generation == networkGeneration) observer.stats(tx, rx, age, peer, text);
                    });
                }

                @Override
                public void onError(String message) {
                    handler.post(() -> {
                        if (!closed && generation == networkGeneration) recoverNetwork(message);
                    });
                }
            }, context.getCacheDir().getAbsolutePath());
            WifiAwareNetworkSpecifier specifier = new WifiAwareNetworkSpecifier.Builder(publisher)
                    .setPskPassphrase(DEMO_PASSPHRASE)
                    .setPort(transport.port())
                    .setTransportProtocol(17)
                    .build();
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    if (closed || generation != networkGeneration || networkReady) return;
                    try {
                        transport.configureIperf(network,connectivity.getLinkProperties(network));
                        transport.start(network);
                        networkReady = true;
                        observer.status("数据链路已建立，等待双向心跳");
                    } catch (IOException e) {
                        fail("绑定 Aware 网络失败：" + e.getMessage());
                    }
                }

                @Override
                public void onLinkPropertiesChanged(Network network, LinkProperties properties) {
                    if (!closed && generation == networkGeneration && transport != null) transport.configureIperf(network,properties);
                    if (!closed && generation == networkGeneration) android.util.Log.i("AwareBridge", "Aware IP: " + properties.getLinkAddresses());
                }

                @Override
                public void onLost(Network network) {
                    if (!closed && generation == networkGeneration) recoverNetwork("数据链路已断开");
                }

                @Override
                public void onUnavailable() {
                    if (!closed && generation == networkGeneration) recoverNetwork("数据链路请求不可用");
                }
            };
            NetworkRequest request = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
                    .setNetworkSpecifier(specifier)
                    .build();
            connectivity.requestNetwork(request, networkCallback, handler);
            observer.status("服务已发布；添加新设备请打开允许配对开关\nUDP 端口 " + transport.port());
        } catch (IOException | RuntimeException e) {
            fail("网络请求失败：" + e.getMessage());
        }
    }

    private void recoverNetwork(String reason) {
        if (closed) return;
        android.util.Log.w("AwareBridge", reason + "; restarting Aware and Publish, retaining pairing state");
        releaseNetwork();
        observer.stats(0, 0, -1, "—", "等待 iOS 重新连接");
        observer.status("数据链路已释放，正在自动恢复发布；请在 iOS 重新连接");
        // This ROM can leave the old NDP in STATE_TERMINATING indefinitely.
        // Closing attach clears stale NDP state that survives discovery-session close.
        publishGeneration++;
        pairingUpdatePending = false;
        handler.removeCallbacks(pairingUpdateTimeout);
        if (publisher != null) {
            publisher.close();
            publisher = null;
        }
        attachGeneration++;
        if (aware != null) {
            aware.close();
            aware = null;
        }
        observer.pairing(pairingEnabled, false);
        handler.postDelayed(() -> {
            if (!closed) attach();
        }, 1_000);
    }

    private void releaseNetwork() {
        // Invalidate queued callbacks from the previous socket/network request.
        networkGeneration++;
        networkReady = false;
        if (networkCallback != null) {
            try {
                connectivity.unregisterNetworkCallback(networkCallback);
            } catch (IllegalArgumentException ignored) {
            }
            networkCallback = null;
        }
        if (transport != null) {
            transport.close();
            transport = null;
        }
    }

    private void fail(String message) {
        if (closed) return;
        close();
        observer.status(message);
        observer.stopped();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        handler.removeCallbacksAndMessages(null);
        pins.clear();
        observer.pin("");
        observer.pairing(false, false);
        releaseNetwork();
        if (publisher != null) {
            publisher.close();
            publisher = null;
        }
        if (aware != null) {
            aware.close();
            aware = null;
        }
        if (receiverRegistered) {
            context.unregisterReceiver(availability);
            receiverRegistered = false;
        }
    }
}
