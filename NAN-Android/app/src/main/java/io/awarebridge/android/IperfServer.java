package io.awarebridge.android;

import android.net.*;
import java.net.*;
import java.util.concurrent.*;

/** libiperf server bound exclusively to the current Aware Network and local IPv6. */
final class IperfServer implements AutoCloseable {
    static { System.loadLibrary("awareiperf"); }
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private volatile boolean closed;
    private final String address;
    private final long network;
    IperfServer(Network network, LinkProperties properties, String directory) {
        this.network = network.getNetworkHandle();
        String local = null;
        if (properties != null) {
            for (LinkAddress link : properties.getLinkAddresses()) {
                InetAddress ip = link.getAddress();
                if (ip instanceof Inet6Address && ip.isLinkLocalAddress()) {
                    local = ip.getHostAddress().split("%")[0] + "%" + properties.getInterfaceName();
                    break;
                }
            }
        }
        address = local;
        if (address != null) WORKER.execute(() -> {
            synchronized (this) {
                if (closed) return;
                prepare(this.network);
            }
            while (!closed) {
                String result = run(address,directory);
                if (!closed) {
                    android.util.Log.i("AwareIperf", "libiperf server cycle finished");
                    try { Thread.sleep(150); } catch (InterruptedException e) { break; }
                }
            }
        });
    }
    String host() { return address == null ? "" : address.split("%")[0]; }
    boolean available() { return address != null && !closed && ready(); }
    private static native boolean ready();
    private static native void prepare(long network);
    private static native void cancel();
    private static native String run(String address, String directory);
    @Override public synchronized void close() { if (closed) return; closed = true; cancel(); }
}
