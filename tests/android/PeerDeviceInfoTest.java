package io.awarebridge.android;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

public final class PeerDeviceInfoTest {
    private static byte[] txt(String... records) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (String record : records) {
            byte[] bytes = record.getBytes(StandardCharsets.UTF_8);
            out.write(bytes.length);
            out.write(bytes, 0, bytes.length);
        }
        return out.toByteArray();
    }

    private static void expect(boolean condition) {
        if (!condition) throw new AssertionError("Peer metadata parsing failed");
    }

    public static void main(String[] args) {
        byte[] raw = txt("pairingName=小明的 iPhone", "modelName=iPhone 12", "vendorName=Apple");
        byte[] wrapped = new byte[raw.length + 3];
        wrapped[0] = 4;
        wrapped[1] = (byte) raw.length;
        wrapped[2] = (byte) (raw.length >> 8);
        System.arraycopy(raw, 0, wrapped, 3, raw.length);
        Map<String, String> info = PeerDeviceInfo.parse(wrapped);
        expect("小明的 iPhone".equals(info.get("pairingName")));
        expect("iPhone 12".equals(info.get("modelName")));
        expect("Apple".equals(info.get("vendorName")));
        expect(PeerDeviceInfo.parse(raw).equals(info));
        expect(PeerDeviceInfo.parse(null).isEmpty());
        expect(PeerDeviceInfo.parse(Arrays.copyOf(wrapped, wrapped.length - 1)).isEmpty());
        expect(PeerDeviceInfo.parse(new byte[]{(byte) 184, 1, 0, 0}).isEmpty());
        expect(PeerDeviceInfo.parse(txt("pairingName=bad\nname")).isEmpty());
        expect(PeerDeviceInfo.parse(new byte[]{3, 'a', '=', (byte) 0xff}).isEmpty());
        expect(!PeerDeviceInfo.parse(txt("pairingName= ", "modelName=iPhone")).containsKey("pairingName"));
        System.out.println("Peer metadata parser checks passed");
    }
}
