
    /** Called only on the Wi-Fi Aware handler, after Binder caller authorization. */
    android.os.Bundle getAwareBridgePairingIdentities(String packageName) {
        return mPairingConfigManager.getAwareBridgePairingIdentities(packageName);
    }
