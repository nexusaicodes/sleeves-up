# Play Store assets

Graphics for the Google Play listing of **Sleeves Up**.

| File | Spec | Use |
|---|---|---|
| `generate_icons.py` | — | **Source of truth for the brand mark.** Emits the `pathData` for the app's vector drawables and renders the 512px Play icon |
| `../app/src/main/ic_launcher-playstore.png` | 512×512 PNG | App icon (generated — do not hand-edit) |
| `feature-graphic.png` | 1024×500 PNG | Feature graphic (generated) |
| `generate_feature_graphic.py` | — | Regenerates `feature-graphic.png` |
| `LISTING.md` | — | The store listing copy, the two rules governing it, and the vocabulary it must never use |
| `screenshots/phone/` | 1080×2400 PNG ×4 | Phone screenshots |
| `screenshots/tablet/` | 2560×1600 PNG ×3 | Tablet screenshots (two-pane History) |
| `seed_demo_data.py` | — | Writes the demo history the screenshots are taken against |

**The feature graphic composites the real 512px icon, so regenerating the mark alone leaves it
stale** — that is how the graphic once kept a round-capped mark the app had already replaced with a
butt-capped one. Run both generators together. **Both are deterministic, so re-running them is also
the check**: identical output means the committed files are current. Comparing modification times
instead gives a false alarm whenever the two were generated seconds apart from unchanged geometry.

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

It composites the real 512px icon over a deep-indigo gradient with the wordmark and privacy
tagline. The gradient sits **below** the launcher indigo (`#3F51B5`) in value on purpose — the
icon tile is flat brand indigo, so a same-value field would swallow it. Fonts fall back across
common macOS system faces.

## Regenerating the screenshots

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
