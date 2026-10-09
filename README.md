# Watching Eye (Android app)

A small Android app to monitor and control the **Watching Eye** Emby plugin from your phone: see each limited user's
daily usage and what they are playing, add extra time, put a user in time-out (and clear it), reset their time, and
stop a stream.

Companion plugin: https://github.com/feerlessleadr/watching-eye-emby (this app is tested against it; older plugin
versions still work, see "Compatibility").

## Setup
1. Install the Watching Eye plugin on your Emby server and add the users you want to limit.
2. On the plugin page, open the **External API** tab: tick **Enable Public API**, click **Generate New Key** and **Save**.
   The app's actions (extra time, time-out, reset) use this API; status can be read with an Emby administrator key alone.
3. In Emby (Dashboard -> API Keys) create an **administrator API key** for the app.
4. In the app's **Settings** enter:
   - **Emby Server URL** - an `https://` address (this app blocks plain `http://`, see below)
   - **Emby API Key** - the administrator key from step 3
   - **WatchingEye API Key** - the key from step 2

## What the app calls
| Purpose | Request |
|---|---|
| Status (every 10 s) | `GET /WatchingEye/LimitedUsersStatus`, falling back to `GET /WatchingEye/Public/Status` |
| Extra time | `POST /WatchingEye/Public/ExtendTime` |
| Time-out / clear | `POST /WatchingEye/Public/TimeOutUser`, `POST /WatchingEye/Public/ClearTimeOut` |
| Reset time | `POST /WatchingEye/Public/ResetUserTime` |
| Sessions / stop / message | `GET /emby/Sessions`, `POST /emby/Sessions/{id}/Playing/Stop`, `POST /emby/Sessions/{id}/Message` |

Credentials are sent only in **request headers** (`X-Emby-Token`, `X-WatchingEye-Api-Key`), never in the URL, because
Emby writes request URLs (including `api_key=` values) to its log files.

## Compatibility with the plugin
- Works with the original plugin 1.6.8.0 and with this fork. Fields added by newer plugin versions
  (`EffectiveDailyLimitMinutes`, `IsEnabled`) are optional; without them the app shows the plain daily limit.
- The plugin limits requests to **1-43200 minutes**; the app clamps what you type and explains refusals.
- Time-outs are shown in the phone's local time and disappear when they end, even if the plugin still reports them.
- After repeated wrong plugin keys the plugin locks its public API for 5 minutes. The app stops calling the public
  route for 5 minutes after the first refusal, so a wrong key cannot trigger that lockout by polling.
- Plain `http://` addresses are blocked (`usesCleartextTraffic=false`); the app tells you when the address is `http://`.

## Build and test
Requires Android Studio (or JDK 17+ and the Android SDK; compileSdk 35).

    gradlew assembleDebug            # app/build/outputs/apk/debug/app-debug.apk
    gradlew testDebugUnitTest        # unit tests (no device needed)

The integration test `PluginIntegrationTest` talks to a **test** Emby server and is skipped unless these environment
variables are set: `WE_BASE_URL`, `WE_EMBY_KEY`, `WE_PLUGIN_KEY`, `WE_USER_ID` (a limited user).

## Signing
Release builds must be signed with your own key; an APK signed with a different key cannot be installed over an
existing install (uninstall first, which clears the saved settings). Never commit keystores or `local.properties`.

## Notes
- Version 1.3 (versionCode 4).
- This repository previously contained a full decompiled export (`export.zip`) and extracted build artifacts; they were
  removed (still in git history). The sources under `app/src/main/java/com/example/watchingeye` are the app.
