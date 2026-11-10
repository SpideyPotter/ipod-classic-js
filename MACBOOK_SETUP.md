# Continue development on a MacBook

## Open the native project

```sh
git clone --branch android-native --single-branch https://github.com/SpideyPotter/ipod-classic-js.git ipod-classic-android
cd ipod-classic-android
```

Install Android Studio, open this folder, and let Gradle sync. In SDK Manager install
Android SDK Platform 35, Android SDK Build-Tools 35.0.0 and Android SDK Platform-Tools.
Use Android Studio's bundled JDK (17+; the cloud build used JDK 21).
Android Studio will create local.properties with your local SDK path. Do not commit it.
Use the included Gradle wrapper; no global Gradle installation is needed.

From Terminal, if JAVA_HOME is not already configured, use the bundled JDK for this command:

```sh
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

For Android Studio installed elsewhere, select its actual bundled JDK path instead.
The scripts/build-cloud.sh helper is specific to the previous cloud environment;
use ./gradlew directly on macOS. Do not copy the cloud proxy settings.

## Run on your Android phone

Enable Developer options and USB debugging, connect with a data-capable USB cable,
approve the computer on the phone, then choose the phone in Android Studio and Run.

Install/sign in to Spotify on the phone. In iPod Classic open Music Sources, save your
Spotify developer app's public client ID, sign in to browse, then connect playback.
See SPOTIFY_SETUP.md for redirect/package identity and current registration caveats.

Your Mac generates its own debug signing certificate. Run:

```sh
./gradlew :app:signingReport
```

Its SHA-1 differs from the cloud debug build. If Spotify exposes signing registration,
register the Mac fingerprint too. The cloud keystore is intentionally not in Git.
Android cannot update an installed app with a differently signed APK: uninstall the
cloud debug app before installing the Mac build if Android reports a signature conflict.
Uninstalling clears the app's client ID/preferences/session; configure them again.

## Handoff status

- Kotlin + Compose native project; version 0.1.1, versionCode 2.
- Spotify browser PKCE sign-in and encrypted token refresh; App Remote 0.8.0 playback.
- Library lists/details, pagination, search, Now Playing, initial Cover Flow and wheel.
- Cloud APK build, eight unit tests and lint passed (zero lint issues).
- User reported App Remote connection failure on 0.1.0. Version 0.1.1 preserves pending
  authorization when Spotify backgrounds the Activity, and shows SDK error types.
  This repair has NOT been confirmed on-device. Reproduce first and capture the
  SDK error line / Android Studio Logcat without sharing tokens or secrets.
- Current Spotify dashboard's Android registration fields could not be verified.
- Existing web implementation is on master; this branch is a standalone Android tree.

## Next work

1. Run 0.1.1 on the phone and diagnose App Remote authorization with the actual SDK error.
2. Verify Web API playlists/account identity and current provider access restrictions.
3. Exercise playback, app switching/reconnection, seek, Cover Flow, wheel and large fonts.
4. Finish Spotify 1.0 polish (advanced controls, visual parity, optional online lyrics).
5. Local music + embedded/.lrc lyrics: 2.0. Apple Music: 3.0. Games: after those releases.

No Spotify client secret, user tokens, signing keystore, SDK installation, Gradle cache
or APK outputs are included in Git. Vendored official SDK/wrapper binaries and their
provenance/license documentation are included so the source builds after cloning.
