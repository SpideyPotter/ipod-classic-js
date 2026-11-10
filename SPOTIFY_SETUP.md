# Connect this debug build to Spotify

Create a Spotify developer app or use one you own. Enter its **public client ID**
in the Android app's Music Sources screen; do not enter a client secret.

Register the redirect URI in your Spotify app configuration. The Android identity
values below apply if Android SDK registration is exposed by your dashboard; its
current layout has not been verified here:

| Setting | Value |
| --- | --- |
| Redirect URI | `com.ipodclassic.app://callback` |
| Android package | `com.ipodclassic.app` |
| Android activity | `com.ipodclassic.app.MainActivity` |
| Current debug signing SHA-1 | `22:07:8A:A4:8D:0E:13:7F:9B:70:80:02:38:D6:EE:23:0C:57:E3:3B` |

The fingerprint belongs to `/workspace/android-user/debug.keystore`. A different
keystore/release build has a different fingerprint and must be registered separately.
The standard debug certificate is for development, not production signing.

1. Install Spotify on the Android phone and sign in.
2. Install `app/build/outputs/apk/debug/app-debug.apk`.
3. Open Music Sources; save the client ID.
4. Select **Sign in to browse**. Authorize in the browser using the same account
   as the installed Spotify app. Return to iPod Classic when Android prompts.
5. Select **Connect Spotify playback** and authorize App Remote.
6. Open Music to browse playlists, albums, artists, liked songs and search.
   Cover Flow uses the account's saved albums.

Spotify development-mode account eligibility, app user restrictions and playback
capabilities are controlled by Spotify. Check your dashboard/current provider rules
if access is denied; do not assume every Spotify account can access an unapproved app.

Library access and playback connection are separate. Signing out of this app clears
its encrypted Web API session and disconnects App Remote; it does not sign out of
Spotify itself or revoke authorization in Spotify's account dashboard.

## Phone validation checklist

- Browser sign-in succeeds; cancellation and expired redirects show useful errors.
- Account label, private playlists and paginated results match the intended account.
- Sign-out/account switching does not show the previous account's results.
- Album/playlist context playback and single tracks honor account capabilities.
- Current title, artwork, pause/resume, seek and skip update correctly.
- Audio continues in Spotify on screen lock and after leaving iPod Classic.
- Returning reconnects controls without starting an unexpected track.
- Android Back, circular gestures, haptics, Cover Flow and large fonts remain usable.

These checks have not been performed on a phone in this environment.

## Version 0.1.1 playback connection fix

The app previously canceled a pending App Remote connection when its Activity stopped,
which can happen while Spotify shows authorization. Pending authorization is now
preserved across that transition. Completed connections still disconnect on background.
The error dialog now shows SDK exception types and targeted guidance, without copying
provider payloads that might contain session information. Actual authorization behavior
still needs phone testing; this change does not prove a developer registration is valid.

Install the newer APK over 0.1.0 (same debug signer), open Spotify and sign in, return
to iPod Classic and reconnect playback. If it fails, report the **SDK error** line.
