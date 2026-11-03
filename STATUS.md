# Watching Eye (Android, legacy): status

Snapshot written 2026-11-03. **SUPERSEDED and frozen** by Embidextrous (`C:\dev\embidextrous`).

## State
- Latest release: v1.6 (tags v1.3 to v1.6). Branch `main` equals `origin/main`, working tree clean. Last commit 2026-10-10 (Dependabot merges; earlier "Remove the in-app screen lock").
- Maintenance only. No in-progress work.
- Does not work with Hall Monitor plugin 2.0.0.0 or newer: the plugin dropped the old routes, header and websocket names on purpose (clean break, see `DECISIONS.md`). It only works with the old Watching Eye plugin.

## Next steps
- None planned. Possible owner-approved cleanup: archive the GitHub repo with a pointer to Embidextrous, update the README (still says "Watching Eye" and links the old plugin repo name).

## Known issues
- Decompile leftovers in `app/src/main/kotlin` (harmless).
- Loose old builds `C:\dev\WatchingEye-v1.3-debug.apk` and `C:\dev\WatchingEye-v3.apk` sit in the workspace root; and `C:\dev\watching-eye-android-app-original-history.bundle` holds original history that includes private details. Never publish the bundle.

## Not verified
- Behavior against any current server. Not tested since the plugin rename.

## Manual steps for the owner
- Decide whether to archive the repo. Keystore (`watchingeye.jks`, alias `key0`) and its GitHub secrets are the owner's; never read them.
