"""Single source of truth for the Sleeves Up brand mark.

The mark is the binary calendar: a 3x3 grid of rounded cells, seven filled and two empty.
It is the product's thesis rendered as a shape -- a day counts because it has a session, so
a day is a cell or it is nothing, and what you read is density rather than depth. Nothing
here is shaded, ramped or ranked; there is no fraction anywhere in this file, by design.

Drawn on a 108x108 canvas so it drops straight into an adaptive-icon layer, with every
point inside the 66dp safe circle (radius 33 from centre).

Emits, from the same geometry:
  * pathData for res/drawable/ic_launcher_foreground.xml -- which is simultaneously the
    launcher foreground, the themed/monochrome layer and the splash icon
  * pathData for res/drawable/ic_stat_checkin.xml (24dp notification silhouette)
  * app/src/main/ic_launcher-playstore.png (512x512 Play listing icon)

WHY NOT A ROLLED SLEEVE. The name suggests one, and it was tried: as a constant-width
stroke it reads as a hammer, as a stepped silhouette as a chess pawn, and reduced to the
"cuff line and fold" it reads as a pipe. Every variant was rendered at a true 48dp and
judged there rather than at 512. Sleeve folds are fine detail and fine detail is exactly
what a launcher grid destroys -- and the one reading that would have rescued it, a hand,
is the fist the mark must not draw. The grid survives 48dp because it has no detail to
lose. Re-open this only with a 48dp render in hand.

Corners are cubic Beziers rather than SVG 'A' commands: Android's PathParser accepts both,
but the flag form is the one place its behaviour diverges from browser renderers, and this
file is the only place the curve is authored.

Run from the repo root:  python3 play-store-assets/generate_icons.py
"""

import math

from PIL import Image, ImageDraw

# --- geometry, on the 108x108 adaptive-icon canvas -------------------------------------

CX = CY = 54.0        # centre
N = 3                 # cells a side

# SIDE is the grid's pitch-to-pitch span, and it is set from optical weight rather than
# from the safe circle. A square's corners reach further than a circle's edge for the same
# apparent size, so matching the previous mark's presence (a ring 49 wide) means a square
# of side ~45, not ~49 -- and the corner then lands at 29.34, comfortably inside 33 without
# the mark reading small. Sizing a square mark by its corner distance is what makes it look
# undersized; sizing it by its side is what makes it sit right beside other launcher icons.
SIDE = 45.0
FILL_FRAC = 0.88      # cell size as a fraction of pitch; the remainder is the gap
CORNER_FRAC = 0.22    # corner radius as a fraction of cell

# Seven of nine. The two gaps are the honest part: a full grid says "perfect month", which
# is the claim this app exists not to make, and a grid with gaps says "showed up, mostly".
# They are placed off the diagonal and off centre on purpose -- a centred gap reads as a QR
# fiducial, and gaps in a line read as a letter or a bolt. Verified at 48dp against both.
EMPTY_CELLS = frozenset({(1, 2), (2, 0)})

BRAND_INDIGO = (63, 81, 181)   # #3F51B5
WHITE = (255, 255, 255)

SAFE_RADIUS = 33.0    # adaptive-icon safe circle; nothing may exceed this

# Circle-to-cubic constant: the control-point offset that approximates a quarter turn.
KAPPA = 0.5522847498307936


def cells():
    """Every filled cell as (x0, y0, x1, y1) on the 108 canvas, reading order."""
    pitch = SIDE / N
    cell = pitch * FILL_FRAC
    inset = (pitch - cell) / 2.0
    origin = CX - SIDE / 2.0
    out = []
    for row in range(N):
        for col in range(N):
            if (row, col) in EMPTY_CELLS:
                continue
            x0 = origin + col * pitch + inset
            y0 = origin + row * pitch + inset
            out.append((x0, y0, x0 + cell, y0 + cell))
    return out


def corner_radius():
    return (SIDE / N) * FILL_FRAC * CORNER_FRAC


def painted_half_width():
    """Centre to the outer edge of an edge cell -- the grid's apparent half-size.

    Not SIDE / 2: that is the pitch span, which includes the half-gap sitting outside the
    last cell and so overstates the mark by the width of something nobody paints.
    """
    pitch = SIDE / N
    return SIDE / 2.0 - (pitch - pitch * FILL_FRAC) / 2.0


def rounded_rect_path(rect, r, scale=1.0, cx=CX, cy=CY):
    """One rounded cell as a closed subpath of lines and cubics."""
    def m(x, y):
        return cx + (x - CX) * scale, cy + (y - CY) * scale

    def f(x, y):
        mx, my = m(x, y)
        return f"{mx:.2f},{my:.2f}"

    x0, y0, x1, y1 = rect
    k = r * KAPPA
    p = [f"M{f(x0 + r, y0)}", f"L{f(x1 - r, y0)}",
         f"C{f(x1 - r + k, y0)} {f(x1, y0 + r - k)} {f(x1, y0 + r)}",
         f"L{f(x1, y1 - r)}",
         f"C{f(x1, y1 - r + k)} {f(x1 - r + k, y1)} {f(x1 - r, y1)}",
         f"L{f(x0 + r, y1)}",
         f"C{f(x0 + r - k, y1)} {f(x0, y1 - r + k)} {f(x0, y1 - r)}",
         f"L{f(x0, y0 + r)}",
         f"C{f(x0, y0 + r - k)} {f(x0 + r - k, y0)} {f(x0 + r, y0)}", "Z"]
    return " ".join(p)


def path_data(scale=1.0, cx=CX, cy=CY):
    """VectorDrawable pathData for the whole mark at the given scale."""
    r = corner_radius()
    return " ".join(rounded_rect_path(c, r, scale, cx, cy) for c in cells())


def verify():
    """The furthest painted point must sit inside the safe circle.

    Measured to the outside of the rounded corner rather than to the bare rectangle corner:
    the rounding pulls the extreme point in by (r - r/sqrt(2)) on the diagonal, which at this
    radius is most of a millimetre of canvas and is the difference between honest and
    pessimistic. It is still measured, not assumed -- a wider FILL_FRAC pushes it out again.
    """
    r = corner_radius()
    worst = 0.0
    for x0, y0, x1, y1 in cells():
        for px, py in ((x0 + r, y0 + r), (x1 - r, y0 + r), (x0 + r, y1 - r), (x1 - r, y1 - r)):
            worst = max(worst, math.hypot(px - CX, py - CY) + r)
    assert worst <= SAFE_RADIUS + 0.01, f"mark exceeds safe circle: {worst:.2f} > {SAFE_RADIUS}"
    return worst


# --- raster rendering for the Play listing ---------------------------------------------

def render(size, bg=BRAND_INDIGO, fg=WHITE, supersample=4):
    """Render the mark to a square RGBA image, anti-aliased by supersampling.

    Pillow's rounded_rectangle draws the same shape the cubics above describe, so the raster
    Play icon and the shipped vector cannot disagree; there is no cap or join to reconcile,
    which is the whole reason a filled mark needs none of the polygon stroking its
    predecessor did.
    """
    s = size * supersample
    k = s / 108.0
    img = Image.new("RGBA", (s, s), bg + (255,) if bg else (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    r = corner_radius()
    for x0, y0, x1, y1 in cells():
        d.rounded_rectangle([x0 * k, y0 * k, x1 * k, y1 * k], radius=r * k, fill=fg)
    return img.resize((size, size), Image.LANCZOS)


# Notification icons live on a 24x24 viewport. Scale so the grid spans 20 of 24, leaving the
# margin the status bar expects. The old mark used a radius; this one uses a half-width,
# because the extreme point of a square is a corner nobody is trying to place.
STAT_HALF_WIDTH = 10.0
STAT_SCALE = STAT_HALF_WIDTH / painted_half_width()


def stat_path_data():
    """pathData for the 24dp notification silhouette."""
    return path_data(scale=STAT_SCALE, cx=12.0, cy=12.0)


if __name__ == "__main__":
    worst = verify()
    print(f"safe-circle check: furthest extent {worst:.2f} of {SAFE_RADIUS} OK")
    print(f"grid {N}x{N}, {N * N - len(EMPTY_CELLS)} of {N * N} filled, "
          f"span {SIDE:g} of 108, corner radius {corner_radius():.2f}\n")

    print("--- ic_launcher_foreground.xml (viewport 108) - launcher, monochrome and splash ---")
    print(path_data())
    print()

    print("--- ic_stat_checkin.xml (viewport 24) ---")
    print(stat_path_data())
    print()

    out = "app/src/main/ic_launcher-playstore.png"
    render(512).convert("RGB").save(out)
    print(f"wrote {out} 512x512")
