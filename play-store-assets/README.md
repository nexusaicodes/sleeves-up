# Play Store assets

Graphics for the Google Play listing of **Sleeves Up**.

| File | Spec | Use |
|---|---|---|
| `generate_icons.py` | — | **Source of truth for the brand mark.** Emits the `pathData` for the app's vector drawables and renders the 512px Play icon |
| `../app/src/main/ic_launcher-playstore.png` | 512×512 PNG | App icon (generated — do not hand-edit) |
| `feature-graphic.png` | 1024×500 PNG | Feature graphic (generated) |
| `generate_feature_graphic.py` | — | Regenerates `feature-graphic.png` |
| `LISTING.md` | — | The store listing copy, the two rules governing it, and the vocabulary it must never use |
| `screenshots/raw/` | — | Device captures, uncaptioned. The input to `generate_screenshots.py` |
| `generate_screenshots.py` | — | Composites `raw/` into `screenshots/phone/`; captions parsed from `LISTING.md` |
| `screenshots/phone/` | 1080×1920 PNG ×7 | Phone screenshots (generated) |
| `screenshots/tablet/` | 2560×1600 PNG ×3 | Tablet screenshots — captured, uncaptioned, no compositing step |
| `seed_demo_data.py` | — | Writes the demo history the screenshots are taken against |

**Every generator is deterministic, so re-running one is also the check**: identical output means
the committed file is current. Comparing modification times instead gives a false alarm whenever two
were generated seconds apart from unchanged geometry.

Both scripts need Pillow (`pip install Pillow`) and run from the repo root.

## The brand mark

One unbroken stroke: an open progress ring whose terminal resolves into a check. The ring is
the day in progress; it doesn't close, it resolves.

`generate_icons.py` holds the geometry and emits everything derived from it:

```bash
python3 play-store-assets/generate_icons.py
```

It prints the `pathData` for `res/drawable/ic_launcher_foreground.xml` and
`ic_stat_checkin.xml`, and writes the 512px Play icon. If you change the geometry, paste the
printed paths into those two drawables — they are the only copies, and the script's `verify()`
guards the adaptive-icon safe circle on every run.

`ic_launcher_foreground.xml` does triple duty: launcher foreground, themed (monochrome) layer
and splash icon all point at it, so they cannot drift apart.

Two sizing rules are encoded there and are easy to get wrong by hand:

- The mark is sized against the **72dp window the launcher actually shows**, not the 66dp safe
  circle. Filling the safe circle is legal but reads oversized next to other icons.
- Arcs are emitted as cubic Béziers, not SVG `A` commands. Android's `PathParser` accepts both,
  but arc flags are the one place its behaviour diverges from browser renderers.

## Regenerating the feature graphic

```bash
python3 play-store-assets/generate_feature_graphic.py
```

It draws the wordmark, the tagline and a fragment of the binary calendar over a deep-indigo
gradient. It does **not** composite the icon any more — it shares only `CORNER_FRAC` with
`generate_icons.py` — so regenerating the mark alone can no longer leave it carrying a shape the app
has replaced. The gradient sits **below** the launcher indigo (`#3F51B5`) in value on purpose: the
filled cells are near-white and the empty ones a whisper of it, so the field has to clear both
states. Type is loaded from `app/src/main/res/font`, so there is no fallback chain and the output
does not depend on the machine. The calendar runs off the right edge rather than ending inside the
canvas, and the script asserts that — a month that ends on canvas reads as a complete object to be
inspected rather than as a record that continues.

## Regenerating the screenshots

Both sets were shot for 3.0 against the **release** build (`assembleRelease`), not the debug one, so
what the listing shows is what an installer gets. With no `keystore.properties` the release variant
falls back to the debug signing config, which is what makes it installable on an emulator at all —
and what lets the seeding below happen under the debug build and then be upgraded in place.

**Seeding needs a debuggable build or a rootable image, and the release APK is neither.** `run-as`
refuses a non-debuggable package and `adb root` is refused on a Play system image, so the sequence
is: install debug, walk the welcome tour (the DB has no tables until a screen queries it), pull,
seed, push, then `install -r` the release APK over the top. Same signature, so the data survives.
**Pull the WAL and shm files too** — Room leaves the whole schema in the write-ahead log, so `_app`
alone comes back as an empty file and the seed script fails with `no such table: sessions`.

The **phone** set is captured on `CheckIn_API33`, deliberately, not on the newer Pixel_8 AVD. It is
a `google_apis` image rather than a `google_apis_playstore` one, so `adb root` works and the clock
can be set to a working hour — an open session seeded for the afternoon reads `0m 0s` against an
emulator running at 1am. The API 33 shade also renders the foreground-service notification, which
the API 37 emulator does not. The **tablet** image is Play-only and so cannot have its clock set;
its seeded day is closed out instead, and the Check-In shot is the resting state.

They are taken against **seeded demo data on an emulator, never a real device** — a real install
has whatever history it happens to have, which is usually too thin to fill a calendar month or give
the splits anything to divide, and a real device puts the owner's own records into a public listing.

```bash
$ANDROID_HOME/emulator/emulator -avd Pixel_8 &          # or Pixel_Tablet
./gradlew :app:installDebug -Dorg.gradle.java.installations.paths="$JBR"
adb shell am start -n com.nexusai.checkin.app/com.checkin.app.MainActivity   # creates the DB
adb shell am force-stop com.nexusai.checkin.app
adb shell "run-as com.nexusai.checkin.app cat databases/_app" > /tmp/_app
python3 play-store-assets/seed_demo_data.py /tmp/_app
adb push /tmp/_app /sdcard/_app
adb shell "cat /sdcard/_app | run-as com.nexusai.checkin.app sh -c 'cat > databases/_app'"
adb shell "run-as com.nexusai.checkin.app rm -f databases/_app-wal databases/_app-shm"
```

Three things that are easy to get wrong:

- **`TODAY` in the seed script is a fixed date.** It has to match the emulator's own clock, or the
  seeded "today" is not today and the Check-In screen shows an empty day.
- **`date_key` must agree with the device timezone.** The script builds both from one `TZ` constant;
  change it to match the emulator rather than letting the two drift.
- **Delete `_app-wal` and `_app-shm` after pushing**, or Room replays the old write-ahead log over
  the file you just wrote.

Set the status bar with SystemUI demo mode (`adb shell am broadcast -a com.android.systemui.demo
-e command enter`, then `clock`/`battery`/`network`/`notifications`) so every shot in the set carries
the same clock and a full battery.
