package io.awarebridge.android;

interface BridgeController extends AutoCloseable {
    void start();
    default void benchmark() { }
    void close();
    default void setPairingEnabled(boolean enabled) { }
}
