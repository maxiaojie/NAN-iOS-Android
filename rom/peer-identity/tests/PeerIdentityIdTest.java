package com.android.server.wifi.aware;

import java.util.Arrays;

public final class PeerIdentityIdTest {
    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Peer identity invariant failed");
    }

    public static void main(String[] args) {
        // Synthetic fixtures only. No device credentials are read or printed.
        byte[] nik = new byte[16];
        Arrays.fill(nik, (byte) 0x31);
        byte[] before = nik.clone();
        String id = PeerIdentityId.forPackage("io.awarebridge.android", nik);
        check(id.matches("[0-9a-f]{64}"));
        check(id.equals("b13c498cbf0cca04512ada2679d81cc0cef3031054501a2adfe02c3748479ce3"));
        check(id.equals(PeerIdentityId.forPackage("io.awarebridge.android", nik.clone())));
        check(Arrays.equals(nik, before));
        check(!id.equals(PeerIdentityId.forPackage("other.app", nik)));
        nik[15] ^= 1;
        check(!id.equals(PeerIdentityId.forPackage("io.awarebridge.android", nik)));
        for (int size : new int[]{0, 15, 17, 32}) {
            try {
                PeerIdentityId.forPackage("io.awarebridge.android", new byte[size]);
                throw new AssertionError("Invalid NIK accepted");
            } catch (IllegalArgumentException expected) { /* expected */ }
        }
        try {
            PeerIdentityId.forPackage("bad\0package", before);
            throw new AssertionError("Ambiguous namespace accepted");
        } catch (IllegalArgumentException expected) { /* expected */ }
        System.out.println("Peer identity invariants passed");
    }
}
