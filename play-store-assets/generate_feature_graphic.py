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

The Arabic graphic is the same composition mirrored, as an RTL reader scans it: the words on the
right and the calendar running off the left edge. The wordmark stays in Latin script and in Outfit,
as `app_name` does in the app; the tagline is the Arabic listing's first caption, set in IBM Plex
Sans Arabic with raqm's right-to-left shaping, so that one needs a Pillow with raqm (see README.md).

Run from the repo root:  python3 play-store-assets/generate_feature_graphic.py [en-GB|ar]
"""

import sys

from PIL import Image, ImageDraw, ImageFont, features

sys.path.insert(0, "play-store-assets")
from generate_icons import CORNER_FRAC  # noqa: E402  -- one source for the cell's shape

W, H = 1024, 500
FONT_DIR = "app/src/main/res/font"

# The app's palette (ui/theme/Color.kt): the field runs from ink, the launcher tile's colour, down
# to the dark theme's paper, so the filled cells (paper) and the empty ones (a whisper of it) read
# as two states against a field well below both in value.
TOP = (27, 42, 74)       # ink #1B2A4A
BOT = (14, 24, 48)       # dark paper #0E1830
PAPER = (241, 230, 206)  # paper #F1E6CE: the cells and the wordmark
MUTED = (167, 176, 194)  # dark inkMuted #A7B0C2: the tagline

WORDMARK = "Sleeves Up"

# Per locale: output, tagline, its face and size, layout, direction, and whether the composition is
# mirrored. The Arabic tagline is the Arabic listing's first screenshot caption, as the English one
# is the English listing's; Plex Arabic runs smaller on the em than Manrope, hence the larger size.
LOCALES = {
    "en-GB": ("play-store-assets/feature-graphic.png", "A day counts because you showed up.",
              "manrope_medium", 29, ImageFont.Layout.BASIC, None, False),
    "ar": ("play-store-assets/feature-graphic-ar.png", "يُحتسب اليوم لأنك حضرت.",
           "plex_arabic_medium", 34, ImageFont.Layout.RAQM, "rtl", True),
}

# --- the calendar fragment -------------------------------------------------------------

COLS, ROWS = 7, 5
CELL = 52
GAP = 12
GRID_X, GRID_Y = 620, 88

# The pitch is what it is because three things have to hold at once on a 1024px canvas: seven
# columns, because rows are weeks and a week that is not seven days stops reading as a calendar;
# the last column straddling the right edge rather than ending inside it, which is the bleed the
# docstring is about; and the whole grid clearing the tagline, which is the widest string here.
# Both ends are asserted below -- a column that starts past the canvas draws nothing at all, and
# the block then reads as a crop rather than as a record that continues.

# Five weeks with ten gaps in them. Scattered rather than clustered, and never a whole
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


def font(name, size, layout=ImageFont.Layout.BASIC):
    # Basic layout unless asked: Latin needs no shaping, and the committed output is basic's.
    return ImageFont.truetype(f"{FONT_DIR}/{name}.ttf", size, layout_engine=layout)


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


def main(locale):
    out, tagline, tag_face, tag_size, tag_layout, direction, mirrored = LOCALES[locale]
    img = gradient()
    draw = ImageDraw.Draw(img, "RGBA")

    def x_of(x0, width):
        # A left edge in the English composition, moved to its mirror image under RTL.
        return W - x0 - width if mirrored else x0

    radius = int(CELL * CORNER_FRAC)
    for row in range(ROWS):
        for col in range(COLS):
            x0 = x_of(GRID_X + col * (CELL + GAP), CELL)
            y0 = GRID_Y + row * (CELL + GAP)
            filled = (row, col) not in EMPTY
            draw.rounded_rectangle(
                [x0, y0, x0 + CELL, y0 + CELL],
                radius=radius,
                fill=(*PAPER, 235) if filled else (*PAPER, 28),
            )

    f_word = font("outfit_bold", 92)
    f_tag = font(tag_face, tag_size, tag_layout)

    tx = 72
    word_w = draw.textlength(WORDMARK, font=f_word)
    tag_w = draw.textlength(tagline, font=f_tag, direction=direction)
    word_xy = (x_of(tx, word_w), 186)
    tag_xy = (x_of(tx + 3, tag_w), 300)
    draw.text(word_xy, WORDMARK, font=f_word, fill=(*PAPER, 255))
    draw.text(tag_xy, tagline, font=f_tag, fill=(*MUTED, 255), direction=direction)

    # The text must not collide with the grid. Asserted rather than eyeballed: the wordmark
    # is the one string here that a rename would lengthen, and a silent overlap in a 1024px
    # graphic is the kind of thing that ships. Measured in the English frame, where the text
    # runs rightwards towards the grid; a mirrored graphic is measured from the right edge.
    boxes = [draw.textbbox(word_xy, WORDMARK, font=f_word),
             draw.textbbox(tag_xy, tagline, font=f_tag, direction=direction)]
    right = W - min(b[0] for b in boxes) if mirrored else max(b[2] for b in boxes)
    assert right < GRID_X - 24, f"text reaches {right}, grid starts at {GRID_X}"

    # The last column must begin on the canvas and end off it. Without the second half the
    # column is simply never drawn, taking its share of EMPTY with it, and the grid ends short
    # of the edge as a finished object -- which is the one thing this composition may not be.
    last_x0 = GRID_X + (COLS - 1) * (CELL + GAP)
    assert last_x0 < W < last_x0 + CELL, f"last column {last_x0}..{last_x0 + CELL} does not straddle {W}"

    img.save(out, "PNG")
    print(f"wrote {out} {img.size}  text edge {right} of {GRID_X}, "
          f"last column {last_x0}..{last_x0 + CELL} across {W}")


if __name__ == "__main__":
    chosen = sys.argv[1:] or list(LOCALES)
    for name in chosen:
        if LOCALES[name][4] == ImageFont.Layout.RAQM and not features.check("raqm"):
            raise SystemExit(f"the {name} graphic needs Pillow with raqm, which this one lacks; see README.md.")
        main(name)
