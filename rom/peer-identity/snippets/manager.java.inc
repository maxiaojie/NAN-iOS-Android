
    /**
     * Returns this application's paired aliases and app-scoped opaque peer identities.
     *
     * <p>The result contains status="ok", source="framework-memory", and records: an
     * ArrayList of Bundles with String alias and String peerId. Equal peerId values identify
     * the same stored peer NIK within this app. A peer identity reset changes the identifier.
     * No NIK or pairing key is returned. This ROM extension supports system user 0 only.
     *
     * <p>This synchronous call can wait up to three seconds; call it off the UI thread.
     * @return a snapshot of this application's paired identities
     */
    @NonNull
    @android.annotation.SuppressLint("UnflaggedApi") // Deliberate custom-ROM public API.
    @RequiresPermission(allOf = {android.Manifest.permission.ACCESS_WIFI_STATE,
            android.Manifest.permission.NEARBY_WIFI_DEVICES})
    public android.os.Bundle getAwareBridgePairingIdentities() {
        android.os.Bundle extras = new android.os.Bundle();
        extras.putParcelable(WifiManager.EXTRA_PARAM_KEY_ATTRIBUTION_SOURCE,
                mContext.getAttributionSource());
        try {
            return mService.getAwareBridgePairingIdentities(mContext.getOpPackageName(),
                    mContext.getAttributionTag(), extras);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }
