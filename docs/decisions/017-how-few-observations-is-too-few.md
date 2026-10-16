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

> **The derivation in this section was re-done.** It is kept because the method is the
> same and the reasoning is worth following, but the input it uses — 21 points — is no
> longer the uncertainty this project publishes. Skip to *Re-derived, because the input
> changed* for the figures in force.

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

## Re-derived, because the input changed

The floors above were derived from a 21-point uncertainty. That was the right method
and the wrong input, as the senior reviewer pointed out: **21 points was the gap between
two candidate arrival definitions while the choice was still open.** The choice is now
settled by measurement against the agency's own `STOPPED_AT` positions (ADR-014), so
what the project still admits to is not the gap between the candidates but the residual
bias of the one that won — a narrower thing. Floors justified by 21 points were resting
on a figure the README no longer claims.

### The new input, measured

The end-of-window definition runs late by a median of 61 seconds where that can be
checked. Shifting every arrival in the week 4–10 October earlier by exactly that much:

| | on time |
|---|---|
| as published | **58.4%** |
| with the 61-second bias removed | 49.7% |
| **published uncertainty** | **8.7 points** |

8.7 points, not 21. For reference, at the opposite extreme — the 211-second median bias
measured on the widest windows — the figure falls to 26.3%, but that is the worst
bucket rather than the pooled estimate, and the pooled estimate is what gets published.

### The floors that follow

| claim | figures it rests on | its noise must stay under | floor |
|---|---|---|---|
| showing a percentage at all | 1 | 8.7 points | **12** |
| holding a position in a ranking | 2 | 8.7 points | **23** |

Both are now **computed in code** from a single constant,
`PublishedUncertainty.HEADLINE_SENSITIVITY_POINTS`, with the floors derived by
`smallestSampleWhereOneObservationMovesLessThan`. The API serves the display floor from
`/api/meta` and the frontend reads it from there rather than keeping a second copy. That
is the reviewer's instruction, and the reason for it is this very section: two numbers
in two places, justified by a third in a document, drifted apart within a day.

### The factor separating them is not a choice either

An earlier version of this record set the ranking floor at "a quarter of the margin",
and the senior reviewer was right to reject it: a quarter is a free parameter hidden
inside something that looks like arithmetic — the same shape as the invented 50% gate
this project already had to withdraw, only better disguised.

There is no need for it. **Showing a percentage rests on one figure; ranking rests on
two.** "This route is worse than that one" is a statement about a pair, and each side
carries its own sampling noise, so the noise behind the comparison is twice the noise
behind either number:

| | requirement | floor |
|---|---|---|
| showing | `100/n < 8.7` | 12 |
| ranking | `2 × 100/n < 8.7` | 23 |

Two is not a preference; it is how many percentages a comparison involves. The code now
takes the number of figures as its only other input, so nothing in the derivation is
chosen.

One alternative was measured and rejected. A stricter standard would be that sampling
noise must not reorder *adjacent* routes in the table, which sounds appealing until it
is measured: the median gap between neighbouring routes on the published week is **0.54
points**, so that standard demands a floor of about 186 arrivals. It over-constrains a
claim the site never makes — nobody asserts that route 37 is worse than route 38 — while
excluding most of the network. The leaderboard's real claims are at the ends of the
table, and those are comparisons between two figures, which is what the floor above
covers.

### What the stricter floors cost — almost nothing, now

The first derivation worried about losing routes, because it was measured against a
single service day where the median route had 31 arrivals. The published range is a
week, where the median route has **302**:

| floor | routes clearing it | share of the 81 observed |
|---|---|---|
| 5 | 81 | 100% |
| **12** (showing) | **79** | **97.5%** |
| **23** (ranking) | **78** | **96.3%** |
| 46 | 75 | 92.6% |

So the ranking floor roughly quintupled from the original five and the leaderboard lost
three routes. The worry was real on one day's data and is not on a week's — the same
point the original "revisit if" made about absolute counts against ranges, now with
numbers attached. The median route has 302 arrivals in the published week.

Note the direction, because it reads backwards at first: **a narrower published
uncertainty makes the floors stricter, not looser.** Sampling noise that once sat
comfortably inside a 21-point methodological uncertainty now stands out against an
8.7-point one. Improving the method shortens the leaderboard, and that is correct.

Revisit if: the margin is re-measured, which moves both floors on its own — that is the
point of computing them. Or if the leaderboard's claims change shape: a site that
started asserting a full ordering rather than ends of a table would need the adjacent-gap
standard and its floor of about 186, and would have to say so.
