package io.awarebridge.android;

interface BridgeController extends AutoCloseable {
    void start();
    default void benchmark(String protocol, int streams, boolean reverse, int rateMbps) { }
    void close();
    default void setPairingEnabled(boolean enabled) { }
}
