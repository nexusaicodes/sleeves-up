"""Seed a representative Sleeves Up history into a pulled `_app` database.

Demo data for Play Store screenshots. Shapes it to show what the app actually does:
a handful of missed days so the split is not a flat 100%, sessions starting across
the morning, afternoon and evening so the start-time split has all three slices,
days of one, two and three blocks so the sessions-per-day split has all three, and
an open session today so the gauge is running.

Day *lengths* are still varied, but nothing renders them any more — the calendar is
one mark per day shown up, whether it held 45 minutes or nine hours. They vary here
only so the hours charts are not a flat line.

Usage: python3 seed.py <path-to-_app>
"""
import random
import sqlite3
import sys
from datetime import date, datetime, timedelta, timezone

TZ = timezone(timedelta(hours=4))          # the device's zone; date_key must agree with it
TODAY = date(2026, 8, 28)
# The first of the *previous* month, rather than a fixed number of days back: the summary
# screenshot's caption states a figure for a whole calendar month, so a window opening on the
# 2nd would assert it over one day fewer than the month has. This tracks TODAY on its own; the
# seed below does not, and has to be re-searched whenever TODAY moves.
START = (TODAY.replace(day=1) - timedelta(days=1)).replace(day=1)
MISS_CHANCE = {5: 0.55, 6: 0.65}           # Sat/Sun are the days most often skipped
WEEKDAY_MISS = 0.10

# Reproducible: same screenshots on a re-run. The value is *searched*, not arbitrary — it is
# the seed for which July lands on exactly 24 days shown up of 31, which is what the summary
# screenshot's caption states. Change TODAY and this number stops meaning anything; re-search
# it rather than leaving a caption asserting a figure the data no longer produces. The search
# is a loop over candidate seeds counting July's days; it survived the 27th -> 28th move, which
# is luck rather than a property — do not assume the next date shift is free.
random.seed(20260006)


def millis(d: date, hour: int, minute: int) -> int:
    return int(datetime(d.year, d.month, d.day, hour, minute, tzinfo=TZ).timestamp() * 1000)


def sessions_for(d: date):
    """Zero to three (start_min, end_min) pairs, as minutes past midnight."""
    if random.random() < MISS_CHANCE.get(d.weekday(), WEEKDAY_MISS):
        return []

    # A morning block almost always; an afternoon block usually; an evening block sometimes.
    blocks = []
    start = random.randint(8 * 60 + 30, 10 * 60 + 15)
    blocks.append((start, start + random.randint(75, 190)))

    if random.random() < 0.80:
        gap = random.randint(35, 80)
        start = blocks[-1][1] + gap
        blocks.append((start, start + random.randint(90, 210)))

    if random.random() < 0.30:
        gap = random.randint(90, 180)
        start = blocks[-1][1] + gap
        blocks.append((start, start + random.randint(45, 120)))

    return blocks


def main(db_path: str) -> None:
    rows = []
    d = START
    while d < TODAY:
        for start_min, end_min in sessions_for(d):
            started = millis(d, 0, 0) + start_min * 60_000
            stopped = millis(d, 0, 0) + end_min * 60_000
            rows.append((started, stopped, stopped - started, d.isoformat()))
        d += timedelta(days=1)

    # A few evenings, so the start-time split has a real third slice. The generator above starts
    # its blocks in the morning and early afternoon, so without these the evening slice is a
    # sliver and the chart reads as if the app could not see one.
    # Every offset is a multiple of seven away from one of two weekdays, so none of them lands on a
    # weekend: each one replaces that day's rows and therefore *forces* a session there, and forcing
    # one onto every Sunday would contradict MISS_CHANCE and the missed days the calendar is here to
    # show. They are spread across both months, and the seed search above counts them as days shown
    # up rather than assuming they are free.
    for offset in (2, 9, 16, 23, 35, 42, 49, 56):
        d = TODAY - timedelta(days=offset)
        rows = [r for r in rows if r[3] != d.isoformat()]
        for s, e in ((9 * 60 + 20, 12 * 60 + 40), (19 * 60 + 30, 22 * 60 + 15)):
            started, stopped = millis(d, 0, 0) + s * 60_000, millis(d, 0, 0) + e * 60_000
            rows.append((started, stopped, stopped - started, d.isoformat()))

    # Today: two closed sessions, then one still running — the gauge counts up from it and
    # the primary action reads Check Out.
    key = TODAY.isoformat()
    for s, e in ((9 * 60 + 5, 11 * 60 + 20), (12 * 60 + 15, 14 * 60 + 5)):
        started, stopped = millis(TODAY, 0, 0) + s * 60_000, millis(TODAY, 0, 0) + e * 60_000
        rows.append((started, stopped, stopped - started, key))
    rows.append((millis(TODAY, 15, 12), None, None, key))

    conn = sqlite3.connect(db_path)
    conn.execute("DELETE FROM sessions")
    conn.executemany(
        # `closed_by` is omitted, so it stores NULL: nothing on screen reads it and only the CSV export does, so the
        # screenshots are unaffected either way.
        "INSERT INTO sessions (started_at, stopped_at, duration, date_key) VALUES (?, ?, ?, ?)",
        rows,
    )
    conn.commit()
    conn.execute("PRAGMA wal_checkpoint(TRUNCATE)")
    days = len({r[3] for r in rows})
    closed = [r for r in rows if r[2] is not None]
    print(f"{len(rows)} sessions across {days} days "
          f"({START} .. {TODAY}), longest day "
          f"{max(sum(x[2] for x in closed if x[3] == k) for k in {r[3] for r in closed}) / 3_600_000:.1f}h")
    conn.close()


if __name__ == "__main__":
    main(sys.argv[1])
