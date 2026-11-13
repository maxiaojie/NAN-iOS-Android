# Third-party notices

## Apple BuildingPeerToPeerApps

iOS discovery/pairing and Network API usage are adapted from Apple's BuildingPeerToPeerApps sample. The satellite simulation, graphics, and Coder/TLV wire protocol are not included. Apple copyright and license are retained in `NAN-iOS/LICENSE.txt`.

## Historical AOSP Wi-Fi compile-time API jar

Earlier commits included an AOSP-extracted `framework-wifi.jar` inherited from the original wifi_aware project. It was a compile-only dependency and was not packaged in the APK. After removing setVendorData, the app builds against the standard Android SDK 36; the jar and its build configuration have been removed. Historical copies remain in Git history. The Apache License 2.0 reference is retained in `licenses/Apache-2.0.txt`; the original jar's exact source revision was not recorded.

## Build tooling

Gradle wrapper and Android Gradle Plugin are used to build the Android app; dependencies and SDKs are obtained separately through their original distributions. Xcode and Apple system frameworks are not redistributed in this repository.
