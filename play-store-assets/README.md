# Play Store assets

Graphics for the Google Play listing of **Sleeves Up**.

| File | Spec | Use |
|---|---|---|
| `generate_icons.py` | — | **Source of truth for the brand mark.** Emits the `pathData` for the app's vector drawables and renders the 512px Play icon |
| `../app/src/main/ic_launcher-playstore.png` | 512×512 PNG | App icon (generated — do not hand-edit) |
| `generate_feature_graphic.py` | — | Renders `feature-graphic.png` |
| `feature-graphic.png` | 1024×500 PNG | Feature graphic (generated) |
| `LISTING.md` | — | Store listing copy, the rules governing it, and the vocabulary it must never use |
| `screenshots/raw/` | — | Device captures, uncaptioned; input to `generate_screenshots.py` |
| `generate_screenshots.py` | — | Composites `raw/` into `screenshots/phone/`, captions parsed from `LISTING.md` |
| `screenshots/phone/` | 1080×1920 PNG ×8 | Phone screenshots (generated) |
| `screenshots/tablet/` | 2560×1600 PNG ×4 | Tablet screenshots — captured, uncaptioned, not composited |
| `screenshots/all/` | — | Every screen of the last capture run, phone and tablet, for review. **Gitignored**: the listing files above are what ships |
| `seed_demo_data.py` | — | Writes the demo history the screenshots are taken against |

The generators need Pillow (`pip install Pillow`) and run from the repo root. Each is
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

`ic_launcher_foreground.xml` is the launcher foreground and the themed (monochrome) layer, and the
splash draws it through `ic_splash_icon.xml`, an `<inset>` that only enlarges it — so the three
cannot drift. Sizing rules encoded in the script:

- The mark is sized against the **72dp window the launcher actually shows**, not the 66dp safe
  circle, which would read oversized next to other icons.
- Corners are cubic Béziers, not SVG `A` commands: arc flags are where Android's `PathParser`
  diverges from browser renderers.

## Regenerating the feature graphic

```bash
python3 play-store-assets/generate_feature_graphic.py
```

It draws the wordmark, the tagline and a fragment of the binary calendar over a deep-indigo
gradient, sharing only `CORNER_FRAC` with `generate_icons.py`. The gradient sits below the launcher
indigo (`#3F51B5`) in value so both the near-white filled cells and the faint empty ones read. Type
is loaded from `app/src/main/res/font`, so output does not depend on the machine. The calendar runs
off the right edge — asserted by the script — so it reads as a continuing record, not a complete
object.

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
python3 play-store-assets/seed_demo_data.py /tmp/_app     # checkpoints the WAL into _app
U=$(adb shell stat -c %u /data/data/$P)
adb push /tmp/_app $D/_app
adb shell "rm -f $D/_app-wal $D/_app-shm; chown $U:$U $D/_app; chmod 660 $D/_app; restorecon $D/_app"
```

The same seeded `_app` can be pushed to the tablet after its own first run. Reminder times live in
`reminder_prefs`, keyed by habit uuid, so set them through the Habits screen's editor on each device
(Writing 09:00, Gym 18:00, Guitar 20:00) rather than expecting them to travel with the database.

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
then copy the listing picks into `screenshots/raw/` (phone, named as LISTING.md's table) and
`screenshots/tablet/`, and run `python3 play-store-assets/generate_screenshots.py`.
