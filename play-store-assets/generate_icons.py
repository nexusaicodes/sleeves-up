"""Single source of truth for the CheckIn brand mark.

The mark is one unbroken stroke: an open progress ring whose terminal resolves into a
check. Drawn on a 108x108 canvas so it drops straight into an adaptive-icon layer, with
every point inside the 66dp safe circle (radius 33 from centre).

Emits, from the same geometry:
  * pathData for res/drawable/ic_launcher_foreground.xml -- which is simultaneously the
    launcher foreground, the themed/monochrome layer and the splash icon
  * pathData for res/drawable/ic_stat_checkin.xml (24dp notification silhouette)
  * app/src/main/ic_launcher-playstore.png (512x512 Play listing icon)

Arcs are emitted as cubic Beziers rather than SVG 'A' commands: Android's PathParser
accepts both, but the flag form is the one place its behaviour diverges from browser
renderers, and this file is the only place the curve is authored.

Run from the repo root:  python3 play-store-assets/generate_icons.py
"""

import math

from PIL import Image, ImageDraw

# --- geometry, on the 108x108 adaptive-icon canvas -------------------------------------

CX = CY = 54.0        # centre

# The 66dp safe circle is a limit, not a target: filling it makes the icon read oversized
# beside other launcher icons. SCALE sizes the mark against the 72dp window the launcher
# actually shows, landing the outer stroke edge near 24 of 108 (about two thirds of it).
SCALE = 0.74

R = 28.0 * SCALE          # ring radius
STROKE = 9.0 * SCALE      # stroke width
ARC_START = 78.0          # ring opens here (degrees, 0 = east, counter-clockwise)
ARC_SWEEP = 320.0         # ...and sweeps this far; the remaining 40 is the opening
LONG_ARM = 40.0 * SCALE   # check's long arm, continuing from the arc terminal
SHORT_ARM = 16.0 * SCALE  # check's short arm
LONG_ANGLE = 50.0         # long arm's descent from horizontal

BRAND_INDIGO = (63, 81, 181)   # #3F51B5
WHITE = (255, 255, 255)

SAFE_RADIUS = 33.0    # adaptive-icon safe circle; nothing may exceed this

# Butt caps and mitred joins, matching strokeLineCap/strokeLineJoin on both drawables. The
# mark used to be stroked round throughout, which softened the two things that carry it:
# the ring's opening, which now reads as a deliberate cut rather than a tapered end, and
# the check's vertex, which now comes to a point. MITER_LIMIT is Android's own default --
# past it a join renders bevelled instead, so the two must agree or the raster Play icon
# and the shipped vector disagree at exactly the corner the eye lands on.
MITER_LIMIT = 4.0

# Width, in supersampled pixels, of the boundary redrawn over each stroke polygon. See render().
SEAM_PAD = 2


def _pt(angle_deg, radius=R):
    """Point on the ring. Screen coords, so y is flipped."""
    a = math.radians(angle_deg)
    return CX + radius * math.cos(a), CY - radius * math.sin(a)


def check_points():
    """The three corners of the check, continuing from the arc's terminal."""
    tip = _pt(ARC_START + ARC_SWEEP)
    dx, dy = -math.cos(math.radians(LONG_ANGLE)), math.sin(math.radians(LONG_ANGLE))
    vertex = (tip[0] + LONG_ARM * dx, tip[1] + LONG_ARM * dy)
    heel = (vertex[0] - SHORT_ARM * 0.70710678, vertex[1] - SHORT_ARM * 0.70710678)
    return tip, vertex, heel


def arc_cubics(segments=4):
    """The ring as cubic Beziers: (start_point, [(c1, c2, end), ...])."""
    step = ARC_SWEEP / segments
    k = 4.0 / 3.0 * math.tan(math.radians(step) / 4.0)
    start = _pt(ARC_START)
    out = []
    for i in range(segments):
        t0 = ARC_START + i * step
        t1 = t0 + step
        p0, p1 = _pt(t0), _pt(t1)
        # unit tangent for increasing angle, y flipped
        t0r, t1r = math.radians(t0), math.radians(t1)
        c1 = (p0[0] + k * R * -math.sin(t0r), p0[1] + k * R * -math.cos(t0r))
        c2 = (p1[0] - k * R * -math.sin(t1r), p1[1] - k * R * -math.cos(t1r))
        out.append((c1, c2, p1))
    return start, out


def path_data(scale=1.0, cx=CX, cy=CY):
    """VectorDrawable pathData for the whole mark at the given scale."""
    def m(p):
        return (cx + (p[0] - CX) * scale, cy + (p[1] - CY) * scale)

    def f(p):
        return f"{m(p)[0]:.2f},{m(p)[1]:.2f}"

    start, cubics = arc_cubics()
    parts = [f"M{f(start)}"]
    for c1, c2, end in cubics:
        parts.append(f"C{f(c1)} {f(c2)} {f(end)}")
    tip, vertex, heel = check_points()
    parts.append(f"L{f(vertex)}")
    parts.append(f"L{f(heel)}")
    return " ".join(parts)


def verify():
    """The stroked outline -- not the centreline -- must sit inside the safe circle.

    Measured off [stroke_polygons] rather than by padding each on-curve point by half the
    stroke, because a mitred join reaches past that halo: the check's vertex extends by
    half the stroke over sin(half the angle), which at a sharp corner is most of a stroke
    width more than a round join would have taken.
    """
    worst = max(
        math.hypot(x - CX, y - CY)
        for poly in stroke_polygons(polyline(), STROKE)
        for x, y in poly
    )
    assert worst <= SAFE_RADIUS + 0.01, f"mark exceeds safe circle: {worst:.2f} > {SAFE_RADIUS}"
    return worst


def stroke_polygons(pts, width, miter_limit=MITER_LIMIT):
    """A polyline stroked as filled polygons: butt caps, mitred joins.

    One quad per segment plus one wedge per join, all in the same coordinate space as
    [pts]. Overlap between them is harmless -- they are filled, not outlined -- and the
    wedge is what fills the notch a plain quad leaves on the outside of a turn.

    Pillow has neither cap nor join control, so this stands in for it; sharing the result
    with [verify] is what keeps the Play raster and the shipped vector the same shape.
    """
    r = width / 2.0
    segs = []
    for p0, p1 in zip(pts, pts[1:]):
        dx, dy = p1[0] - p0[0], p1[1] - p0[1]
        length = math.hypot(dx, dy)
        if length == 0:
            continue
        u = (dx / length, dy / length)
        n = (-u[1], u[0])
        segs.append((p0, p1, u, n))

    polys = [
        [
            (p0[0] + n[0] * r, p0[1] + n[1] * r),
            (p1[0] + n[0] * r, p1[1] + n[1] * r),
            (p1[0] - n[0] * r, p1[1] - n[1] * r),
            (p0[0] - n[0] * r, p0[1] - n[1] * r),
        ]
        for p0, p1, _, n in segs
    ]

    for (_, corner, ua, na), (_, _, ub, nb) in zip(segs, segs[1:]):
        turn = ua[0] * ub[1] - ua[1] * ub[0]
        if turn == 0:
            continue
        # The outside of the turn is the side the two offset edges diverge on; the inside
        # needs nothing, since the segment quads already overlap there.
        side = -1.0 if turn > 0 else 1.0
        a = (corner[0] + side * na[0] * r, corner[1] + side * na[1] * r)
        b = (corner[0] + side * nb[0] * r, corner[1] + side * nb[1] * r)
        t = ((b[0] - a[0]) * ub[1] - (b[1] - a[1]) * ub[0]) / turn
        point = (a[0] + ua[0] * t, a[1] + ua[1] * t)
        # Past the limit the join bevels, exactly as the platform's renderer does.
        if math.hypot(point[0] - corner[0], point[1] - corner[1]) > miter_limit * r:
            polys.append([corner, a, b])
        else:
            polys.append([corner, a, point, b])

    return polys


# --- raster rendering for the Play listing ---------------------------------------------

def polyline(steps=48):
    """The whole mark flattened to one list of points, in 108-space.

    Sampled from the same Beziers the vector drawables use, so the raster and the
    vector can't drift apart.
    """
    start, cubics = arc_cubics()
    pts = [start]
    p0 = start
    for c1, c2, end in cubics:
        for i in range(1, steps + 1):
            t = i / steps
            u = 1 - t
            x = (u ** 3 * p0[0] + 3 * u * u * t * c1[0]
                 + 3 * u * t * t * c2[0] + t ** 3 * end[0])
            y = (u ** 3 * p0[1] + 3 * u * u * t * c1[1]
                 + 3 * u * t * t * c2[1] + t ** 3 * end[1])
            pts.append((x, y))
        p0 = end
    _, vertex, heel = check_points()
    pts += [vertex, heel]
    return pts


def render(size, bg=BRAND_INDIGO, fg=WHITE, supersample=4):
    """Render the mark to a square RGBA image, anti-aliased by supersampling."""
    s = size * supersample
    k = s / 108.0
    img = Image.new("RGBA", (s, s), bg + (255,) if bg else (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # Each polygon is filled and then had its own boundary drawn over it. Pillow's polygon
    # fill leaves the odd single pixel of background at a junction where two of them meet
    # at a shallow angle, and the downsample smears each pinhole into a faint mark on an
    # otherwise flat white stroke. SEAM_PAD is in supersampled pixels, so at the final size
    # it is a fraction of one -- it closes the holes without moving the edge.
    pts = [(x * k, y * k) for x, y in polyline()]
    for poly in stroke_polygons(pts, STROKE * k):
        d.polygon(poly, fill=fg)
        d.line(list(poly) + [poly[0]], fill=fg, width=SEAM_PAD)

    return img.resize((size, size), Image.LANCZOS)


# Notification icons live on a 24x24 viewport. Scale so the stroke's outer halo lands at
# radius 10.5 of 12, leaving the margin the status bar expects.
STAT_SCALE = 10.5 / (R + STROKE / 2)


def stat_path_data():
    """pathData for the 24dp notification silhouette."""
    return path_data(scale=STAT_SCALE, cx=12.0, cy=12.0)


if __name__ == "__main__":
    worst = verify()
    print(f"safe-circle check: furthest extent {worst:.2f} of {SAFE_RADIUS} OK\n")

    print("--- ic_launcher_foreground.xml (viewport 108) — launcher, monochrome and splash ---")
    print(path_data())
    print(f"strokeWidth {STROKE:g}\n")

    print("--- ic_stat_checkin.xml (viewport 24) ---")
    print(stat_path_data())
    print(f"strokeWidth {STROKE * STAT_SCALE:.2f}\n")

    print(f"strokeLineCap butt  strokeLineJoin miter  strokeMiterLimit {MITER_LIMIT:g}\n")

    out = "app/src/main/ic_launcher-playstore.png"
    render(512).convert("RGB").save(out)
    print(f"wrote {out} 512x512")
