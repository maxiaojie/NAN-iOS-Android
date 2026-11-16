package io.awarebridge.android;

interface BridgeController extends AutoCloseable {
    void start();
    void close();
    default void setPairingEnabled(boolean enabled) { }
}
