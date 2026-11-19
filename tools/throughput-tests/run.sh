#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../.."
test_dir=$(mktemp -d)
trap 'rm -rf "$test_dir"' EXIT
javac -d "$test_dir" NAN-Android/app/src/main/java/io/awarebridge/android/ThroughputTest.java tools/throughput-tests/ProtocolTest.java
java -cp "$test_dir" io.awarebridge.android.ProtocolTest "$test_dir/report.bin" "$test_dir/data.bin"
swiftc -parse-as-library NAN-iOS/AwareBridge/ThroughputTest.swift tools/throughput-tests/ProtocolTest.swift -o "$test_dir/protocol-test"
"$test_dir/protocol-test" "$test_dir/report.bin" "$test_dir/data.bin"
