# Third-party notices

## Apple BuildingPeerToPeerApps

iOS discovery/pairing and Network API usage are adapted from Apple's BuildingPeerToPeerApps sample. The satellite simulation, graphics, and Coder/TLV wire protocol are not included. Apple copyright and license are retained in `NAN-iOS/LICENSE.txt`.

## AOSP Wi-Fi compile-time API jar

`NAN-Android/app/libs/framework-wifi.jar` was copied from the original Android `wifi_aware` project's AOSP-extracted API jar. It is used only at compile time, and is not redistributed inside the application APK. AOSP Android framework code is generally licensed under Apache License 2.0; see `licenses/Apache-2.0.txt`. The input jar does not include source revision or an embedded NOTICE; its exact AOSP revision has not been reconstructed here. See `docs/android-rom.md` for usage and limits. Keep original notices when replacing it with an independently built artifact.

## Build tooling

Gradle wrapper and Android Gradle Plugin are used to build the Android app; dependencies and SDKs are obtained separately through their original distributions. Xcode and Apple system frameworks are not redistributed in this repository.
