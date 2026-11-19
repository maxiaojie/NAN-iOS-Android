package io.awarebridge.android;

import java.nio.ByteBuffer;
import java.util.BitSet;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** ABT1: 32-byte big-endian header, 1200-byte UDP data packets. */
final class ThroughputTest implements AutoCloseable {
    interface Sender { void send(byte[] data) throws Exception; }
    private final Sender sender;
    private final Consumer<String> status;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean closed;
    private long sending, receiving, sent, receiveStart;
    private boolean receiveCompleted;
    private final BitSet seen = new BitSet();
    ThroughputTest(Sender sender, Consumer<String> status) { this.sender = sender; this.status = status; }
    static boolean matches(byte[] data, int length) {
        return length >= 32 && ByteBuffer.wrap(data).getInt() == 0x41425431;
    }
    static byte[] packet(int type, long id, long count, long duration) {
        return ByteBuffer.allocate(type == 2 ? 1200 : 32).putInt(0x41425431).putInt(type)
                .putLong(id).putLong(count).putLong(duration).array();
    }
    synchronized void start() {
        if (closed) return;
        if (sending != 0 || receiving != 0) { status.accept("已有吞吐测试进行中"); return; }
        final long id = (System.nanoTime() & Long.MAX_VALUE) | 1;
        sending = id;
        status.accept("发送测试中 · 10 秒 · 目标 50 Mbps");
        worker.execute(() -> {
            try {
                for (int i=0;i<3;i++) sender.send(packet(1,id,0,0));
                long start = System.nanoTime(), next = start, count=0;
                byte[] data = packet(2,id,0,0);
                while (!closed && System.nanoTime()-start < 10_000_000_000L) {
                    for (int i=0;i<16;i++) {
                        ByteBuffer.wrap(data).putLong(16,count++);
                        sender.send(data);
                    }
                    next += 3_072_000L;
                    long wait = next-System.nanoTime();
                    if (wait>0) TimeUnit.NANOSECONDS.sleep(wait);
                    else next=System.nanoTime(); // Do not burst to catch up after a stall.
                }
                if (closed) return;
                long duration=System.nanoTime()-start;
                synchronized (this) { sent=count; }
                for (int i=0;i<3;i++) { sender.send(packet(3,id,count,duration)); Thread.sleep(50); }
                Thread.sleep(3000);
                synchronized (this) {
                    if (sending==id) { sending=0; status.accept("测试超时：未收到对端结果，请确认双端均已升级"); }
                }
            } catch (Exception e) {
                synchronized (this) { if (!closed) { if (sending==id) { sending=0; status.accept("吞吐测试失败："+e.getMessage()); } } }
            }
        });
    }
    synchronized void receive(byte[] data, int length) throws Exception {
        if (closed || !matches(data,length)) return;
        ByteBuffer b=ByteBuffer.wrap(data); b.getInt(); int type=b.getInt();
        long id=b.getLong(), count=b.getLong(), duration=b.getLong();
        if (id<=0 || count<0 || count>1_000_000) return;
        if (type==1 && length==32) {
            if (receiving==id) return;
            if (sending!=0 || receiving!=0) return;
            receiving=id; receiveCompleted=false; seen.clear(); receiveStart=System.nanoTime();
            status.accept("正在接收对端吞吐测试…");
        } else if (type==2 && length==1200 && receiving==id) {
            if (!seen.get((int)count)) { seen.set((int)count); }
        } else if (type==3 && length==32 && receiving==id && duration>0) {
            receiveCompleted=true;
            long received=seen.get(0,(int)count).cardinality();
            status.accept(result("接收完成",received,count,duration));
            sender.send(packet(4,id,received,duration));
            // Keep this run briefly so a lost report can be recovered by duplicate END.
        } else if (type==4 && length==32 && sending==id && duration>0 && count<=sent) {
            status.accept(result("对端实收",count,sent,duration)); sending=0;
        }
    }
    synchronized void tick() {
        if (receiving!=0 && System.nanoTime()-receiveStart>14_000_000_000L) {
            receiving=0; seen.clear();
            if (!receiveCompleted) status.accept("接收测试超时：未收到结束报文");
        }
    }
    private static String result(String prefix,long received,long total,long ns) {
        return String.format(Locale.ROOT,"%s：%.2f Mbps · %.2f MB · %.1f 秒 · 丢包 %.2f%%",
                prefix,received*1200.0*8000/ns,received*1200.0/1e6,ns/1e9,
                total==0?0:100.0*(total-received)/total);
    }
    @Override public synchronized void close() { closed=true; worker.shutdownNow(); }
}
