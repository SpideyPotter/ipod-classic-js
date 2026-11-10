# Cloud build setup

Tools live outside the project so build caches and SDK binaries do not become source artifacts:

- JDK: `/workspace/android-tools/jdk-21.0.6+7`
- Android SDK: `/workspace/android-sdk`
- Android user directory: `/workspace/android-user`
- Gradle cache: `/workspace/.gradle-android`

Run `scripts/build-cloud.sh :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`.
Use `scripts/build-cloud.sh :app:signingReport` for the debug certificate fingerprint.

The SDK manager could not retrieve manifests through this environment's Java networking.
SDK platform 35, Build Tools 35 and platform-tools were downloaded from Google's official
repository with the inherited proxy, verified against repository manifest SHA-1 checksums,
and extracted into the SDK directory. License acceptance was run through sdkmanager.
Gradle's writable user configuration contains environment-specific proxy settings and the
system Java trust store path. These settings are not included in the Android source tree.

Official Gradle 8.11.1 wrapper files are included; distribution SHA-256 is pinned.
Spotify App Remote 0.8.0 AAR is from spotify/android-sdk's official app-remote-lib directory.
Its license is retained in app/libs/SPOTIFY-LICENSE. See THIRD_PARTY.md for provenance.

No hardware-accelerated emulator or physical device is attached. Build/unit/lint checks
cannot establish Spotify authorization, native rendering or playback behavior on a phone.
