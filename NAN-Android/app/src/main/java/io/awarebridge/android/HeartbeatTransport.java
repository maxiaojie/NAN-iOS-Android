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
    }
    private final DatagramSocket socket = new DatagramSocket(null);
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    private final Observer observer;
    private final String session = UUID.randomUUID().toString();
    private volatile boolean closed;
    private InetSocketAddress peer;
    private String peerSession;
    private long sequence, tx, rx, lastReceived;
    private long startedAt;
    private String lastText = "等待 iOS 发送首个心跳";

    HeartbeatTransport(Observer observer) throws SocketException {
        this.observer = observer;
        socket.bind(new InetSocketAddress(0));
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
                    if (message.getInt("v") != 1 || !"ios".equals(message.getString("platform"))
                            || message.getString("session").isEmpty() || message.getLong("seq") < 0) continue;
                } catch (Exception malformed) { continue; }
                String type = message.optString("type");
                if (!type.equals("hello") && !type.equals("heartbeat") && !type.equals("ack")) continue;
                synchronized (this) {
                    if (closed) return;
                    InetSocketAddress source = new InetSocketAddress(packet.getAddress(), packet.getPort());
                    if (peer == null) {
                        if (type.equals("ack")) continue;
                        peer = source;
                        socket.connect(peer);
                    }
                    if (!peer.equals(source)) continue;
                    if (type.equals("ack") && !session.equals(message.optString("ackSession"))) continue;
                    if (peerSession == null) peerSession = message.getString("session");
                    if (!peerSession.equals(message.getString("session"))) continue;
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
        JSONObject message = new JSONObject().put("v", 1).put("type", type)
                .put("platform", "android").put("session", session).put("seq", seq)
                .put("timestampMs", System.currentTimeMillis()).put("text", text);
        if (type.equals("ack")) message.put("ackSession", peerSession);
        byte[] data = message.toString().getBytes(StandardCharsets.UTF_8);
        socket.send(new DatagramPacket(data, data.length, peer));
        tx++;
    }
    private void report() {
        observer.onStats(tx, rx, lastReceived == 0 ? -1 : SystemClock.elapsedRealtime() - lastReceived,
                peer == null ? "—" : peer.toString(), lastText);
    }
    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        timer.shutdownNow();
        socket.close();
    }
}
