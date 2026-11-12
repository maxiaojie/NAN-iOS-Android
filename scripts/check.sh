#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -z "${JAVA_HOME:-}" && -x /usr/libexec/java_home ]]; then
    export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
fi
NAN_CHECK_DIR="$(mktemp -d)"
trap 'rm -rf "$NAN_CHECK_DIR"' EXIT
NAN-Android/gradlew -p NAN-Android :app:assembleDebug :app:lintDebug
xcrun swiftc NAN-iOS/AwareBridge/BridgeMessage.swift tests/protocol/main.swift -o "$NAN_CHECK_DIR/protocol-check"
"$NAN_CHECK_DIR/protocol-check" tests/protocol/android-heartbeat.json
xcodebuild -project NAN-iOS/AwareBridge.xcodeproj -scheme AwareBridge \
  -configuration Debug -sdk iphoneos -derivedDataPath "$NAN_CHECK_DIR/DerivedData" \
  CODE_SIGNING_ALLOWED=NO build
