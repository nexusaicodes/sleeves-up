# iOS: six product decisions

Six rules in CLAUDE.md rest on Android behaviour that iOS doesn't have. Each one needed a decision
before the iOS app's code was written, because each one changes what the app promises. **The first
five were signed off by the owner on 2026-11-22 and the sixth on 2026-11-25, each as proposed below.** CLAUDE.md's "iOS platform rules"
entry states them as rules; this file keeps the reasoning and the sources.

Every iOS claim below cites Apple's documentation, quoted from the JSON form of
developer.apple.com's documentation pages. Where Apple's docs don't state something, this file says
so, and doesn't rely on it.

## 1. Who controls a habit's reminders

**Android rule.** A notification's opt-out is its channel and nowhere else. Each habit has its own
channel, and no screen in the app holds a notification switch. An in-app opt-out is rejected as a
one-way door: Android has no API to block or lower a channel, so a stored opt-out could never be
handed to the system.

**iOS.** Notification settings are kept per app, with switches per surface (Lock Screen,
Notification Center and so on). Apple's documentation for `UNNotificationSettings` describes no
per-category switch the user can set. It does document a way for the system's Settings screen to
link into the app's own notification settings ("A Boolean value indicating the system displays a
button for in-app notification settings").
([UNNotificationSettings](https://developer.apple.com/documentation/usernotifications/unnotificationsettings))

**Why the Android argument doesn't carry over.** It rests on a system control (the channel) that an
app setting would contradict. On iOS there is no per-habit system control to contradict. The
app-wide switch stays the master: when it is off, nothing posts whatever the app says.

**Decision.** On iOS only, each habit gets a reminder on/off in its editor on the Habits screen,
beside its reminder time. The system Settings screen links to it through the in-app
notification-settings button. The app-wide system switch still governs everything. Android keeps
channels and no in-app switch. The divergence is written into CLAUDE.md as a platform rule, not left
implicit.

**What it costs.** The two apps differ in where the switch lives. That's the platform's difference,
not a new behaviour.

## 2. Closing a forgotten session at midnight

**Android rule.** An inexact alarm wakes a receiver at the session's midnight, which writes the
close, stamped at that midnight (`ClosedBy.DAY_BOUNDARY`).

**iOS.** An app's code cannot be scheduled to run at a set time. Background refresh is a hint: "the
system doesn't guarantee launching the task at the specified date, but only that it won't begin
sooner."
([earliestBeginDate](https://developer.apple.com/documentation/backgroundtasks/bgtaskrequest/earliestbegindate))
A local notification can fire at midnight, but it runs no app code. Apple documents the extension
that can change a notification before it is shown only in terms of remote (push) notifications
([UNNotificationServiceExtension](https://developer.apple.com/documentation/usernotifications/unnotificationserviceextension)),
and this app sends none.

**Decision.** Close the session the next time any of the app's code runs: launch, return to the
foreground, a background refresh, or a widget or Live Activity timeline request. Stamp it with
`SessionSchedule.dayBoundaryOf`, which is already shared and tested on iOS. The stored record is
**identical** to Android's: same stop instant, same `DAY_BOUNDARY` ending. The zone rule is the same
too: use the zone the session was checked in under.

**What it costs.** Until code runs, the row is open, so anything showing session state from stored
data (a widget, a Live Activity) shows it as open past midnight. It also means the check-in screen
must close it before drawing, never draw an open session from yesterday first.

## 3. Reminders that decide when they fire

**Android rule.** One alarm wakes `NudgeDispatcher`, which asks `NudgeEligibility` at that moment:
has this habit had a session today, has the 90-minute gap cleared, which wording is next.

**iOS.** Local notifications are scheduled requests: "Once scheduled, a notification request remains
active until its trigger condition is met, or you explicitly cancel it."
([Scheduling a notification locally](https://developer.apple.com/documentation/usernotifications/scheduling-a-notification-locally-from-your-app))
Nothing runs when one fires (see decision 2).

**Decision.** Turn the decision from "at fire time" into "at schedule time, revised whenever code
runs":
- Each time app code runs, schedule each active habit's reminders for the next few days with the
  same rules (`NudgeEligibility`, once shared), and pick each one's wording then.
- A check-in cancels that habit's pending reminder for today. This is the iOS form of `PostedNudges.retire`.
- Reminders for a day the user never opens the app on still fire. They are true whenever the user
  hasn't checked in, which is the only case they're meant for.

**Measured limit.** Apple's pages above state no limit on how many requests can be pending. On an
iPhone 15 running iOS 27.2.1 (2026-11-28), a throwaway app scheduled 200 calendar-triggered
requests, latest-firing first: 64 stayed pending, the 64 firing soonest, and the rest were dropped
without an error. So the plan never relies on the ceiling: the shared `NudgePlan` holds 12 slots for
a session's two-hourly reminders (`SessionSchedule.MAX_REMINDERS_PER_SESSION`) and plans as many
days of habit reminders as fit in the other 52, at most 14.

**What it costs.** The 90-minute gap and the one-per-day rule are applied to a plan, not to what
actually happened. A user who checks in from the widget without opening the app may still get that
day's reminder, unless the widget's check-in route also runs code to cancel it. It should: a
check-in always passes through the app, since the presence gate lives there.

## 4. Showing that a session is running

**Android rule.** A foreground service keeps a timer notification up for as long as the session is
open. The platform draws the clock.

**iOS.** A Live Activity fits, with a system-drawn timer, but: "A Live Activity can be active for up
to eight hours unless its app or a person ends it before this limit." After that the system can leave
it on the Lock Screen for up to four more hours, so it "remains on the Lock Screen for a maximum of 12
hours" in all.
([Displaying live data with Live Activities](https://developer.apple.com/documentation/activitykit/displaying-live-data-with-live-activities))

**Decision.** Start a Live Activity at check-in and end it at check-out. A session longer than eight
hours loses it, and the session itself is unaffected: the row stays open and the gauge in the app
keeps counting. The session reminder, every two hours with only the first one alerting, is
scheduled as local notifications at check-in and cancelled at check-out. That covers the gap
exactly as it covers a dismissed timer notification on Android.

**What it costs.** After eight hours nothing on the Lock Screen shows the session is open, except
the reminder notifications.

## 5. What the widget shows

**Android rule.** "Check in to …" cycles through the active habits' names using a `ViewFlipper`, and
the widget shows no figure.

**iOS.** Widgets are redrawn from a timeline under a budget: "WidgetKit uses a budget to distribute
widget reloads over the course of the day", typically "40 to 70 refreshes" a day for a widget the
user often views, with entries "at least about 5 minutes apart."
([Keeping a widget up to date](https://developer.apple.com/documentation/widgetkit/keeping-a-widget-up-to-date))
A name changing every few seconds can't be built from timeline entries.

**Decision.** With one active habit, "Check in to <name>". With several, a plain "Check In" that
opens the habit picker, which is what Android does with animations off. With a session open, "Check
out of <name>". Never a figure. The rule that matters, that the widget shows no score, carries over
unchanged; only the cycling is dropped.

**What it costs.** Users with several habits don't see their names on the widget.

## 6. A gate with no passcode to fall back on

**Android rule.** The presence gate is never a dead end. When the device has no usable unlock, the
fallback button becomes **Set up screen lock**, which opens `Settings.ACTION_SECURITY_SETTINGS`.

**iOS.** The device-unlock policy covers "biometry, Apple Watch, or the device passcode", and "In
iOS, policy evaluation fails with the error `LAError.Code.passcodeNotSet` if the device passcode
isn't enabled."
([LAPolicy.deviceOwnerAuthentication](https://developer.apple.com/documentation/localauthentication/lapolicy/deviceownerauthentication))
The only Settings link an app is given opens "your app's custom settings in the Settings app".
([openSettingsURLString](https://developer.apple.com/documentation/uikit/uiapplication/opensettingsurlstring))
So there is no link to passcode settings.

**Decision.** With no passcode, the fallback says where to set one: Settings, then Face ID &
Passcode (or Touch ID & Passcode, or Passcode, by the device's biometry). Returning to the app
re-checks, so a passcode set there makes device unlock available at once. **Not now** leaves with
nothing written, as on every gate screen. Opening the app's own Settings page instead was rejected:
it is the wrong page.

**What it costs.** One more step for the user than Android's direct link.

## Decided

Decisions 1–5 on 2026-11-22, decision 6 on 2026-11-25.

| # | Decision | Outcome |
|---|---|---|
| 1 | Reminder opt-out per habit | iOS-only in-app switch, linked from system Settings; app-wide switch stays master |
| 2 | Forgotten session at midnight | Close on next code run, stamped at its own midnight; same record as Android |
| 3 | Reminder timing | Schedule ahead, revise on every code run, cancel on check-in; 64 pending, measured |
| 4 | Running-session display | Live Activity, up to its 8-hour limit; session reminders cover the rest |
| 5 | Widget | One name, or "Check In" with several habits; no cycling, no figures |
| 6 | Gate with no passcode (2026-11-25) | Instructions for setting one, re-checked on return; Not now always |

The pending-notification limit in decision 3 was measured on 2026-11-28: 64 per app.
