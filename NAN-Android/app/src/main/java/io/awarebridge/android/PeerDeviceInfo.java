package io.awarebridge.android;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Parses the peer's TXT metadata without treating arbitrary SSI bytes as a name. */
final class PeerDeviceInfo {
    static Map<String, String> parse(byte[] data) {
        Map<String, String> values = new LinkedHashMap<>();
        if (data == null || data.length == 0) return values;
        if ((data[0] & 0xff) == 4 && data.length >= 3) {
            // Typed TXT TLVs: one-byte type and little-endian two-byte length.
            int offset = 0;
            while (offset < data.length) {
                if (data.length - offset < 3) return new LinkedHashMap<>();
                int type = data[offset] & 0xff;
                int size = (data[offset + 1] & 0xff) | ((data[offset + 2] & 0xff) << 8);
                offset += 3;
                if (size > data.length - offset) return new LinkedHashMap<>();
                if (type == 4 && !parseTxt(data, offset, offset + size, values)) {
                    return new LinkedHashMap<>();
                }
                offset += size;
            }
        } else if (!parseTxt(data, 0, data.length, values)) {
            values.clear();
        }
        return values;
    }

    private static boolean parseTxt(byte[] data, int offset, int end, Map<String, String> values) {
        while (offset < end) {
            int size = data[offset++] & 0xff;
            if (size > end - offset) return false;
            String record;
            try {
                record = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(data, offset, size)).toString();
            } catch (CharacterCodingException e) {
                return false;
            }
            offset += size;
            int equals = record.indexOf('=');
            if (equals <= 0) continue;
            String key = record.substring(0, equals);
            if (!key.equals("pairingName") && !key.equals("modelName") && !key.equals("vendorName") && !key.equals("ndpSecurity")) continue;
            String value = record.substring(equals + 1).trim();
            if (value.isEmpty()) continue;
            for (int i = 0; i < value.length(); i++) {
                if (Character.isISOControl(value.charAt(i)) || value.charAt(i) == '\u2028'
                        || value.charAt(i) == '\u2029') return false;
            }
            values.putIfAbsent(key, value);
        }
        return true;
    }
}
