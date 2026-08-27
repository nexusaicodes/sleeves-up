# Play listing copy

The text of the en-GB store listing, kept in the repo so a change to the app and a change to the
words describing it land in the same commit.

**Two rules govern every line below.**

1. **Nothing grades a day, and hours rank nothing.** There is no target, no half-day tier and no
   deficit — see the no-target entry in `CLAUDE.md` — and since 3.0 no personal best either: the
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

## App title (30 max)

```
Sleeves Up
```

The previous title was `CheckIn - Solopreneur Tracker` (28). The same shape does not fit — `Sleeves
Up - Solopreneur Tracker` is 32 and `Sleeves Up: Solopreneur Tracker` is 31, both over the limit —
so the title is the bare wordmark. **The keywords do not move to the short description — they
move to the full description, which is indexed too**, and that is where
"check-in tracker", "solopreneurs", "freelancers" and "remote workers" now sit. The short
description is spent instead on the two claims no competitor can copy, because it is the field a
stranger actually reads and the one Play truncates least. If a keyword suffix is wanted later,
`Sleeves Up - Check In Daily` (27) and `Sleeves Up: Work Tracker` (24) both fit; the bare name is
the choice to beat, not a default.

**The Play title is user-facing and the package is not.** `applicationId` stays
`com.nexusai.checkin.app` — frozen at the first upload — and every internal name (the
`com.checkin.app` package, `CheckInApp`, `Theme.CheckInApp`, the type names) stays as it is. A
listing rename costs nothing; an `applicationId` rename costs the install base.

## Short description (80 max)

```
No internet permission. No photo taken. A day counts because you showed up.
```

## Full description (4000 max)

```
Sleeves up or sleeves down. There is no third state, and that is the whole app: a day counts because you showed up for it, not because its hours cleared a bar.

Sleeves Up is a private, on-device check-in tracker for solopreneurs, freelancers and remote workers — no account, no sign-in, no server.

A day counts because it has a session — no target to meet, no half-day tier, no deficit to fall into. A 45-minute day counts as a day you showed up for exactly as much as a nine-hour one. Your hours stay visible everywhere — totals, charts, the calendar, the CSV export — as a quantity, never as a grade.

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
• If you have not checked in, Sleeves Up says so — at most twice a day, hours apart.
• Turn them off with a long press on any reminder; that opens the channel and switches it off in one tap.

WHO IT'S FOR
Solopreneurs, freelancers, consultants, students, and anyone working without external oversight who wants to build a consistent habit of showing up. Every day counts the same.

Sleeves Up is a self-discipline tool, not a substitute for any employer or legal time-keeping system.
```

---

## What's new (500 max, per release)

Play keeps these per version code, so the text below is 3.0's and is replaced wholesale at the next
release rather than appended to.

**3.0 is the first release since 2.0, not since 2.1.** 2.1 was prepared — version bumped, copy
written — and never tagged or uploaded; `git tag` is the record, and it lists v0.1, v1.1 and v2.0
only. So everything 2.1 was going to say is still unsaid, and an existing user going 2.0 → 3.0 meets
the rename *and* the end of hours-as-a-grade in the same update. Both belong here. Writing this note
as "since 2.1" is the mistake the tag list exists to catch.

It is subject to both rules at the top of this file, which rules out the obvious framing for a
rename — "a fresh new look" grades nothing but says nothing either — and rules out advertising a
removal as a loss: "no more streaks" reads as a feature cut to someone who never had the app.

```
The app is now called Sleeves Up. Same app, same data, nothing to migrate — a day still counts because it has a session.

Hours have stopped grading anything at all. Every day you showed up is one mark on the calendar, whether it held 45 minutes or nine hours: nothing is drawn fainter for being shorter, and nothing is measured against your longest day. Your hours are still all there, as a number.

Two new charts instead: when in the day your sessions start, and how many you run a day. Both are there to recognise yourself in, not to score.

New in Settings: a plain list of what this app cannot do, and where to check it yourself.
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
| roll up your sleeves · sleeves up! (as an instruction) · let's get to work | The name describes a state, not an order. Nobody has ever rolled their sleeves up for nine hours, so the metaphor carries no measurement — which is exactly what lets it sit on an app where hours grade nothing. Said *at* the user on a cadence it becomes a mascot, and a mascot is the gamification register this app has none of. Gloss it once in the listing; never in a notification, a celebration or an empty state. |
| shaded by how long · intensity · ringed against your best | The calendar is binary, and the app fills no arc at all — the Check-In gauge is the brand mark breathing, and it says only whether a session is open. |

## Categorisation & contact

- **Category:** Productivity
- **Contact email:** `saksham@nexusai.world` · **Website:** `https://nexusai.world`
- **Privacy policy:** `https://nexusai.world/sleeves-up/privacy`

  A second copy of `ExternalLinks.PRIVACY_POLICY_URL`, edited by hand in the Console. `/checkin/privacy`
  redirects here permanently, so a stale field still resolves and nothing reports the drift.

## Graphics

| Asset | Spec | Source |
|---|---|---|
| App icon | 512×512 PNG | `app/src/main/ic_launcher-playstore.png` — generated by `generate_icons.py` |
| Feature graphic | 1024×500 PNG | `feature-graphic.png` — generated by `generate_feature_graphic.py` |
| Phone screenshots | 7 at 1080×1920 | `screenshots/phone/` — composited by `generate_screenshots.py` |
| Tablet screenshots | 3 at 2560×1600 | `screenshots/tablet/` — captured, uncaptioned. See below |

All three generators are deterministic, so re-running them is how you check the committed files are
current: identical output means no drift. See `README.md` for the capture procedure.

The feature graphic no longer composites the 512px icon — it draws the calendar fragment itself, and
shares only `CORNER_FRAC` with `generate_icons.py` — so regenerating the mark alone no longer leaves
it stale. That coupling was real while the graphic pasted the icon in, and it is gone; run both
anyway, since they are cheap and the wordmark and the mark are the same brand decision.

### The seven phone screenshots

Play shows only the **first two or three** in search results and in the "you might also like" rails,
and most people never scroll the strip. So 1–3 have to work as a standalone three-panel pitch and
4–7 are for someone already swiping. All seven are captioned in the same position, type and band —
a raw screenshot asks a stranger to decode a UI they have never seen, so the caption sells and the
screenshot corroborates.

| # | File | Shows | Caption |
|---|---|---|---|
| 1 | `01-calendar.png` | A complete month, binary, weekends mostly among the gaps | A day counts because you showed up. |
| 2 | `02-face-check.png` | The gate's disclosure, stating the mechanism in the app's words | Check in with a face check that takes no photo. |
| 3 | `03-summary.png` | Days shown up against days tracked; hours below, plain | 24 days out of 31. Hours are a number, not a grade. |
| 4 | `04-running.png` | The timer notification in the shade, chronometer running | A live timer while you're in. Nothing gets subtracted. |
| 5 | `05-start-times.png` | The morning / afternoon / evening split | See when you actually start. No bucket is the right one. |
| 6 | `06-privacy.png` | The in-app privacy screen | No internet permission at all. It cannot upload, because it cannot connect. |
| 7 | `07-export.png` | The system share sheet mid-export | Export everything. That copy is yours. |

Slot 1 is the hero and earns it mechanically rather than by taste: every habit tracker on Play uses
an intensity ramp, so a binary calendar reads as *different* before a word is read, and what it
reads as is density rather than depth — which is a picture of the thesis. Slot 2 is second because
"no photo" is the objection people form on their own, and getting there first is worth more than any
feature. Slot 5's second sentence is load-bearing: without it a viewer assumes morning is the good
column, because every other productivity app has taught them to.

Slot 6 is shot against **the app's own screen** (`ui/about/PrivacyScreen.kt`), never Android's system
permission page — that page is not this app's UI, it varies by OEM and version, and Play is
inconsistent about accepting system chrome. The claim is verifiable from the APK, which is what
makes it worth making; the screen states it and says where to check it.

**What may not appear in any of the seven** — each of these described a mechanism the app no longer
has, so a screenshot showing one is a screenshot of a build that does not exist:

- any ring, arc or progress bar filling toward a personal best
- any duration-shaded calendar
- any superlative — longest, best, record
- the word *streak*, including in a caption
- a perfect unbroken month: it contradicts the pitch, and it makes the app look like it is for
  people who do not need it

### Tablet screenshots

Three shots, reshot for 3.0 against the same seeded history as the phone set: the Check-In screen at
rest, History with a day selected, and Reports. **They are uploaded uncaptioned**, unlike the phone
set, and that is a deliberate asymmetry rather than an omission. The phone strip is read in search
results by someone who has never seen the app, which is what the caption band is for; the tablet
strip is only ever reached from the listing page itself, by someone who has already read that copy.

History is the one worth having, and the reason the set exists at all: it goes two-pane on expanded
widths, so the calendar and the selected day's ledger sit side by side — which is the layout a phone
cannot show and the one a tablet user is deciding about. The Check-In screen is deliberately the
resting state, green button and still lattice, because a tablet is not where a session is usually
running.

Play does not require tablet screenshots; without them a listing carries a "not optimised for
tablets" note on tablet devices, which is what these remove.

