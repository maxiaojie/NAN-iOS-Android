package com.android.server.wifi.aware;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** App-scoped opaque identifier; never exposes the underlying peer NIK. */
final class PeerIdentityId {
    private PeerIdentityId() {}

    static String forPackage(String packageName, byte[] peerNik) {
        if (packageName == null || packageName.isEmpty()
                || packageName.indexOf('\0') >= 0 || peerNik == null || peerNik.length != 16) {
            throw new IllegalArgumentException("Invalid pairing identity");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("NAN-Peer-Identity/v1\0".getBytes(StandardCharsets.UTF_8));
            digest.update(packageName.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            byte[] result = digest.digest(peerNik);
            char[] hex = "0123456789abcdef".toCharArray();
            char[] text = new char[result.length * 2];
            for (int i = 0; i < result.length; i++) {
                text[2 * i] = hex[(result[i] & 0xff) >>> 4];
                text[2 * i + 1] = hex[result[i] & 0x0f];
            }
            return new String(text);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 unavailable", e);
        }
    }
}
