
    /** Snapshot only; does not create, update or remove any pairing credential. */
    android.os.Bundle getAwareBridgePairingIdentities(String packageName) {
        java.util.ArrayList<android.os.Bundle> records = new java.util.ArrayList<>();
        java.util.Set<String> aliases = mPerAppPairedAliasMap.get(packageName);
        if (aliases != null) {
            if (aliases.size() > 256) {
                throw new IllegalStateException("Too many pairing records for a Binder snapshot");
            }
            java.util.ArrayList<String> sorted = new java.util.ArrayList<>(aliases);
            java.util.Collections.sort(sorted);
            for (String alias : sorted) {
                PairingSecurityAssociationInfo info = mAliasToSecurityInfoMap.get(alias);
                if (info == null || alias.isEmpty()) {
                    throw new IllegalStateException("Incomplete pairing record");
                }
                android.os.Bundle record = new android.os.Bundle();
                record.putString("alias", alias);
                record.putString("peerId", PeerIdentityId.forPackage(packageName, info.mPeerNik));
                records.add(record);
            }
        }
        android.os.Bundle result = new android.os.Bundle();
        result.putString("status", "ok");
        result.putString("source", "framework-memory");
        result.putParcelableArrayList("records", records);
        return result;
    }
