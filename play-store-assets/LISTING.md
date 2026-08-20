# Play listing copy

The text of the en-GB store listing, kept in the repo so a change to the app and a change to the
words describing it land in the same commit.

**Two rules govern every line below.**

1. **Nothing grades a day.** There is no target, no half-day tier and no deficit — see the no-target
   entry in `CLAUDE.md`. Copy that implies an hours bar reintroduces the failure the app was rebuilt
   to remove, whatever it is called.
2. **"No internet permission" is a claim to re-verify, not to assume.** It holds because presence
   detection is done by the camera HAL and nothing on the classpath declares `INTERNET`; the merged
   manifest's only additions are `ACCESS_NETWORK_STATE` and `WAKE_LOCK` from `androidx.work`,
   `USE_FINGERPRINT` from `androidx.biometric`, and an app-scoped dynamic-receiver permission — none
   of which grants network access. Any dependency pulling in a telemetry artifact makes the line
   false without touching a word of it. Check before every release, against the merger report rather
   than the built manifest — `strings` over the latter also matches the `android:permission`
   attributes guarding `androidx.work`'s components (`DUMP`, `BIND_JOB_SERVICE`), which are access
   controls on what the app exposes and not permissions it requests, so it reads as two findings that
   need explaining every time:
   `grep uses-permission app/build/outputs/logs/manifest-merger-release-report.txt`
   Run `:app:bundleRelease` first — that report is a build output and is not committed, so an
   unbuilt tree has none and a stale one describes the previous release rather than this one.
   Expect eleven lines: the app's own six, `USE_FINGERPRINT` / `WAKE_LOCK` / `ACCESS_NETWORK_STATE`, and
   the app-scoped `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (which appears twice, once
   `${applicationId}`-templated and once resolved). Anything else is new and unexplained.

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

## What's new (500 max, per release)

Play keeps these per version code, so the text below is 2.0's and is replaced wholesale at the next
release rather than appended to. It is subject to both rules at the top of this file: **2.0's leading
claim is the removal of the hours target, which is the one change an existing user will notice** —
the camera rework is larger in the code and invisible in the app until a check runs.

Two things are deliberately absent. The **crash on granting a permission** (fragment 1.8.2, see
`CLAUDE.md`) is a real fix and is not named: release notes are read by people deciding whether to
care about an update, and a bug report is not what earns that. And nothing here **grades a day**,
which rules out the obvious framing — "track your hours", "hit your goal" — for the release that
deleted the mechanism those describe.

```
2.0 changes what a day means. There is no hours target any more — no half-days, no deficit. A day counts because you showed up, and hours are a number, never a grade.

The face check now runs on the camera hardware itself. No photo is taken, and the app holds no internet permission at all. It passes the moment it sees you, with device unlock if it cannot.

Also: a first-run tour, reminders twice a day at most, and today counts as soon as you check out.
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
