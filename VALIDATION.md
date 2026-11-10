# Validation — 2026-11-10

Command: `scripts/build-cloud.sh :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`

- Debug APK assembled successfully.
- Unit tests: 8 run, 0 failures, 0 errors.
- Android lint: 0 issues in the final report.
- APK signing verified with Android apksigner.
- Original web repository has no modified files.

APK: `releases/ipod-classic-0.1.1-debug.apk` (debug-signed development build).
SHA-256 file is beside the APK. Spotify registration values are in SPOTIFY_SETUP.md.

Not verified: on-device rendering, Spotify authorization/API access, playback,
background/foreground reconnection, gestures, accessibility or battery behavior.
No emulator/phone execution was performed. ADB invocation currently fails while trying
to create its configuration in the read-only host home directory; this does not affect
APK compilation. Android/Gradle analytics also emitted a non-fatal home-directory warning.

Local audio/lyrics and Apple Music remain scheduled for 2.0 and 3.0 respectively;
games remain deferred. Online lyrics, advanced playback controls, and exact visual
parity are not finished in this initial Spotify migration.

## Playback repair build 0.1.1

Build, all eight unit tests and Android lint passed. App Remote pending authorization
now survives Activity backgrounding; established connections still disconnect.
SDK failure types are displayed with targeted guidance. Unit tests verify error cause
selection and that raw provider payloads are not exposed. No on-device reproduction
or confirmation of the playback repair has been performed.
