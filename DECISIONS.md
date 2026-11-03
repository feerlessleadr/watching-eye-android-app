# Decisions (Watching Eye Android, superseded)

Append-only, newest at the bottom. Never rewrite an entry; to reverse one, add a new entry that references it.

**Existing logs:** this repo has no decision log of its own. The plugin-side history is `C:\dev\hall-monitor-emby\PROGRESS.md` (section 12 covers this app) and the combined-app decisions are in `C:\dev\embidextrous\docs\decisions.md`. The entries below were reconstructed from the README and those docs.

### 2026-10-10, No in-app screen lock
- **Context:** An app lock had been added.
- **Options considered:** Keep a PIN or biometric lock; rely on the phone's lock.
- **Decision:** Remove it (commit "Remove the in-app screen lock"). Rely on the phone's screen lock.
- **Rationale:** Owner's choice; anyone who can open the phone can already add time.
- **Impact:** README tells users to keep the phone locked. Same decision was carried into Embidextrous.

### 2026-10-12, Frozen: replaced by Embidextrous, plugin renamed to Hall Monitor
- **Context:** The owner merged the Hall Monitor and SmartLists apps into Embidextrous, and the plugin dropped every "Watching Eye" name (plugin 2.0.0.0: `HallMonitor.dll`, `/emby/HallMonitor/...`, `X-HallMonitor-Api-Key`, `HallMonitorStatus`).
- **Options considered:** Keep old names answered for a transition; clean break.
- **Decision:** Clean break. This app is not updated and stops working with plugin 2.0+. The repo stays untouched until Embidextrous fully replaces it.
- **Rationale:** The original plugin's author re-released it as a closed-source app, so the owner wanted no shared names. Embidextrous is a different package, so there is no update path anyway.
- **Impact:** Do not develop here. Archiving the repo with a pointer is a later, owner-approved step. See `C:\dev\embidextrous\DECISIONS.md` ("Nothing is called Watching Eye any more").
