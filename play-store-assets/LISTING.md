# Play listing copy

The text of the en-GB store listing, kept in the repo so a change to the app and a change to the
words describing it land in the same commit.

The app also ships in Modern Standard Arabic, and so does the listing: see **Arabic listing** below,
written under the same two rules and the vocabulary table's Arabic column.

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
   **The in-app update prompt is the one outside question, and it is the Play Store's.**
   `com.google.android.play:app-update` binds to the Play Store app over IPC and declares nothing;
   its merge adds only the non-exported `PlayCoreDialogWrapperActivity` and the
   `com.google.android.gms.version` meta-data. Play's docs say the check sends Google the device
   metadata, app version and installed modules, so neither the listing nor the Privacy screen may
   claim nothing at all leaves the device about the app, and the Data safety form should be
   re-checked against Play's in-app updates data-safety section whenever it is resubmitted.

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
• In English or Arabic — choose in Settings, or follow your phone

PRIVATE BY DESIGN
• Your session data never leaves this device. There is no account, no server and no sync.
• The app holds no internet permission at all, so it cannot upload anything.
• No photo is ever taken. Your camera hardware reports whether a face is in frame and the app reads only that count — frames never leave the camera, so there is no image to store, show, delete or leak.
• Face detection, not face recognition: the check confirms someone is there. It identifies no one and matches nothing.
• No ads. No analytics. The only question asked of anyone else is whether Google Play has a newer version.
• Your history lives in a local database on your phone. Export it to CSV whenever you like — that copy is yours.

HABITS
• Keep a separate record for each thing you are showing up for — the gym, the writing, the practice.
• Switch between them from the top bar. Each has its own calendar, reports and export.
• Nothing compares one habit with another. Each is its own record.

REMINDERS
• If you have not checked in to a habit, Sleeves Up says so — once that day, around the time you picked for it.
• Turn one off with a long press on it; that opens its channel and switches it off in one tap, without touching the others.

WHO IT'S FOR
Solopreneurs, freelancers, consultants, students, and anyone working without external oversight who wants to build a consistent habit of showing up. Every day counts the same.

Sleeves Up is a self-discipline tool, not a substitute for any employer or legal time-keeping system.
```

---

## What's new (500 max, per release)

Play keeps these per version code, so the text below is 3.2's and is replaced wholesale at the next
release rather than appended to. **500 is a hard limit and this section has been over it**, which
Play rejects at submission rather than truncating — count the block before pasting it, em dash and
blank lines included.

**3.2 follows 3.1**, which is what Play serves (a Play install read on 2026-10-27). Write each note against the
version actually live, not the last tag: a tag can precede its upload, and a release prepared but
never uploaded leaves everything it meant to say still unsaid.

It is subject to both rules at the top of this file, and the vocabulary table below applies in full —
it binds a *denial* as much as a boast. It is written for someone updating, who has already been
through the welcome tour and so meets the widget's page alone, later, rather than in the tour.

```
Check in from your home screen: add the Sleeves Up widget, and a tap takes you straight to the presence check. With more than one habit, it asks which.

Sleeves Up now runs on Android 12 and 12L.

When Google Play has a newer version, the app can offer it to you without leaving.
```

---

## Arabic listing (ar)

Play's Arabic translation of the listing, entered under **Translations → Arabic** in the Console.
Modern Standard Arabic, in the in-app copy's words: الحضور is the app's own word for showing up (the
Check In action is تسجيل الحضور), عادة is the word for a habit and never هدف, and every figure is in
Latin digits, as on screen. It is written under both rules above and the vocabulary table's Arabic
column.

### App title

```
Sleeves Up
```

The product name stays in Latin script, as `app_name` does in the app (`translatable="false"`).

### Short description (80 max)

```
لا إذن بالإنترنت، ولا تُلتقط أي صورة. يُحتسب اليوم لأنك حضرت.
```

### Full description (4000 max)

```
الأكمام مشمّرة أو منزلة، ولا حالة ثالثة بينهما — وهذا هو التطبيق كله: يُحتسب اليوم لأنك حضرت له، لا لأن ساعاته تجاوزت حدًّا ما.

Sleeves Up متتبّع خاص لتسجيل الحضور يعمل على جهازك، لروّاد الأعمال المستقلين والعاملين لحسابهم الخاص والعاملين عن بُعد — بلا حساب، وبلا تسجيل دخول، وبلا خادم.

يُحتسب اليوم لأن فيه جلسة — لا حدّ عليك بلوغه، ولا نصف يوم، ولا عجز تقع فيه. اليوم الذي حضرت فيه خمسًا وأربعين دقيقة يُحتسب تمامًا كاليوم الذي حضرت فيه تسع ساعات. وتبقى ساعاتك ظاهرة في كل مكان — في المجاميع والرسوم البيانية والتقويم وملف التصدير CSV — مقدارًا، لا تقديرًا.

كيف يعمل
• سجّل حضورك لتبدأ جلسة، وسجّل انصرافك لتنهيها. وقتك في اليوم هو مجموع جلساتك المكتملة.
• يُؤكَّد كل تسجيل حضور وانصراف بفحص للوجه يجري على جهازك دون اتصال. وإن تعذّر إتمامه، يلجأ التطبيق إلى بصمتك أو قفل شاشة جهازك.
• يعمل مؤقّت مباشر في إشعار ما دمت مسجّلًا حضورك. ولا يُطرح شيء من أي جلسة أبدًا.
• الجلسات ثابتة عن قصد — لا تعديل، ولا حذف، ولا إدخال يدوي.
• إن نسيت تسجيل الانصراف، تُغلق الجلسة تلقائيًا عند منتصف الليل، في اليوم الذي بدأت فيه.

ما تراه
• تقويم يضع علامة على كل يوم حضرت فيه — علامة واحدة، هي نفسها لليوم القصير والطويل
• انقر أي يوم لترى جلساته من بدايتها إلى نهايتها، وما بلغه ذلك اليوم
• تقارير بالساعات اليومية والشهرية، وبالوقت الذي تبدأ فيه جلساتك من اليوم، وبعدد الجلسات في اليوم
• تصدير CSV عبر قائمة المشاركة في النظام
• بالعربية أو الإنجليزية — اختر من الإعدادات، أو اتبع لغة هاتفك

خاص بطبيعته
• بيانات جلساتك لا تغادر هذا الجهاز أبدًا. لا حساب، ولا خادم، ولا مزامنة.
• لا يملك التطبيق أي إذن بالوصول إلى الإنترنت، فلا يمكنه رفع أي شيء.
• لا تُلتقط أي صورة. تُبلغ كاميرا جهازك فقط بوجود وجه في الإطار، ولا يقرأ التطبيق سوى ذلك العدد — لا تغادر الإطارات الكاميرا، فلا توجد صورة تُحفظ أو تُعرض أو تُحذف أو تتسرّب.
• كشفٌ عن وجود وجه، لا تعرّفٌ عليه: يتأكد الفحص من وجود شخص، ولا يحدد هوية أحد ولا يطابق شيئًا.
• لا إعلانات. لا تحليلات. السؤال الوحيد الموجَّه إلى جهة أخرى هو سؤال Google Play عن وجود إصدار أحدث.
• سجلّك محفوظ في قاعدة بيانات محلية على هاتفك. صدّره إلى CSV متى شئت — تلك النسخة ملكك.

العادات
• احتفظ بسجل منفصل لكل شيء تحضر من أجله — النادي، أو الكتابة، أو التمرين.
• بدّل بينها من الشريط العلوي. لكل عادة تقويمها وتقاريرها وتصديرها.
• لا شيء يقارن عادة بأخرى. كل عادة سجلّ قائم بذاته.

التذكيرات
• إن لم تسجّل حضورك في عادة ما، يذكّرك Sleeves Up بذلك — مرة واحدة في ذلك اليوم، قرابة الوقت الذي اخترته لها.
• لإيقاف تذكير، اضغط عليه مطوّلًا؛ يفتح ذلك قناته ويوقفها بنقرة واحدة، دون أن يمسّ غيرها.

لمن هذا التطبيق
لروّاد الأعمال المستقلين، والعاملين لحسابهم الخاص، والمستشارين، والطلاب، وكل من يعمل دون رقابة خارجية ويريد أن يبني عادة الحضور بانتظام. كل يوم يُحتسب بالقدر نفسه.

Sleeves Up أداة للانضباط الذاتي، وليس بديلًا عن أي نظام لتسجيل الوقت لدى صاحب عمل أو لأغراض قانونية.
```

### What's new (500 max)

```
سجّل حضورك من الشاشة الرئيسية: أضف التطبيق المصغّر لـ Sleeves Up، وبلمسة واحدة تصل مباشرة إلى التحقق من الحضور. وإن كانت لديك أكثر من عادة، يسألك أيّها.

يعمل Sleeves Up الآن على Android 12 و12L.

حين يتوفر إصدار أحدث على Google Play، يمكن للتطبيق أن يعرضه عليك دون مغادرته.
```

### Screenshot captions

For an Arabic screenshot set, which is not shot yet: Play shows the en-GB set under the Arabic listing
until one is uploaded. Same slots as the phone table below, same order. Kept as a list rather than a
table, because `generate_screenshots.py` parses every table row of that shape as an en-GB caption.

1. يُحتسب اليوم لأنك حضرت.
2. سجّل حضورك بفحص للوجه لا يلتقط أي صورة.
3. 24 يومًا من 31. الساعات رقم، لا تقدير.
4. سجلّ لكل عادة. ولا شيء يقارن بينها.
5. مؤقّت مباشر ما دمت حاضرًا. ولا يُطرح منه شيء.
6. اعرف متى تبدأ فعلًا. لا توجد فترة هي الصحيحة.
7. لا إذن بالإنترنت إطلاقًا. لا يستطيع الرفع، لأنه لا يستطيع الاتصال.
8. صدّر كل شيء. تلك النسخة ملكك.

---

## Vocabulary that must stay out

Each of these described a mechanism the app no longer has, and each reads as plausible copy, which
is what makes them easy to write back in.

| Never say | Arabic: never say | Because |
|---|---|---|
| daily target · half day · full-day leave · leave deficit | هدف يومي · نصف يوم · إجازة يوم كامل · عجز | No day is measured against an hours bar. Rule 1. |
| pause · paused time · net hours | إيقاف مؤقت · وقت متوقف · الساعات الصافية | Nothing is subtracted from a session; `duration` is exactly `stopped_at - started_at`. |
| periodic presence check · re-verify | فحص دوري للحضور · إعادة التحقق | Only check-in and check-out are gated. There is no mid-session check. |
| selfie · photo · capture | سيلفي · التقاط صورة · تصوير (as what the check does; «لا تُلتقط أي صورة» is the denial) | The camera reports a face count as metadata. No image is ever produced. |
| attendance | نظام حضور وانصراف · دوام · كشف الحضور (the workplace register; الحضور for showing up is the app's own word) | The vocabulary was renamed out of the app; the tab is History. |
| goal · goals · target habit | هدف · أهداف · غاية — the word is عادة | The word is **habit**. A goal is something to reach, which is a target under another name — Rule 1. |
| most consistent habit · your best habit · compare your habits · total across habits | أكثر عاداتك انتظامًا · أفضل عادة · قارن عاداتك · مجموع العادات | Each habit is its own record and nothing sets two side by side. Ranking habits is the personal-best baseline applied across habits instead of across days. |
| streak · consecutive days · keep the chain going | سلسلة · أيام متتالية · لا تقطع السلسلة | A streak is a number the next day can take away. The calendar shows consecutive days visually and the app counts none of them. |
| longest day · best day · personal best · your record | أطول يوم · أفضل يوم · أفضل أداء شخصي · رقمك القياسي | Nothing ranks one of the user's days against the others, and nothing is a baseline anything else is drawn against. |
| roll up your sleeves · sleeves up! (as an instruction) · let's get to work | شمّر عن ساعديك · شمّر أكمامك! · هيا إلى العمل | The name describes a state, not an order. Nobody has ever rolled their sleeves up for nine hours, so the metaphor carries no measurement — which is exactly what lets it sit on an app where hours grade nothing. Said *at* the user on a cadence it becomes a mascot, and a mascot is the gamification register this app has none of. Gloss it once in the listing; never in a notification, a celebration or an empty state. |
| shaded by how long · intensity · ringed against your best | مظلّل حسب المدة · الشدّة · حلقة تقيسك بأفضل أداء | The calendar is binary, and the app fills no arc at all — the Check-In gauge is the brand mark breathing, and it says only whether a session is open. |

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
| Phone screenshots | 8 at 1080×1920 | `screenshots/phone/` — composited by `generate_screenshots.py` |
| Tablet screenshots | 4 at 2560×1600 | `screenshots/tablet/` — captured, uncaptioned. See below |

All three generators are deterministic, so re-running them is how you check the committed files are
current: identical output means no drift. See `README.md` for the capture procedure.

The feature graphic no longer composites the 512px icon — it draws the calendar fragment itself, and
shares only `CORNER_FRAC` with `generate_icons.py` — so regenerating the mark alone no longer leaves
it stale. That coupling was real while the graphic pasted the icon in, and it is gone; run both
anyway, since they are cheap and the wordmark and the mark are the same brand decision.

### The eight phone screenshots

Play shows only the **first two or three** in search results and in the "you might also like" rails,
and most people never scroll the strip. So 1–3 have to work as a standalone three-panel pitch and
4–8 are for someone already swiping. All eight are captioned in the same position, type and band —
a raw screenshot asks a stranger to decode a UI they have never seen, so the caption sells and the
screenshot corroborates.

| # | File | Shows | Caption |
|---|---|---|---|
| 1 | `01-calendar.png` | A complete month, binary, weekends mostly among the gaps | A day counts because you showed up. |
| 2 | `02-face-check.png` | The gate's disclosure, stating the mechanism in the app's words | Check in with a face check that takes no photo. |
| 3 | `03-summary.png` | Days shown up against days tracked; hours below, plain | 24 days out of 31. Hours are a number, not a grade. |
| 4 | `04-habits.png` | The Habits screen: three habits, each with its reminder time and nothing else | One record per habit. Nothing compares them. |
| 5 | `05-running.png` | The timer notification in the shade, chronometer running | A live timer while you're in. Nothing gets subtracted. |
| 6 | `06-start-times.png` | The morning / afternoon / evening split | See when you actually start. No bucket is the right one. |
| 7 | `07-privacy.png` | The in-app privacy screen | No internet permission at all, so it cannot upload. |
| 8 | `08-export.png` | The system share sheet mid-export | Export everything. That copy is yours. |

Slot 1 is the hero and earns it mechanically rather than by taste: every habit tracker on Play uses
an intensity ramp, so a binary calendar reads as *different* before a word is read, and what it
reads as is density rather than depth — which is a picture of the thesis. Slot 2 is second because
"no photo" is the objection people form on their own, and getting there first is worth more than any
feature. Slot 4 is the release's headline, placed first after the pitch: it shows names and reminder
times only, because a figure beside each habit would rank them, and its caption says so. Slot 6's second sentence is load-bearing: without it a viewer assumes morning is the good
column, because every other productivity app has taught them to.

Slot 7 is shot against **the app's own screen** (`ui/about/PrivacyScreen.kt`), never Android's system
permission page — that page is not this app's UI, it varies by OEM and version, and Play is
inconsistent about accepting system chrome. The claim is verifiable from the APK, which is what
makes it worth making; the screen states it and says where to check it.

**What may not appear in any of the eight** — each of these described a mechanism the app no longer
has, so a screenshot showing one is a screenshot of a build that does not exist:

- any ring, arc or progress bar filling toward a personal best
- any duration-shaded calendar
- any superlative — longest, best, record
- the word *streak*, including in a caption
- a perfect unbroken month: it contradicts the pitch, and it makes the app look like it is for
  people who do not need it

### Tablet screenshots

Four shots, reshot for 3.1 against the same seeded history as the phone set: the Check-In screen at
rest, History with a day selected, Reports, and the Habits screen. **They are uploaded uncaptioned**, unlike the phone
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

