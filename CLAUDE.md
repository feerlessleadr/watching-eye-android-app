# Watching Eye (Android, legacy)

> **SUPERSEDED and frozen.** Replaced by Embidextrous (`C:\dev\embidextrous`, Hall Monitor tab). This app speaks the old plugin names (`/WatchingEye/...`, `X-WatchingEye-Api-Key`), which Hall Monitor plugin 2.0.0.0 and newer no longer answer, so it does not work with the current plugin. Do not add features. If asked to change Hall Monitor phone behavior, change Embidextrous. Only touch this repo for an explicit request (for example archiving it).

> **Persistent docs:** read `C:\dev\_docs\WORKSPACE.md` and `STATUS.md` here before working. `DECISIONS.md` here is an append-only log. The keep-docs-updated rule is in `C:\dev\CLAUDE.md`. This repo is public: keep hostnames, IPs, user names and emails out of it.

## Summary
Original phone app for the Watching Eye Emby plugin (now called Hall Monitor, repo `C:\dev\hall-monitor-emby`): see each limited user's time left, add or undo extra time, time-out, reset, stop a stream. Live push over one websocket. Repo `feerlessleadr/watching-eye-android-app` (public, MIT). Package `com.example.watchingeye`, versionName 1.6 (versionCode 7).

## Stack
Java 17 and XML views, AGP 8.10.1 (Groovy `build.gradle`), compileSdk and targetSdk 35, minSdk 26. Retrofit 2.11, Gson, OkHttp 4.12, Glide, encrypted preferences (security-crypto). minify off.

## Commands
```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
./gradlew.bat assembleDebug
./gradlew.bat testDebugUnitTest
```
Integration tests (`PluginIntegrationTest`, `LowPrivilegeIntegrationTest`) are skipped unless the env vars `WE_BASE_URL`, `WE_EMBY_KEY`, `WE_PLUGIN_KEY`, `WE_USER_ID` are set.

## Release (if ever needed)
Bump `versionCode` and `versionName` in `app/build.gradle`, push tag `vX.Y` (must equal versionName). The "Release APK" workflow signs with repo secrets and checks the certificate against a SHA-256 pinned in `release.yml` (`tools/verify-apk-signature.sh`). The signing key is the owner's and is not in the repo.

## Conventions and gotchas
- No app lock (relies on the phone lock). Credentials only in headers, never URLs. Settings encrypted with the Keystore.
- Recommended setup: a regular non-admin Emby user for the app plus the plugin's Public API key.
- The plugin locks the public API for 5 minutes after repeated wrong keys; the app pauses 5 minutes after the first refusal.
- `app/src/main/kotlin` holds decompile leftovers (`.kotlin_builtins`, okhttp3 publicsuffix resources). Not used on purpose; leave alone.
- README still says "Watching Eye" and links the old plugin repo name `watching-eye-emby`.
