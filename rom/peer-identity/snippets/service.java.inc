
    @Override
    public android.os.Bundle getAwareBridgePairingIdentities(String callingPackage,
            String callingFeatureId, android.os.Bundle extras) {
        enforceAccessPermission();
        final int uid = getMockableCallingUid();
        mWifiPermissionsUtil.checkPackage(uid, callingPackage);
        // The underlying alias map is keyed by package, not by Android user.
        if (android.os.UserHandle.getUserId(uid) != 0) {
            throw new SecurityException("Pairing identity query supports system user only");
        }
        if (extras == null) {
            throw new IllegalArgumentException("Missing attribution extras");
        }
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.NEARBY_WIFI_DEVICES, TAG);
        enforceNearbyOrLocationPermission(callingPackage, callingFeatureId, uid, extras,
                "Read own Wi-Fi Aware paired identities");
        java.util.concurrent.FutureTask<android.os.Bundle> task =
                new java.util.concurrent.FutureTask<>(
                        () -> mStateManager.getAwareBridgePairingIdentities(callingPackage));
        if (android.os.Looper.myLooper() == mHandler.getLooper()) {
            task.run();
        } else if (!mHandler.post(task)) {
            throw new IllegalStateException("Wi-Fi Aware handler unavailable");
        }
        try {
            return task.get(3, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            task.cancel(false);
            mHandler.removeCallbacks(task);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Pairing identity query interrupted");
        } catch (java.util.concurrent.TimeoutException e) {
            task.cancel(false);
            mHandler.removeCallbacks(task);
            android.util.Log.w(TAG, "Pairing identity query timed out");
            throw new IllegalStateException("Pairing identity query timed out");
        } catch (java.util.concurrent.ExecutionException e) {
            // Do not attach a cause: it could contain security association details.
            android.util.Log.w(TAG, "Pairing identity snapshot failed");
            throw new IllegalStateException("Pairing identity snapshot failed");
        }
    }
