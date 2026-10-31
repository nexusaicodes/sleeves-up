# Play Store assets

Graphics for the Google Play listing of **Sleeves Up**.

| File | Spec | Use |
|---|---|---|
| `generate_icons.py` | — | **Source of truth for the brand mark.** Emits the `pathData` for the app's vector drawables and renders the 512px Play icon |
| `../app/src/main/ic_launcher-playstore.png` | 512×512 PNG | App icon (generated — do not hand-edit) |
| `generate_feature_graphic.py` | — | Renders `feature-graphic.png` and `feature-graphic-ar.png` |
| `feature-graphic.png` | 1024×500 PNG | Feature graphic, en-GB (generated) |
| `feature-graphic-ar.png` | 1024×500 PNG | Feature graphic, ar (generated) |
| `LISTING.md` | — | Store listing copy, the rules governing it, and the vocabulary it must never use |
| `screenshots/<locale>/raw/` | — | Device captures, uncaptioned; input to `generate_screenshots.py`. One folder per listing language: `en-GB`, `ar` |
| `generate_screenshots.py` | — | Composites each `raw/` into its `phone/`, captions parsed from `LISTING.md` |
| `screenshots/<locale>/phone/` | 1080×1920 PNG ×8 | Phone screenshots (generated) |
| `screenshots/en-GB/tablet/` | 2560×1600 PNG ×4 | Tablet screenshots — captured, uncaptioned, not composited. English only; Play shows them under every language |
| `screenshots/all/` | — | Every screen of the last capture run, phone and tablet, for review. **Gitignored**: the listing files above are what ships |
| `seed_demo_data.py` | — | Writes the demo history the screenshots are taken against |

The generators need Pillow (`pip install Pillow`) and run from the repo root; the Arabic screenshots
also need raqm (see Regenerating the screenshots). Each is
deterministic, so re-running one is also the check: identical output means the committed file is
current.

## The brand mark

The binary calendar: a 3×3 grid of rounded cells, seven filled and two empty. A day is a cell or
nothing — no shading, no ramp — and the two gaps say "showed up, mostly" rather than "perfect
month".

```bash
python3 play-store-assets/generate_icons.py
```

It prints the `pathData` for `res/drawable/ic_launcher_foreground.xml` and `ic_stat_checkin.xml`,
and writes the 512px Play icon. After changing the geometry, paste the printed paths into those two
drawables (never hand-edit a path), and update the same constants in the app's `BrandGrid.kt`, which
draws the mark cell by cell in Compose. `verify()` asserts the adaptive-icon safe circle on every
run.

`ic_launcher_foreground.xml` is the launcher foreground and the themed (monochrome) layer. The
script also writes the animated splash, `ic_splash_icon.xml`; re-run it after changing the geometry
or `BrandGrid`'s settle timing. Sizing rules encoded in the script:

- The mark is sized against the **72dp window the launcher actually shows**, not the 66dp safe
  circle, which would read oversized next to other icons.
- Corners are cubic Béziers, not SVG `A` commands: arc flags are where Android's `PathParser`
  diverges from browser renderers.

## Regenerating the feature graphic

```bash
python3 play-store-assets/generate_feature_graphic.py   # both languages, or name one: en-GB, ar
```

It draws the wordmark, the tagline and a fragment of the binary calendar over a gradient from ink
(`#1B2A4A`, the launcher tile) to the dark theme's paper (`#0E1830`), sharing only `CORNER_FRAC`
with `generate_icons.py`. The cells and wordmark are paper (`#F1E6CE`) and the tagline the dark
theme's muted ink, so both the filled cells and the faint empty ones read against the field. The
screenshots' field and caption colour are imported from it, so the two stay one brand. Type
is loaded from `app/src/main/res/font`, so output does not depend on the machine. The calendar runs
off the right edge — asserted by the script — so it reads as a continuing record, not a complete
object. The Arabic graphic is the same composition mirrored: the words on the right, the calendar
off the left edge, the wordmark still in Latin script and Outfit, and the tagline the Arabic
listing's first caption in IBM Plex Sans Arabic. Like the Arabic screenshots it is set with raqm, so
it needs the same Pillow (see Regenerating the screenshots); without raqm the English graphic still
generates and the Arabic one stops with a message.

**The file bytes depend on the Pillow version, the pixels do not.** Under the venv's Pillow 11.3,
`feature-graphic.png` renders pixel-identical to the committed one but encodes to different PNG
bytes, so git reports it modified. Render each graphic under the Pillow that produced it — the
English one with `python3 … en-GB`, the Arabic one with `/tmp/assets-venv/bin/python … ar` — or
`git checkout` the English file after a run of both; that diff changes nothing on screen.

## Regenerating the screenshots

Shoot against the **release** build (`assembleRelease`, signed with the upload key), so the listing
shows what an installer gets. Use **seeded demo data on an emulator, never a real device**: a real
install's history is too thin to fill a month, and it would put the owner's records into a public
listing.

Both AVDs run the `android-33/google_apis` image, so `adb root` works and the clock can be set. That
is what lets the release build be seeded at all: `run-as` refuses a release package, but root can
write its database directly.

| AVD | Device | Use |
|---|---|---|
| `CheckIn_API33` | pixel_6, 1080×2400 | Phone set; its shade renders the foreground-service notification, which the API 37 emulator's does not |
| `CheckIn_Tablet_API33` | pixel_tablet, 2560×1600 | Tablet set; expanded width, so History is two-pane. Create it with `avdmanager create avd -n CheckIn_Tablet_API33 -k "system-images;android-33;google_apis;arm64-v8a" -d pixel_tablet` |

```bash
$ANDROID_HOME/emulator/emulator -avd CheckIn_API33 -timezone Asia/Dubai -camera-front emulated &
adb root
adb shell settings put global auto_time 0
adb shell date 082816052026.00          # TODAY in the seed script, 16:05 (after the open session's start)
adb shell locksettings set-pin 1234      # the gate's device-unlock fallback; no emulated camera finds a face
adb install -r app/build/outputs/apk/release/app-release.apk
# Open the app and walk first run (tour, naming step, notification ask) — capture it on the way.
# The database has no tables until a screen queries it.
P=com.nexusai.checkin.app; D=/data/data/$P/databases
adb shell am force-stop $P
for f in _app _app-wal _app-shm; do adb exec-out "cat $D/$f" > /tmp/$f; done
python3 play-store-assets/seed_demo_data.py /tmp/_app en-GB   # or ar; checkpoints the WAL into _app
U=$(adb shell stat -c %u /data/data/$P)
adb push /tmp/_app $D/_app
adb shell "rm -f $D/_app-wal $D/_app-shm; chown $U:$U $D/_app; chmod 660 $D/_app; restorecon $D/_app"
```

The same seeded `_app` can be pushed to the tablet after its own first run. Reminder times live in
`reminder_prefs`, keyed by habit uuid, and do not travel with the database. Set Writing's (09:00) in
the naming step. The seed opens a session, which locks the Habits screen, so Gym's (18:00) and
Guitar's (20:00) go straight into the prefs file under the uuids `seed_demo_data.py` fixes:

```bash
F=/data/data/$P/shared_prefs/reminder_prefs.xml
adb shell am force-stop $P
adb exec-out cat $F > /tmp/rp.xml
# add inside <map>:
#   <int name="reminder_minute_5c0d1e2f-0000-4000-8000-00000000a002" value="1080" />
#   <int name="reminder_minute_5c0d1e2f-0000-4000-8000-00000000a003" value="1200" />
adb push /tmp/rp.xml $F
adb shell "chown $U:$U $F; chmod 660 $F; restorecon $F"
```

The Habits panels and the tablet's resting Check-In screen are shot after checking out through the
gate: no emulated camera finds a face, so wait for **Use device unlock** and enter the PIN.

A reinstall can come back in Arabic: Android 13 backs up and restores per-app locales with the app.
Clear it with `adb shell cmd locale set-app-locales $P --locales ""` before the first run.

**The Arabic set** is shot the same way, with three differences. Seed with `ar`, which names the habits
الكتابة، الرياضة، الغيتار over the same record, so "24 of 31" still holds. Set the app to Arabic
before its first run (`cmd locale set-app-locales $P --locales ar`). And run the whole device in
Arabic, with Latin digits as the app uses, so the shade, the share sheet and the permission dialogs
are Arabic too and the status bar mirrors:

```bash
adb shell "setprop persist.sys.locale ar-AE-u-nu-latn; setprop ctl.restart zygote"
```

The UAE region starts the week on Saturday, which History then shows. Set `persist.sys.locale` back
to `en-US` (and restart zygote) afterwards.

Easy to get wrong:

- **Pull the WAL and shm files too.** Room keeps the schema in the write-ahead log, so `_app` alone
  is an empty file and seeding fails with `no such table: sessions`.
- **`TODAY` in the seed script is a fixed date** and must match the emulator's clock, or the
  Check-In screen shows an empty day. It also anchors the searched `random.seed` that makes July
  24 of 31 — the summary caption — so move neither without re-searching.
- **`date_key` must agree with the device timezone.** The script derives both from its `TZ`
  constant; launch the emulator with `-timezone` to match.
- **Delete `_app-wal` and `_app-shm` on the device after pushing**, or Room replays the old log over
  the seeded file.
- **`-camera-front` takes `emulated`, not `virtualscene`** (that is back-camera only); without it
  the presence check's preview is black. Never `webcam0`, which would put the host's camera feed in
  a capture.
- **The tablet's taskbar** is the launcher's: hide it with
  `adb shell pm disable-user --user 0 com.google.android.apps.nexuslauncher` plus the three-button
  navigation overlay (`cmd overlay enable-exclusive --category
  com.android.internal.systemui.navbar.threebutton`). Re-enable the launcher afterwards.

Set the status bar with SystemUI demo mode (`adb shell settings put global sysui_demo_allowed 1`,
then `am broadcast -a com.android.systemui.demo -e command enter`, then `clock -e hhmm 1605`,
`battery -e level 100`, `network -e wifi show -e fully true -e level 4`, `notifications -e visible
false`) so every shot carries the same clock and a full battery. Re-send `notifications` before each
capture: opening the shade restores the app's status-bar icon.

Capture with `adb exec-out screencap -p > play-store-assets/screenshots/all/<device>/NN-name.png`,
then copy the listing picks into `screenshots/<locale>/raw/` (phone, named as LISTING.md's table) and
`screenshots/en-GB/tablet/`, and run `python3 play-store-assets/generate_screenshots.py` (both
languages, or name one: `en-GB`, `ar`). Re-set the clock and restart the app just before the shade
shot: the timer notification's clock is fixed when it is posted.

**English captions are set with Pillow's basic layout and Arabic ones with raqm**, chosen per font,
so one Pillow 11.3 with raqm produces both, and the English set reproduces pixel for pixel. raqm
needs FriBiDi at runtime: Pillow's wheels load it if installed (`brew install fribidi`), but only in a
Python of the same architecture, so on Apple silicon use Homebrew's Python:

```bash
/opt/homebrew/bin/python3.13 -m venv /tmp/assets-venv
/tmp/assets-venv/bin/pip install "pillow==11.3.0"
/tmp/assets-venv/bin/python play-store-assets/generate_screenshots.py
```

Without raqm the English set still generates, and the Arabic one stops with a message. Arabic
captions break between sentences when each fits a line, else where the two lines are most even.
Before committing, compare pixels, not bytes: a different PNG encoder rewrites identical images, and
the panels you did not re-shoot must come out identical.
