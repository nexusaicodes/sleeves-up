# Play listing copy

The text of the en-GB store listing. Kept here because the previous copy went out of date silently:
it survived the removal of the daily target, the pause and the mid-session check, and it claimed a
permission the app does not hold.

**Two rules govern every line below, and both have been broken before.**

1. **Nothing grades a day.** There is no target, no half-day tier and no deficit — see the no-target
   entry in `CLAUDE.md`. Copy that implies an hours bar reintroduces the failure the app was
   rebuilt to remove.
2. **The honest privacy claim is "session data never leaves this device", never "the app has no
   internet permission".** The *merged* release manifest carries `INTERNET`, `ACCESS_NETWORK_STATE`
   and `WAKE_LOCK` from ML Kit's transitive dependencies (`transport-backend-cct`), which any user
   can see in system settings. Verify with:
   `unzip -p app/build/outputs/bundle/release/app-release.aab base/manifest/AndroidManifest.xml | strings | grep android.permission`

---

## Short description (80 max)

```
Private on-device check-in tracker. A day counts because you showed up.
```

## Full description (4000 max)

```
CheckIn is a private, on-device check-in tracker for solopreneurs, freelancers and remote workers — no account, no sign-in, no server.

A day counts because it has a session, not because its hours cleared a bar. There is no target to meet, no half-day tier and no deficit to fall into: a 45-minute day counts as a day you showed up for exactly as much as a nine-hour one. Your hours stay visible everywhere — totals, charts, the calendar, the CSV export — as a quantity, never as a grade.

HOW IT WORKS
• Check in to start a session, check out to end it. Your time for the day is the sum of your completed sessions.
• Every check-in and check-out is confirmed by an on-device face check that runs offline. If it cannot complete, the app falls back to your device biometric or screen lock.
• A live timer runs in a notification while you are checked in. Nothing is ever subtracted from a session.
• Sessions are immutable by design — no editing, no deleting, no manual entry.
• Forget to check out and the session closes automatically at midnight, on the day it began.
• Consecutive days with a session build a streak.

WHAT YOU SEE
• A calendar shaded by how long each day ran, measured against your own longest day
• Monthly summaries — days shown up, best streak, average day, longest day — each ringed against your own all-time best, so a full ring means "this month matches your best"
• Reports with daily and monthly trends, your current streak, and an all-time split
• CSV export through the system share sheet

PRIVATE BY DESIGN
• Your session data never leaves this device. There is no account, no server and no sync.
• The camera is used only for the presence check. Each frame is analysed on your device and deleted as soon as the check resolves — no image is ever stored, displayed or shared.
• Face detection, not face recognition: the check confirms someone is there. It identifies no one and matches nothing.
• No ads. No analytics. No third-party data collection.
• Your history lives in a local database on your phone. Export it to CSV whenever you like — that copy is yours.

REMINDERS
• If you have not checked in, CheckIn says so — at most twice a day, hours apart.
• Turn them off with a long press on any reminder; that opens the channel and switches it off in one tap.

WHO IT'S FOR
Solopreneurs, freelancers, consultants, students, and anyone working without external oversight who wants to build a consistent habit of showing up. Every day counts the same.

CheckIn is a self-discipline tool, not a substitute for any employer or legal time-keeping system.
```

---

## What changed from the July copy, and why

| Removed claim | Reason |
|---|---|
| "ships without the internet permission" | False — the merged manifest holds `INTERNET`. Replaced with "session data never leaves this device". |
| "Set a daily target … meet it and you're present; fall short and a half-day or full-day leave is recorded" | The target, `AttendanceStatus` and `DeficitCalculator` are deleted. |
| "A rolling leave deficit accumulates" | Deleted with the target. |
| "a periodic presence reminder asks you to re-verify. Time between … is paused" | The mid-session check and the pause mechanism are deleted; nothing is subtracted from a session. |
| "Automatic, pause-aware net-hours calculation" | Same. |
| "Per-day targets with present / half-day / full-day classification" | Same. |
| "attendance tracker" / "Attendance calendar" | The attendance vocabulary was renamed out; the tab is History. |

## Categorisation & contact

- **Category:** Productivity
- **Contact email:** `saksham@nexusai.world` · **Website:** `https://nexusai.world`
- **Privacy policy:** `https://nexusai.world/checkin/privacy`

## Graphics status

| Asset | Spec | State |
|---|---|---|
| App icon | 512×512 PNG | `app/src/main/ic_launcher-playstore.png` — generated, current |
| Feature graphic | 1024×500 PNG | `feature-graphic.png` — regenerated 2026-08-20 against the current mark |
| Phone screenshots | 2–8, ≥1080 px on a side | `screenshots/phone/` — 4 at 1080×2400 |
| Tablet screenshots | 7″ + 10″ | `screenshots/tablet/` — 3 at 2560×1600 |
