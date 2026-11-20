#!/usr/bin/env python3
"""Prepare an inspectable source patch. Never modifies a ROM checkout itself."""
import argparse
import difflib
from pathlib import Path

HERE = Path(__file__).resolve().parent
AWARE = 'framework/java/android/net/wifi/aware/'
SERVICE = 'service/java/com/android/server/wifi/aware/'
TARGETS = [
    (AWARE + 'WifiAwareManager.java', 'manager.java.inc', ['mContext', 'mService']),
    (AWARE + 'IWifiAwareManager.aidl', 'aidl.aidl.inc', ['interface IWifiAwareManager']),
    (SERVICE + 'WifiAwareServiceImpl.java', 'service.java.inc',
     ['mStateManager', 'mHandler', 'enforceNearbyOrLocationPermission', 'getMockableCallingUid']),
    (SERVICE + 'WifiAwareStateManager.java', 'state.java.inc', ['mPairingConfigManager']),
    (SERVICE + 'PairingConfigManager.java', 'cache.java.inc',
     ['mPerAppPairedAliasMap', 'mAliasToSecurityInfoMap', 'mPeerNik']),
]


def prepare(root):
    changes = []
    for relative, snippet, anchors in TARGETS:
        original = (root / relative).read_text()
        if 'getAwareBridgePairingIdentities(' in original:
            raise ValueError('Extension already present (or partially applied): ' + relative)
        if any(anchor not in original for anchor in anchors):
            raise ValueError('ROM source differs; review integration manually: ' + relative)
        end = original.rfind('}')
        if end < 0 or original[end + 1:].strip():
            raise ValueError('Unexpected source ending: ' + relative)
        updated = original[:end] + (HERE / 'snippets' / snippet).read_text() + original[end:]
        if relative.endswith('.aidl') and 'import android.os.Bundle;' not in original:
            raise ValueError('Expected existing Bundle import: ' + relative)
        changes.append((relative, original, updated))
    helper = SERVICE + 'PeerIdentityId.java'
    if (root / helper).exists():
        raise ValueError('Helper already exists: ' + helper)
    changes.append((helper, '', (HERE / 'PeerIdentityId.java').read_text()))
    return ''.join(''.join(difflib.unified_diff(
        before.splitlines(True), after.splitlines(True),
        fromfile='a/' + name if before else '/dev/null', tofile='b/' + name))
        for name, before, after in changes)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--wifi-root', required=True, type=Path,
                        help='Absolute path to packages/modules/Wifi in your Android 17 tree')
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    root = args.wifi_root.resolve()
    output = args.output.resolve()
    if output == root or root in output.parents:
        parser.error('Keep patch output outside the Wi-Fi source tree')
    try:
        patch = prepare(root)
    except (OSError, ValueError) as error:
        parser.error(str(error))
    # No partial patch if source validation fails; no overwriting unrelated output.
    with output.open('x') as stream:
        stream.write(patch)
    print('Prepared', output, '(review, then git apply --check before applying)')


if __name__ == '__main__':
    main()
