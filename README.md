<p align="center"><img src="assets/logo/watching-eye-512.png" alt="Watching Eye: a watchful owl with one clock eye and one real eye" width="128"></p>

# Watching Eye (Android app)

A small Android app to monitor and control the **Watching Eye** Emby plugin from your phone: see each limited user's
time left and what they are playing, add extra time with one tap (and undo it), put a user in time-out (and end it),
reset their time, and stop a stream. Updates arrive by **live push** from your server (no polling), the app follows
the phone's light/dark setting, and there is an optional alert when someone reaches a limit plus a home-screen widget.

Companion plugin: https://github.com/feerlessleadr/watching-eye-emby (this app is tested against it; older plugin
versions still work, see "Compatibility").

## Setup
1. Install the Watching Eye plugin on your Emby server and add the users you want to limit.
2. On the plugin page, open the **External API** tab: tick **Enable Public API**, click **Generate New Key** and **Save**.
   The app's actions (extra time, time-out, reset) use this API; status can be read with an Emby administrator key alone.
3. In Emby create an ordinary (**non-administrator**) user just for the app, for example `watchingeye-app`. With plugin 1.9
   or newer this is all the app needs from Emby, so losing the phone does not hand over the server. (With an older plugin you
   still need an administrator API key from Dashboard -> API Keys; the app works but that key is powerful, so replace it
   when you can.)
4. In the app's **Settings** enter:
   - **Emby Server URL** - an `https://` address (this app blocks plain `http://`, see below)
   - Tap **Sign in with a regular Emby account** and enter that user's name and password. The password is used once and
     never stored; the app keeps only the resulting token (a warning appears if the account is an administrator).
   - **WatchingEye API Key** - the key from step 2

### Protecting the app itself
- **Screen lock:** by default the app asks for your phone's PIN, pattern or fingerprint before it opens (and before Settings),
  because anyone who can open it can add time. Switch it off in Settings only if you accept that. It also hides the app from
  screenshots and the recent-apps view. A phone without a screen lock cannot offer this.
- **Notifications:** set Android to hide sensitive content on the lock screen (Settings -> Notifications); then the limit alert shows only "a viewing limit was reached", and names and the +15/+30/+60
  buttons need an unlocked phone, and the buttons ask for the screen lock first.
- **Storage:** settings are stored encrypted with a key held in the Android Keystore. If they cannot be read they are discarded
  (you enter them again); the app never falls back to plain text.
- **Widget:** Settings has an option to show "User 1, User 2" instead of names on the home-screen widget.
- **Verify a download:** `gh attestation verify WatchingEye-v1.6.apk --repo feerlessleadr/watching-eye-android-app`

## What the app calls
| Purpose | Request |
|---|---|
| Live updates | one websocket to `/embywebsocket` (token in the `X-Emby-Token` header): `WatchingEyeStatusStart` (plugin 1.8+) and `SessionsStart` (Emby) |
| Status (first load, pull-to-refresh, and every 20 s only while the websocket is down) | `GET /WatchingEye/LimitedUsersStatus`, falling back to `GET /WatchingEye/Public/Status` |
| Undo for extra time | `POST /WatchingEye/Public/ReduceTime` (plugin 1.8+) |
| Extra time | `POST /WatchingEye/Public/ExtendTime` |
| Time-out / clear | `POST /WatchingEye/Public/TimeOutUser`, `POST /WatchingEye/Public/ClearTimeOut` |
| Reset time | `POST /WatchingEye/Public/ResetUserTime` |
| Sessions / stop / message | `GET /emby/Sessions`, `POST /emby/Sessions/{id}/Playing/Stop`, `POST /emby/Sessions/{id}/Message` |

Credentials are sent only in **request headers** (`X-Emby-Token`, `X-WatchingEye-Api-Key`), never in the URL, because
Emby writes request URLs (including `api_key=` values) to its log files.

## Compatibility with the plugin
- Works with the original plugin 1.6.8.0 and with this fork. Fields added by newer plugin versions
  (`EffectiveDailyLimitMinutes`, `IsEnabled`) are optional; without them the app shows the plain daily limit.
- Plugin **1.9 or newer** is needed for the regular-account sign-in and plugin-supplied now-playing; **1.8 or newer** for live push, Undo and the exact countdown; with an older plugin the app still works
  by asking the server every 20 s, shows the daily limit only, and Undo reports that the plugin is too old.
- If you put a reverse proxy in front of Emby it must allow websockets, otherwise the app falls back to asking every 20 s.
- **Alerts** (Settings, off by default) keep a connection open in the background (a permanent low-priority notification)
  and notify you when someone reaches a limit, with +15/+30/+60 buttons. Some phones stop background apps; exclude the app
  from battery optimisation if the alerts stop arriving.
- The plugin limits requests to **1-43200 minutes**; the app clamps what you type and explains refusals.
- Time-outs are shown in the phone's local time and disappear when they end, even if the plugin still reports them.
- After repeated wrong plugin keys the plugin locks its public API for 5 minutes. The app stops calling the public
  route for 5 minutes after the first refusal, so a wrong key cannot trigger that lockout by retrying.
- Plain `http://` addresses are blocked (`usesCleartextTraffic=false`); the app tells you when the address is `http://`.

## Build and test
Requires Android Studio (or JDK 17+ and the Android SDK; compileSdk 35).

    gradlew assembleDebug            # app/build/outputs/apk/debug/app-debug.apk
    gradlew testDebugUnitTest        # unit tests (no device needed)

The integration test `PluginIntegrationTest` talks to a **test** Emby server and is skipped unless these environment
variables are set: `WE_BASE_URL`, `WE_EMBY_KEY`, `WE_PLUGIN_KEY`, `WE_USER_ID` (a limited user).

## Releasing (automatic signed APK on GitHub)
Pushing a version tag builds, signs and publishes the APK:

1. Bump `versionCode` (must increase every release) and `versionName` in `app/build.gradle`, commit and push.
2. Tag and push: `git tag v1.4 && git push origin v1.4` (the tag must equal `v` + `versionName`, otherwise the build stops).
3. The **Release APK** workflow runs the unit tests, builds the release APK, signs it, checks that it is signed with the
   expected key, and attaches `WatchingEye-v1.4.apk` and a `.sha256` checksum to a GitHub Release.

A dry run is available without publishing anything: Actions tab -> **Release APK** -> **Run workflow**. It builds and
signs the APK and keeps it as a workflow artifact for 14 days. The **CI** workflow runs the tests and a debug build on
every push.

### One-time setup: signing secrets
The key is never stored in the repository. Add four **repository secrets** (Settings -> Secrets and variables -> Actions):

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | the keystore file, base64 encoded: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\path\to\watchingeye.jks")) \| Set-Clipboard` |
| `ANDROID_KEYSTORE_PASSWORD` | keystore password |
| `ANDROID_KEY_ALIAS` | key alias (`key0`) |
| `ANDROID_KEY_PASSWORD` | key password (often the same as the keystore password) |

The workflow compares the signing certificate with the fingerprint of the key that signed the installed app
(`EXPECTED_CERT_SHA256` in `.github/workflows/release.yml`; a public value, not a secret). To use a different key, update that value.
Keep your own backup of the keystore and passwords: GitHub secrets cannot be read back, and without the key an installed
app can never be updated.

### Signing locally
`app/build.gradle` signs the release build only when `ANDROID_KEYSTORE_FILE`, `ANDROID_KEYSTORE_PASSWORD`,
`ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD` are set; otherwise `assembleRelease` produces an unsigned APK.
An APK signed with a different key cannot be installed over an existing install (uninstall first, which clears the saved
settings). Never commit keystores or `local.properties`.

## License
MIT, see [LICENSE](LICENSE). The app depends on open-source libraries (AndroidX, Retrofit, OkHttp, Gson, Glide, ...)
that keep their own licenses.

## Notes
- Version 1.6 (versionCode 7; released by pushing a `v1.6` tag).
- The logo (a watchful owl with one clock eye and one real eye, under a night sky) is original artwork made for this app
  and covered by the MIT license like the rest of the repository. The master is `assets/logo/watching-eye-icon.svg`
  (a 512 px PNG sits next to it); the app's adaptive and themed icons are the vector drawables `ic_launcher_*` in `res/drawable`.
- This repository previously contained a full decompiled export (`export.zip`) and extracted build artifacts; they were
  removed from the code and from the git history. The sources under `app/src/main/java/com/example/watchingeye` are the app.
