# 017 — How few observations is too few

> **NEEDS DANIEL'S REVIEW** — written by Claude Code at the senior reviewer's
> instruction. The numbers are measured; the words are not yet the author's own.

Context: the API shipped with a floor of five arrivals before a route could take a
position in the leaderboard. The reviewer rejected the number — correctly — on the
grounds that at five arrivals a single bus moves the percentage twenty points, and the
site would still be willing to call a route the worst in Halifax. The previous round of
this project had already produced one invented threshold (a 50% gate that was admitted
to be a guess), so this one needed measuring.

Two different questions were hiding behind one number, and they get different answers.

1. **May this percentage be shown at all?** A weak claim: here is a figure, here is the
   count behind it.
2. **May this route or stop take a position in a ranking?** A much stronger claim: this
   route is better or worse than that one, and this stop is the route's worst.

## The test a floor has to pass

A single observation moves a percentage by `100/n` points. This project already
discloses that its headline figure moves **21 percentage points** depending on which
end of a stop's reporting window is taken as the arrival (ADR-014). That disclosed
uncertainty is the natural yardstick:

> **A sample is too thin when one observation can swing its percentage further than the
> uncertainty the project already admits to.**

Below five arrivals, one bus swings the figure more than 21 points — further than the
entire methodological uncertainty. That sets the floor for *showing* a percentage.

For *ranking*, the bar has to be stricter, because a ranking is a comparison between
routes and the noise has to be small relative to the differences being claimed.

## The measurement

Arrivals per route over service date 9 October 2026 — 6,550 arrivals across 67 observed
routes, median 31 arrivals per route, quartiles 8 and 150:

| floor | routes clearing it | share of the 67 | one bus moves the figure |
|---|---|---|---|
| 1 | 67 | 100% | 100 points |
| 5 | 54 | 80.6% | 20.0 points |
| 10 | 46 | 68.7% | 10.0 points |
| **20** | **37** | **55.2%** | **5.0 points** |
| 30 | 34 | 50.7% | 3.3 points |
| 50 | 23 | 34.3% | 2.0 points |
| 100 | 19 | 28.4% | 1.0 point |

Decision:

- **Display floor: 5.** Below it the page prints "too few observations" instead of a
  percentage. This is the point at which one bus stops swinging the figure further than
  the 21 points already disclosed.
- **Ranking floor: 20.** Below it a route is returned with `rankable: false` and sorts
  last, and a stop is left out of the worst-stops list with the exclusion counted.

Why 20 rather than 30 or 50: it is the lowest floor at which one bus moves the figure
by **5 points or less**, which is a quarter of the disclosed uncertainty, while still
leaving **37 of 67 routes** — more than half the observed network — in the ranking. At
50 the leaderboard loses two thirds of Halifax's routes to buy a further 3 points of
stability, which is a worse trade: a leaderboard missing most of the city is not more
honest, it is just quieter. The curve's knee is at 20, and everything below it degrades
fast — 10 points at a floor of ten, 20 at a floor of five.

What I gave up:

- **Thirty of the 67 observed routes cannot be ranked** on a single day's data. They are
  still returned, still carry their counts, and still appear on the site in their own
  table below the ranking, because a route observed twice is a fact about this
  project's coverage rather than something to hide. A longer date range moves routes
  above the floor, which is the honest way to get a fuller leaderboard.
- **A single number for both questions**, which would have been simpler to explain. The
  display floor and the ranking floor differ by a factor of four and each has to be
  justified separately.

Revisit if: the arrival-moment uncertainty is ever narrowed — if a future feed exposes
`STOPPED_AT` widely enough to time arrivals directly (today it is on 0.40% of
positions, ADR-014), the 21-point yardstick shrinks and both floors should rise with
it. The floors are tied to that number, not chosen beside it.

Also revisit if: the default range stops being a single service day. Both floors are
absolute counts, so a week of data clears them far more easily than a day does. The
honest version of that observation is that a floor should perhaps be expressed per
service day rather than per range, and this record does not do that.
