package io.awarebridge.android;

import android.net.*;
import org.json.JSONObject;
import java.net.*;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Exactly one native role per link; all sockets are bound to that Aware Network. */
final class IperfEngine implements AutoCloseable {
    static { System.loadLibrary("awareiperf"); }
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private final boolean server;
    private final long network;
    private final String directory, address;
    private volatile boolean closed;
    private boolean busy;
    IperfEngine(Network network, LinkProperties properties, String directory, boolean server) {
        this.server = server;
        this.network = network.getNetworkHandle();
        this.directory = directory;
        String local = null;
        if (properties != null) for (LinkAddress link : properties.getLinkAddresses()) {
            InetAddress ip = link.getAddress();
            if (ip instanceof Inet6Address && ip.isLinkLocalAddress()) {
                local = ip.getHostAddress().split("%")[0] + "%" + properties.getInterfaceName();
                break;
            }
        }
        address = local;
        if (server && address != null) WORKER.execute(() -> {
            synchronized (this) { if (closed) return; prepare(this.network); }
            while (!closed) {
                String result = run(true, address, directory, false, false, 4, 10, 0);
                if (!closed) {
                    android.util.Log.i("AwareIperf", "Server cycle: " + result);
                    try { Thread.sleep(150); } catch (InterruptedException e) { return; }
                }
            }
        });
    }
    String host() { return address == null ? "" : address.split("%")[0]; }
    boolean available() { return address != null && !closed && (!server || ready()); }
    synchronized void start(InetAddress peer, String protocol, int streams, boolean reverse,
                            int rate, int duration, Consumer<String> report) {
        if (closed || server || busy || !available()) { report.accept("测速未就绪或已有测试运行中"); return; }
        if (!(protocol.equals("TCP") || protocol.equals("UDP")) || streams < 1 || streams > 16
                || duration < 1 || duration > 3600 || rate < 0 || rate > 100000) {
            report.accept("无效的测速参数"); return;
        }
        busy = true;
        String remote = peer.getHostAddress(); // The heartbeat peer already carries the Aware scope.
        WORKER.execute(() -> {
            try {
                synchronized (this) { if (closed) return; prepare(network); }
                report.accept("iperf3 " + protocol + " · " + streams + " 线程 · 测试中…");
                String result = run(false, remote, directory, protocol.equals("UDP"), reverse, streams, duration, rate);
                if (!closed) report.accept(summary(result, protocol, reverse));
            } finally { synchronized (this) { busy = false; } }
        });
    }
    private static String summary(String json, String protocol, boolean reverse) {
        try {
            JSONObject root = new JSONObject(json);
            if (root.has("error")) return "iperf3 失败：" + root.getString("error");
            JSONObject end = root.getJSONObject("end");
            JSONObject sum = end.optJSONObject("sum_received");
            if (sum == null) sum = end.getJSONObject("sum");
            int streams = root.getJSONObject("start").getJSONObject("test_start").getInt("num_streams");
            String text = String.format(Locale.ROOT, "%s · %d 线程 · %s\n接收汇总 %.2f Mbps · %.2f MB · %.1f 秒",
                    protocol, streams, reverse ? "发布端 → 订阅端" : "订阅端 → 发布端",
                    sum.getDouble("bits_per_second") / 1e6, sum.getDouble("bytes") / 1e6, sum.getDouble("seconds"));
            if (protocol.equals("UDP")) {
                JSONObject udp = end.optJSONObject("sum");
                if (udp == null) udp = sum;
                text += String.format(Locale.ROOT, "\n丢包 %.2f%% · 抖动 %.3f ms", udp.optDouble("lost_percent", 0), udp.optDouble("jitter_ms", 0));
            }
            return text;
        } catch (Exception e) { return "iperf3 返回无效结果：" + e.getClass().getSimpleName(); }
    }
    private static native boolean ready();
    private static native void prepare(long network);
    private static native void cancel();
    private static native String run(boolean server, String address, String directory, boolean udp,
                                     boolean reverse, int streams, int duration, int rate);
    @Override public synchronized void close() { if (closed) return; closed = true; cancel(); }
}
