# Play listing copy

The text of the en-GB store listing, kept in the repo so a change to the app and a change to the
words describing it land in the same commit.

**Two rules govern every line below.**

1. **Nothing grades a day.** There is no target, no half-day tier and no deficit — see the no-target
   entry in `CLAUDE.md`. Copy that implies an hours bar reintroduces the failure the app was rebuilt
   to remove, whatever it is called.
2. **"No internet permission" is a claim to re-verify, not to assume.** It holds because presence
   detection is done by the camera HAL and nothing on the classpath declares `INTERNET`; the merged
   manifest carries only `ACCESS_NETWORK_STATE` and `WAKE_LOCK`, both from `androidx.work`, and
   neither grants network access. Any dependency pulling in a telemetry artifact makes the line
   false without touching a word of it. Check before every release:
   `unzip -p app/build/outputs/bundle/release/app-release.aab base/manifest/AndroidManifest.xml | strings | grep android.permission`

---

## Short description (80 max)

```
No internet permission. No photo taken. A day counts because you showed up.
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
• The app holds no internet permission at all. It cannot upload anything, because it cannot reach the network.
• No photo is ever taken. Your camera hardware reports whether a face is in frame and the app reads only that count — frames never leave the camera, so there is no image to store, show, delete or leak.
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

## Vocabulary that must stay out

Each of these described a mechanism the app no longer has, and each reads as plausible copy, which
is what makes them easy to write back in.

| Never say | Because |
|---|---|
| daily target · half day · full-day leave · leave deficit | No day is measured against an hours bar. Rule 1. |
| pause · paused time · net hours | Nothing is subtracted from a session; `duration` is exactly `stopped_at - started_at`. |
| periodic presence check · re-verify | Only check-in and check-out are gated. There is no mid-session check. |
| selfie · photo · capture | The camera reports a face count as metadata. No image is ever produced. |
| attendance | The vocabulary was renamed out of the app; the tab is History. |

## Categorisation & contact

- **Category:** Productivity
- **Contact email:** `saksham@nexusai.world` · **Website:** `https://nexusai.world`
- **Privacy policy:** `https://nexusai.world/checkin/privacy`

## Graphics

| Asset | Spec | Source |
|---|---|---|
| App icon | 512×512 PNG | `app/src/main/ic_launcher-playstore.png` — generated by `generate_icons.py` |
| Feature graphic | 1024×500 PNG | `feature-graphic.png` — generated by `generate_feature_graphic.py` |
| Phone screenshots | 4 at 1080×2400 | `screenshots/phone/` |
| Tablet screenshots | 3 at 2560×1600 | `screenshots/tablet/` |

Both generators are deterministic, so re-running them is how you check the committed files are
current: identical output means no drift. See `README.md` for the screenshot procedure and for why
the feature graphic goes stale when the mark is regenerated on its own.
