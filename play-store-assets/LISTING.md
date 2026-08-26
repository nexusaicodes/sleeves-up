# Play listing copy

The text of the en-GB store listing, kept in the repo so a change to the app and a change to the
words describing it land in the same commit.

**Two rules govern every line below.**

1. **Nothing grades a day, and hours rank nothing.** There is no target, no half-day tier and no
   deficit — see the no-target entry in `CLAUDE.md` — and since 2.1 no personal best either: the
   calendar is binary, and no figure in the app is drawn against the user's own record. Copy that
   implies an hours bar reintroduces the failure the app was rebuilt to remove, whatever it is
   called, and copy that implies a personal best to beat does the same thing one level up.
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

WHAT YOU SEE
• A calendar that marks every day you showed up — one mark, the same for a short day and a long one
• Tap any day to see the sessions it held, start to finish, and what the day came to
• Reports with daily and monthly hours, when in the day your sessions start, and how many you run a day
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

Play keeps these per version code, so the text below is 2.1's and is replaced wholesale at the next
release rather than appended to. It is subject to both rules at the top of this file: **2.1's leading
claim is that hours have stopped ranking anything**, which is what an existing user actually sees —
the calendar and the month card both look different the moment they open the app.

Two things are deliberately absent. The wording must not **advertise what was taken away** as a loss:
"no more streaks" reads as a feature removal to someone who never had the app, so the copy states
what a day looks like now instead. And nothing here **grades a day**, which rules out the obvious
framing — "track your hours", "hit your goal", "beat your best" — for the release that finished
deleting the mechanism those describe.

```
2.0 stopped grading a day by its hours. 2.1 stops hours grading anything at all.

Every day you showed up is now one mark on the calendar, whether it held 45 minutes or nine hours — nothing is drawn fainter for being shorter, and nothing is measured against your longest day. Your hours are still all there, as a number.

Two new charts instead: when in the day your sessions start, and how many you run a day. Both are there to recognise yourself in, not to score.
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
| streak · consecutive days · keep the chain going | A streak is a number the next day can take away. The calendar shows consecutive days visually and the app counts none of them. |
| longest day · best day · personal best · your record | Nothing ranks one of the user's days against the others, and nothing is a baseline anything else is drawn against. |
| shaded by how long · intensity · ringed against your best | The calendar is binary, and no arc in the app fills to measure anything — the Check-In gauge's sweep is motion, not a score. |

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
