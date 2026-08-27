"""Composites the captioned Play screenshots from raw device captures.

Reads  play-store-assets/screenshots/raw/<NN-name>.png   (whatever the device gave)
Writes play-store-assets/screenshots/phone/<NN-name>.png (1080x1920, 9:16)

**The captions are not stored here.** They are parsed out of the screenshot table in
LISTING.md, because that table is what a human reads and edits when the pitch changes, and a
second copy in this file is a copy that can disagree with the listing it is supposed to
illustrate. The parse is deliberately brittle: a table that stops matching raises rather than
silently rendering an empty band, which is the same call `verifyLicenseCoverage` makes when it
parses coordinates out of a Kotlin source file.

Why composite at all rather than upload raw captures: only the first two or three screenshots
are ever seen, and a raw screenshot asks a stranger to decode a UI they have never met. The
caption sells and the shot corroborates. Every panel therefore uses the same band, the same
type and the same field -- a set that varies reads as a set assembled from whatever was to
hand.

Output is 9:16 regardless of what the device's aspect happens to be, which is the point of
compositing into a fixed canvas: a 20:9 phone capture dropped straight into the listing is
letterboxed by Play, and a crop to 9:16 would cut app UI.

Run from the repo root:  python3 play-store-assets/generate_screenshots.py
"""

import os
import re
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

sys.path.insert(0, "play-store-assets")
from generate_feature_graphic import BOT, TOP, font  # noqa: E402 -- one field for the brand

W, H = 1080, 1920
RAW = "play-store-assets/screenshots/raw"
OUT = "play-store-assets/screenshots/phone"
LISTING = "play-store-assets/LISTING.md"

MARGIN = 88
CAPTION_TOP = 104
CAPTION_SIZE = 52
CAPTION_LEADING = 66
DEVICE_TOP = 320
DEVICE_BOTTOM = 1856
DEVICE_RADIUS = 38
EXPECTED = 7

ROW = re.compile(r"^\|\s*(\d)\s*\|\s*`([^`]+)`\s*\|[^|]*\|\s*([^|]+?)\s*\|\s*$")


def captions():
    """(filename, caption) for each row of LISTING.md's screenshot table, in order."""
    out = []
    for line in open(LISTING, encoding="utf-8"):
        m = ROW.match(line.rstrip("\n"))
        if m:
            out.append((m.group(2), m.group(3)))
    if len(out) != EXPECTED:
        raise SystemExit(
            f"parsed {len(out)} caption rows from {LISTING}, expected {EXPECTED}. "
            "The screenshot table's shape changed -- fix the parse rather than the table."
        )
    return out


def wrap(draw, text, fnt, width):
    lines, words = [], text.split()
    while words:
        line = words.pop(0)
        while words and draw.textlength(f"{line} {words[0]}", font=fnt) <= width:
            line += f" {words.pop(0)}"
        lines.append(line)
    return lines


def gradient():
    small = Image.new("RGB", (64, 64))
    px = small.load()
    for y in range(64):
        for x in range(64):
            t = (x / 64) * 0.35 + (y / 64) * 0.65
            px[x, y] = tuple(int(a + (b - a) * t) for a, b in zip(TOP, BOT))
    return small.resize((W, H), Image.BILINEAR)


def rounded(img, radius):
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, img.width - 1, img.height - 1],
                                           radius=radius, fill=255)
    out = img.convert("RGBA")
    out.putalpha(mask)
    return out


def compose(raw_path, caption):
    canvas = gradient()
    draw = ImageDraw.Draw(canvas, "RGBA")

    fnt = font("outfit_bold", CAPTION_SIZE)
    lines = wrap(draw, caption, fnt, W - MARGIN * 2)
    if len(lines) > 2:
        # Three lines would run into the device. Shrink once and re-wrap rather than clipping:
        # the long captions are the ones carrying the claim, so losing their tail is the worst
        # available failure.
        fnt = font("outfit_bold", 44)
        lines = wrap(draw, caption, fnt, W - MARGIN * 2)
    for i, line in enumerate(lines):
        tw = draw.textlength(line, font=fnt)
        draw.text(((W - tw) / 2, CAPTION_TOP + i * CAPTION_LEADING), line,
                  font=fnt, fill=(255, 255, 255, 255))

    shot = Image.open(raw_path).convert("RGB")
    box_h = DEVICE_BOTTOM - DEVICE_TOP
    box_w = int(box_h * shot.width / shot.height)
    shot = shot.resize((box_w, box_h), Image.LANCZOS)
    x = (W - box_w) // 2

    shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle(
        [x + 6, DEVICE_TOP + 16, x + box_w + 6, DEVICE_BOTTOM + 16],
        radius=DEVICE_RADIUS, fill=(0, 0, 0, 120))
    # Blurred once and used as both the source and its own mask: the filter is a full-canvas
    # convolution and running it twice for one paste costs a second of it per panel.
    shadow = shadow.filter(ImageFilter.GaussianBlur(22))
    canvas.paste(shadow, (0, 0), shadow)

    framed = rounded(shot, DEVICE_RADIUS)
    canvas.paste(framed, (x, DEVICE_TOP), framed)
    return canvas


def main():
    os.makedirs(OUT, exist_ok=True)
    rows = captions()
    missing = [n for n, _ in rows if not os.path.exists(f"{RAW}/{n}")]
    if missing:
        raise SystemExit(f"no raw capture for: {', '.join(missing)}\n"
                         f"See README.md for the capture procedure; raws live in {RAW}/.")
    for name, caption in rows:
        out = f"{OUT}/{name}"
        compose(f"{RAW}/{name}", caption).convert("RGB").save(out)
        print(f"wrote {out}  “{caption}”")


if __name__ == "__main__":
    main()
