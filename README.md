# iPod Classic for Android

Native Kotlin + Jetpack Compose migration of the sibling `ipod-classic-js` project.
Independent Android project; the website is unchanged. Original iPod.js by Tanner
Villarete, under the MIT license (retained in LICENSE).

## Current implementation

- Native Compose iPod shell, touch/wheel menu selection and Android back handling.
- Home, Music, playlists/albums/artists/liked songs, detail screens, track search,
  Cover Flow, Now Playing, Music Sources, Settings, About and lyrics-status screen.
- Spotify Authorization Code + PKCE in the external browser, validated OAuth state,
  Keystore-encrypted tokens, expiry/refresh handling; no client secret in the APK.
- Spotify Web API browsing with pagination, refresh, loading/error/empty states.
  Results are not persisted or shared across accounts; switching clears visible data.
- Official Spotify App Remote 0.8.0: connection, state/artwork subscription, play,
  pause, next/previous and seek; disconnect on background and reconnect on foreground
  after the user has requested playback connection. Spotify owns background audio.
- Initial swipeable/rotating Cover Flow for saved Spotify albums, dark/silver shells,
  persisted haptic preferences. This is an initial visual port, not pixel-perfect parity.

Spotify integration is implemented but still needs real-account/device validation.
No credentials are preconfigured. On-device operation is not established by a build.
Local music is 2.0, Apple Music is 3.0, and games come after those releases.

## Requirements

### Build and tooling

- JDK 17 or 21; Gradle wrapper 8.11.1, Android Gradle Plugin 8.9.2.
- Android SDK platform 35, Build Tools 35.0.0, platform-tools; Android SDK licenses accepted.
- Kotlin/Compose compiler 2.1.20. Android 8.0/API 26 or newer for the initial app.
- Google Maven, Maven Central and Gradle distribution network access.
- Physical Android phone for playback, haptics, permissions and background tests.
- Android Studio optional locally. Cloud setup uses a workspace-local JDK and SDK; see CLOUD_BUILD.md.

### Product requirements

- Read on-device audio using MediaStore and content URIs, never absolute file assumptions.
- Request READ_MEDIA_AUDIO on Android 13+; READ_EXTERNAL_STORAGE only on older versions.
  Offer Storage Access Framework import for selected audio/lyric files. No broad storage permission.
- Songs, albums, artists, playlists; handle empty libraries, denial, deleted files,
  unavailable storage and rescans. Persist local playlists separately from provider playlists.
- Media3 ExoPlayer with MediaSessionService for background playback, audio focus,
  interruptions, headset controls, notification and lock-screen transport controls.
  Declare foreground media playback permissions/service type when that service is added.
- Seek, queue, next/previous, volume, shuffle and repeat; persist user preferences.
- Lyrics: embedded plain/synced lyrics when available and user-selected .lrc files;
  timed highlighting and explicit unavailable state. Online provider optional after
  coverage, licensing, privacy and attribution review; no Spotify lyric scraping.
- Accessible touch targets, TalkBack labels, font scaling, portrait layout and system insets.
  Native system-bar appearance must be verified on actual Samsung/Pixel devices.
- No confidential credentials in the APK. Spotify is the 1.0 milestone; Apple Music is scheduled for 3.0.
- Spotify App Remote controls the installed Spotify app; Web Playback SDK is not reused.
  Confirm current SDK/account/distribution restrictions before integration. Playlist browsing
  uses Spotify Web API and account-scoped caches, pagination and visible errors.
- Apple Music: validate MusicKit Android SDK authentication/playback capabilities and
  developer credentials on a real device before committing to the integration.

## Release roadmap

### 1.0 — Spotify

1. Finish the native shell and build baseline: install SDK, accept licenses, compile
   APK, run lint and verify back navigation, system insets and accessibility on a phone.
2. Prove Spotify App Remote connection, playback controls, app switching and reconnect
   on a real device. Spotify must be installed; playback runs through Spotify.
   Validate current provider account/SDK/distribution requirements before implementation.
3. Implement mobile sign-in and Spotify Web API library browsing: playlists, albums,
   artists, search and detail screens; account-scoped caches, pagination, visible errors.
   Use PKCE as a public native client; no confidential client secret or token-exchange backend is needed for this flow.
4. Connect Now Playing, supported transport controls and playback state; add wheel
   gestures, haptics and themes. Spotify retains responsibility for background audio.
5. Investigate an optional online lyrics provider separately; ship lyrics only when
   provider terms, coverage and correct track matching are established. Lyrics are not
   supplied automatically by Spotify's playback SDK.
6. Device regression checks, privacy disclosures, signing and release preparation.

Local library permissions, scanning, import, local playback and games are outside 1.0.

### 2.0 — Local music

1. Add MediaStore scanning and Storage Access Framework import, runtime permissions,
   local songs/albums/artists, artwork and persisted local playlists.
2. Add Media3 player and MediaSessionService, queue, seek, shuffle/repeat, notification
   and lock-screen controls; test interruptions, screen lock and process recovery.
3. Add embedded lyrics and .lrc file association, timed highlighting and missing-lyrics states.
4. Add source selection and verify Spotify/local switching without mixed caches or queues.

### 3.0 — Apple Music

1. Validate MusicKit for Android authentication and playback on a real device.
2. Add Apple Music connection, library/search/detail screens and supported transport controls.
3. Verify source switching, account separation, authorization errors and subscription states.

### Later — Games and optional presentation features

Port Brick and Solitaire after the music releases. Games are not release gates for
1.0, 2.0 or 3.0. Cover Flow is included in the Spotify 1.0 scope; its initial saved-album
carousel is implemented, with device polish and validation still pending.

## Pages and overlays

| Page | Status | Release |
| --- | --- | --- |
| Home / Music | Native menus | 1.0 |
| Playlists / Playlist detail | Spotify requests + pagination | 1.0 |
| Albums / Album detail | Spotify requests + pagination | 1.0 |
| Artists / Artist detail | Followed artists + album browsing | 1.0 |
| Liked Songs | Spotify saved tracks | 1.0 |
| Search | Spotify track search | 1.0 |
| Cover Flow | Initial saved-album carousel | 1.0 |
| Now Playing | App Remote transport, artwork and metadata | 1.0 |
| Music Sources / Connection | Client ID setup, browser sign-in, playback connection | 1.0 |
| Settings | Dark/silver shell, wheel haptics | 1.0 |
| About | Version and original attribution | 1.0 |
| Lyrics | Status screen only; provider integration pending | Optional 1.0 online; 2.0 local |
| Local library / Import | Planned | 2.0 |
| Local playlist editing / Queue | Planned | 2.0 |
| Apple Music connection/library | Planned | 3.0 |
| Games / Brick / Solitaire | Deferred | After 3.0 |

Overlays: error dialog and native search keyboard implemented; contextual actions,
queue sheet and lyric file picker remain planned. Native offline/error/empty states replace the web
service-worker offline page.

## Build

With SDK installed and licenses accepted:

```sh
export ANDROID_HOME=/path/to/android-sdk
export GRADLE_USER_HOME=/workspace/.gradle-android
./gradlew :app:assembleDebug :app:lintDebug
```

Alternatively set `sdk.dir` in an untracked `local.properties` file.
APK: `app/build/outputs/apk/debug/app-debug.apk`. No signing keys are committed.
The first build downloads dependencies. Installed Java alone does not establish
that compilation or runtime behavior works.

## Spotify developer setup

1. Create your own Spotify developer app and comply with its current account/access rules.
2. Register redirect URI **com.ipodclassic.app://callback** exactly.
3. If your dashboard exposes Android SDK registration, the package is
   **com.ipodclassic.app**, activity **com.ipodclassic.app.MainActivity**, and signing
   SHA-1 is listed in SPOTIFY_SETUP.md. The dashboard may not expose those fields;
   its current registration flow has not been verified here. Do not confuse browser
   Web API authorization with App Remote playback authorization. Use the SDK error
   type shown by version 0.1.1 to diagnose playback access failures.
4. Install Spotify and sign in on the test phone. Ensure the user is permitted to access
   your developer app if Spotify requires an allowlist for its development mode.
5. Install the debug APK, open Music Sources, enter the public client ID, and save.
6. Choose **Sign in to browse** for Web API authorization; then **Connect Spotify playback**
   for App Remote authorization. These are distinct authorizations. The Spotify account
   selected in the browser should match the account in the installed Spotify app.

App Remote account capabilities can restrict single-track playback. Spotify API access
and schemas may vary by app/account; errors are visible and must be tested against the
registered app. The app does not download Spotify audio or provide offline Spotify playback
independently. Selecting a track plays that track URI; selecting Play in Spotify on an
album/playlist starts that context. Advanced queue management, shuffle/repeat controls,
volume UI and synced lyrics remain unfinished.

## Validation

Unit tests cover the RFC 7636 PKCE vector, OAuth state rejection, verifier encoding,
wheel angle wraparound and playback time formatting. Run:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Phone checks still required: sign-in/denial, account switching, private and long playlists,
API errors, playback and artwork, seek/skip, background/foreground reconnect, wheel feel,
Cover Flow and accessibility. No phone or emulator is attached to this chat.

Build results: [VALIDATION.md](VALIDATION.md). Spotify configuration: [SPOTIFY_SETUP.md](SPOTIFY_SETUP.md).

For local development on macOS, see [MACBOOK_SETUP.md](MACBOOK_SETUP.md).
