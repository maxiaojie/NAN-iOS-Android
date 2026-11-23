package io.awarebridge.android;

import android.net.Network;
import android.os.SystemClock;
import org.json.JSONObject;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.*;

/** One raw UTF-8 JSON object per UDP datagram. No Apple Coder/TLV framing. */
final class HeartbeatTransport implements AutoCloseable {
    interface Observer {
        void onStats(long tx, long rx, long ageMs, String peer, String text);
        void onError(String message);
        default void onBenchmark(String text) { }
    }
    private final DatagramSocket socket = new DatagramSocket(null);
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    private final Observer observer;
    private IperfEngine iperf;
    private final boolean publishing;
    private int peerIperfPort;
    private final String directory;
    void benchmark(String protocol, int streams, boolean reverse, int rateMbps, int durationSeconds) {
        // Button callbacks run on the main thread. Keep control datagrams on the
        // same worker as heartbeats; DatagramSocket.send may throw on the UI thread.
        try {
            timer.execute(() -> requestBenchmark(protocol, streams, reverse, rateMbps, durationSeconds));
        } catch (RejectedExecutionException e) {
            observer.onBenchmark("连接已结束，请重新连接后测速");
        }
    }
    private void requestBenchmark(String protocol, int streams, boolean reverse, int rateMbps, int durationSeconds) {
        synchronized (this) {
            if (closed) return;
            if (peerSession == null || iperf == null || !iperf.available()) {
                observer.onBenchmark("请先等待双向心跳及 iperf3 服务就绪"); return;
            }
            try {
                String config = new JSONObject().put("protocol",protocol).put("streams",streams)
                        .put("reverse",publishing ? reverse : !reverse).put("duration",durationSeconds).put("rate",rateMbps).toString();
                if (publishing) send("iperf_request", sequence++, config);
                else {
                    if (peerIperfPort != 5201) {
                        observer.onBenchmark("等待对端测速服务就绪"); return;
                    }
                    startClient(config);
                }
                android.util.Log.i("AwareIperf", "Sent iperf request: " + config);
                observer.onBenchmark("已请求 iperf3 测试 · " + streams + " 线程 · " + durationSeconds + " 秒 · " + protocol);
            } catch (Exception e) {
                android.util.Log.e("AwareIperf", "Failed to send iperf request", e);
                observer.onBenchmark("启动失败：" + e.getClass().getSimpleName()
                        + (e.getMessage() == null ? "" : "：" + e.getMessage()));
            }
        }
    }
    private final String session = UUID.randomUUID().toString();
    private volatile boolean closed;
    private InetSocketAddress peer;
    private String peerSession;
    private long sequence, tx, rx, lastReceived;
    private long startedAt;
    private String lastText = "等待对端发送首个心跳";

    HeartbeatTransport(Observer observer, String directory, boolean publishing) throws SocketException {
        this.observer = observer;
        this.directory = directory;
        this.publishing = publishing;
        socket.bind(new InetSocketAddress(0));
    }
    synchronized void configureIperf(Network network, android.net.LinkProperties properties) {
        if (iperf == null && properties != null && properties.getLinkAddresses().stream()
                .anyMatch(a -> a.getAddress().isLinkLocalAddress())) iperf = new IperfEngine(network,properties,directory,publishing);
    }
    int port() { return socket.getLocalPort(); }

    void start(Network network) throws IOException {
        start(network, null);
    }

    synchronized void start(Network network, InetSocketAddress remote) throws IOException {
        network.bindSocket(socket);
        if (remote != null) {
            peer = remote;
        }
        startedAt = SystemClock.elapsedRealtime();
        new Thread(this::receive, "awarebridge-udp-receive").start();
        timer.scheduleWithFixedDelay(() -> {
            try {
                synchronized (this) {
                    if (closed) return;
                    // Some ROMs end the NDP without delivering NetworkCallback.onLost.
                    // Release the socket and its locked peer/session on liveness loss.
                    long lastActivity = lastReceived == 0 ? startedAt : lastReceived;
                    if (peer != null && SystemClock.elapsedRealtime() - lastActivity > 20_000) {
                        observer.onError("心跳超时，数据链路已失去响应");
                        return;
                    }
                    if (peer != null) {
                        String type = sequence == 0 ? "hello" : "heartbeat";
                        send(type, sequence++, "Android " + type);
                    }
                    report();
                }
            } catch (Exception e) {
                if (!closed) observer.onError("心跳发送失败：" + e.getMessage());
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    private void receive() {
        byte[] buffer = new byte[4097];
        while (!closed) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
                if (packet.getLength() > 4096 || !packet.getAddress().isLinkLocalAddress()) continue;
                JSONObject message;
                try {
                    message = new JSONObject(new String(packet.getData(), packet.getOffset(),
                            packet.getLength(), StandardCharsets.UTF_8));
                    if (message.getInt("v") != 2 || !("ios".equals(message.getString("platform"))
                            || "android".equals(message.getString("platform")))
                            || !(publishing ? "subscriber" : "publisher").equals(message.optString("role"))
                            || message.getString("session").isEmpty() || message.getLong("seq") < 0) continue;
                } catch (Exception malformed) { continue; }
                String type = message.optString("type");
                if (!type.equals("hello") && !type.equals("heartbeat") && !type.equals("ack")
                        && !type.equals("iperf_request") && !type.equals("iperf_result")) continue;
                synchronized (this) {
                    if (closed) return;
                    InetSocketAddress source = new InetSocketAddress(packet.getAddress(), packet.getPort());
                    if (peer == null) {
                        if (!type.equals("hello") && !type.equals("heartbeat")) continue;
                        peer = source;
                        socket.connect(peer);
                    }
                    if (!peer.equals(source)) continue;
                    if (type.equals("ack") && !session.equals(message.optString("ackSession"))) continue;
                    if (peerSession == null) peerSession = message.getString("session");
                    if (!peerSession.equals(message.getString("session"))) continue;
                    lastReceived = SystemClock.elapsedRealtime();
                    peerIperfPort = message.optInt("iperfPort", 0);
                    if (type.equals("iperf_request")) {
                        if (!publishing && iperf != null) {
                            try { startClient(message.optString("text")); }
                            catch (Exception malformed) { observer.onBenchmark("无效的测速请求参数"); }
                        }
                        continue;
                    }
                    if (type.equals("iperf_result")) {
                        android.util.Log.i("AwareIperf", "Peer result: " + message.optString("text"));
                        observer.onBenchmark(message.optString("text"));
                        continue;
                    }
                    rx++;
                    lastReceived = SystemClock.elapsedRealtime();
                    lastText = message.optString("text", type);
                    if (!type.equals("ack")) send("ack", message.getLong("seq"), "Android received " + type);
                    report();
                }
            } catch (Exception e) {
                if (!closed) observer.onError("UDP 接收失败：" + e.getMessage());
                break;
            }
        }
    }

    private void send(String type, long seq, String text) throws Exception {
        JSONObject message = new JSONObject().put("v", 2).put("type", type)
                .put("role", publishing ? "publisher" : "subscriber")
                .put("platform", "android").put("session", session).put("seq", seq)
                .put("timestampMs", System.currentTimeMillis()).put("text", text);
        if (publishing && iperf != null && iperf.available()) {
            message.put("iperfPort",5201).put("iperfAddress",iperf.host());
        }
        if (type.equals("ack")) message.put("ackSession", peerSession);
        byte[] data = message.toString().getBytes(StandardCharsets.UTF_8);
        socket.send(new DatagramPacket(data, data.length, peer));
        tx++;
    }
    private void startClient(String config) throws Exception {
        if (peerIperfPort != 5201 || iperf == null || peer == null) {
            observer.onBenchmark("等待对端测速服务就绪"); return;
        }
        JSONObject c = new JSONObject(config);
        // A local subscriber sends when reverse=false; a publisher asks its
        // remote subscriber to receive when reverse=true.
        boolean reverse = c.getBoolean("reverse");
        if (publishing) return;
        iperf.start(peer.getAddress(), c.getString("protocol"), c.getInt("streams"), reverse,
                c.getInt("rate"), c.getInt("duration"), text -> {
                    synchronized (HeartbeatTransport.this) {
                        if (closed) return;
                        observer.onBenchmark(text);
                        try { send("iperf_result", sequence++, text); }
                        catch (Exception e) { android.util.Log.e("AwareIperf", "Result send failed", e); }
                    }
                });
    }
    private void report() {
        observer.onStats(tx, rx, lastReceived == 0 ? -1 : SystemClock.elapsedRealtime() - lastReceived,
                peer == null ? "—" : peer.toString(), lastText);
    }
    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        if (iperf != null) iperf.close();
        timer.shutdownNow();
        socket.close();
    }
}
