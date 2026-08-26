"""Regenerates play-store-assets/feature-graphic.png (1024x500).

Three elements and no more: the wordmark, the tagline, and a fragment of the binary
calendar. Deliberately **no device frame** -- at 1024x500 a phone shrinks to a thumbnail and
spends the half of the canvas the words need. The graphic runs in Play placements and in most
third-party listings and blog embeds, so it is read small and read once.

The calendar fragment bleeds off the right edge rather than sitting centred in reserve space:
a month that ends inside the canvas reads as a complete object to be inspected, and a month
that runs off it reads as a record that continues -- which is the thing being sold.

It is **not a perfect month**, for the same reason the listing screenshots may not show one:
an unbroken grid says "this app is for people who never miss", which is the opposite of the
pitch. The gaps are the honest part.

Type is the app's own: Outfit for the wordmark, Manrope for the tagline, loaded from
app/src/main/res/font. That is the same split Type.kt makes on screen -- Outfit on display
sizes, Manrope on reading sizes -- and the files are committed, so this script needs no font
fallback chain and renders identically on any machine.

Run from the repo root:  python3 play-store-assets/generate_feature_graphic.py
"""

import sys

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, "play-store-assets")
from generate_icons import CORNER_FRAC  # noqa: E402  -- one source for the cell's shape

W, H = 1024, 500
OUT = "play-store-assets/feature-graphic.png"
FONT_DIR = "app/src/main/res/font"

# Gradient endpoints, deliberately deeper than the launcher indigo (#3F51B5): the filled
# cells are near-white and the empty ones are a whisper of it, so the field has to sit well
# below both in value or the grid stops reading as two states.
TOP = (46, 59, 132)      # #2E3B84
BOT = (26, 33, 84)       # #1A2154

WORDMARK = "Sleeves Up"
TAGLINE = "A day counts because you showed up."

# --- the calendar fragment -------------------------------------------------------------

COLS, ROWS = 7, 5
CELL = 62
GAP = 14
GRID_X, GRID_Y = 646, 58

# Five weeks with eleven gaps in them. Scattered rather than clustered, and never a whole
# empty row: a blank week reads as a fortnight off, which is a story this graphic is not
# telling. Rows are weeks, so the two-day gaps that fall together read as a weekend without
# the graphic ever having to claim weekends are exempt -- they are not.
EMPTY = frozenset({
    (0, 0), (0, 5),
    (1, 3), (1, 6),
    (2, 1),
    (3, 2), (3, 5), (3, 6),
    (4, 0), (4, 4),
})


def font(name, size):
    return ImageFont.truetype(f"{FONT_DIR}/{name}.ttf", size)


def gradient():
    """Diagonal gradient, built small and scaled: a per-pixel Python loop over half a
    million pixels costs seconds and buys nothing a bilinear upscale does not."""
    small = Image.new("RGB", (64, 64))
    px = small.load()
    for y in range(64):
        for x in range(64):
            t = (x / 64) * 0.45 + (y / 64) * 0.55
            px[x, y] = tuple(int(a + (b - a) * t) for a, b in zip(TOP, BOT))
    return small.resize((W, H), Image.BILINEAR)


def main():
    img = gradient()
    draw = ImageDraw.Draw(img, "RGBA")

    radius = int(CELL * CORNER_FRAC)
    for row in range(ROWS):
        for col in range(COLS):
            x0 = GRID_X + col * (CELL + GAP)
            y0 = GRID_Y + row * (CELL + GAP)
            filled = (row, col) not in EMPTY
            draw.rounded_rectangle(
                [x0, y0, x0 + CELL, y0 + CELL],
                radius=radius,
                fill=(255, 255, 255, 235) if filled else (255, 255, 255, 28),
            )

    f_word = font("outfit_bold", 92)
    f_tag = font("manrope_medium", 29)

    tx = 72
    draw.text((tx, 186), WORDMARK, font=f_word, fill=(255, 255, 255, 255))
    draw.text((tx + 3, 300), TAGLINE, font=f_tag, fill=(206, 214, 255, 255))

    # The text must not collide with the grid. Asserted rather than eyeballed: the wordmark
    # is the one string here that a rename would lengthen, and a silent overlap in a 1024px
    # graphic is the kind of thing that ships.
    right = max(draw.textbbox((tx, 186), WORDMARK, font=f_word)[2],
                draw.textbbox((tx + 3, 300), TAGLINE, font=f_tag)[2])
    assert right < GRID_X - 24, f"text reaches {right}, grid starts at {GRID_X}"

    img.save(OUT, "PNG")
    print(f"wrote {OUT} {img.size}  text right edge {right} of {GRID_X}")


if __name__ == "__main__":
    main()
