package io.awarebridge.android;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
public class ProtocolTest {
    public static void main(String[] args) throws Exception {
        List<byte[]> replies=new ArrayList<>();
        try (ThroughputTest test=new ThroughputTest(replies::add, text -> {})) {
            byte[] start=ThroughputTest.packet(1,123,0,0);
            test.receive(start,start.length);
            test.receive(start,start.length); // duplicate START must not reset counters
            for (int seq:new int[]{0,0,2}) {
                byte[] data=ThroughputTest.packet(2,123,seq,0); test.receive(data,data.length);
            }
            byte[] wrong=ThroughputTest.packet(2,999,1,0); test.receive(wrong,wrong.length);
            byte[] end=ThroughputTest.packet(3,123,3,10_000_000_000L); test.receive(end,end.length);
            if (replies.size()!=1 || ByteBuffer.wrap(replies.get(0)).getLong(16)!=2)
                throw new AssertionError("deduplication/session isolation failed");
            Files.write(Path.of(args[0]),replies.get(0));
            Files.write(Path.of(args[1]),ThroughputTest.packet(2,123,2,0));
            test.receive(end,end.length);
            if (!Arrays.equals(replies.get(0),replies.get(1))) throw new AssertionError("END retry failed");
        }
        CountDownLatch done = new CountDownLatch(1);
        ThroughputTest[] loop = new ThroughputTest[1];
        long[] dataPackets = {0};
        loop[0] = new ThroughputTest(data -> {
            ByteBuffer b = ByteBuffer.wrap(data);
            int type = b.getInt(4);
            if (type == 2) dataPackets[0]++;
            if (type == 3) {
                byte[] reply = ThroughputTest.packet(4, b.getLong(8), dataPackets[0], b.getLong(24));
                loop[0].receive(reply, reply.length);
            }
        }, text -> { if (text.startsWith("对端实收")) { System.out.println(text); done.countDown(); } });
        try (ThroughputTest load = loop[0]) {
            load.start();
            if (!done.await(14, TimeUnit.SECONDS) || dataPackets[0] < 1000)
                throw new AssertionError("sender pacing/report flow failed");
        }
        System.out.println("Java protocol and 10-second sender checks passed");
    }
}
