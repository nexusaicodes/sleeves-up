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
| `screenshots/phone/` | 1080×1920 PNG ×7 | Phone screenshots (generated) |
| `screenshots/tablet/` | 2560×1600 PNG ×3 | Tablet screenshots — captured, uncaptioned, not composited |
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

Shoot against the **release** build (`assembleRelease`), so the listing shows what an installer
gets. With no `keystore.properties`, release falls back to the debug signing config, which makes it
installable on an emulator and lets it upgrade a seeded debug install in place.

Use **seeded demo data on an emulator, never a real device**: a real install's history is too thin
to fill a month, and it would put the owner's records into a public listing.

**Seeding needs a debuggable build**: `run-as` refuses a release package and `adb root` is refused
on Play system images. So: install debug, walk the welcome tour and name the habit (the database has
no tables until a screen queries it), pull, seed, push, then `install -r` the release APK over it.

Phone shots use the `CheckIn_API33` AVD — a `google_apis` image, so `adb root` works and the clock
can be set to a working hour (an open session seeded for the afternoon reads `0m 0s` at 1am), and
its shade renders the foreground-service notification, which the API 37 emulator does not. Tablet
shots use `Pixel_Tablet`, a Play image whose clock cannot be set, so its seeded day is closed out
and the Check-In shot is the resting state.

```bash
$ANDROID_HOME/emulator/emulator -avd CheckIn_API33 &     # or Pixel_Tablet
./gradlew :app:installDebug -Dorg.gradle.java.installations.paths="$JBR"
adb shell am start -n com.nexusai.checkin.app/com.checkin.app.MainActivity   # walk the tour
adb shell am force-stop com.nexusai.checkin.app
for f in _app _app-wal _app-shm; do
  adb shell "run-as com.nexusai.checkin.app cat databases/$f" > /tmp/$f
done
python3 play-store-assets/seed_demo_data.py /tmp/_app     # checkpoints the WAL into _app
adb push /tmp/_app /sdcard/_app
adb shell "cat /sdcard/_app | run-as com.nexusai.checkin.app sh -c 'cat > databases/_app'"
adb shell "run-as com.nexusai.checkin.app rm -f databases/_app-wal databases/_app-shm"
```

Easy to get wrong:

- **Pull the WAL and shm files too.** Room keeps the schema in the write-ahead log, so `_app` alone
  is an empty file and seeding fails with `no such table: sessions`.
- **`TODAY` in the seed script is a fixed date** and must match the emulator's clock, or the
  Check-In screen shows an empty day.
- **`date_key` must agree with the device timezone.** The script derives both from its `TZ`
  constant; set it to the emulator's zone.
- **Delete `_app-wal` and `_app-shm` on the device after pushing**, or Room replays the old log over
  the seeded file.

Set the status bar with SystemUI demo mode (`adb shell am broadcast -a com.android.systemui.demo
-e command enter`, then `clock`/`battery`/`network`/`notifications`) so every shot carries the same
clock and a full battery.
