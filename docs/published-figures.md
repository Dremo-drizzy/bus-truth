# Published figures

Every number this project publishes, with the count behind it, the period it covers and
the caveat it must never be quoted without. **If a figure appears in the README, on the
site, in an ADR or in an interview answer and is not in this table, one of them is
wrong.**

> The senior reviewer referred to a file at `claude/published-figures.md`. It is not in
> the repository or the working tree, so this is a version built from the queries that
> produced each number. If his exists, replace this with it — the point is that there is
> exactly one.

All figures cover **4–10 October 2026** unless stated otherwise, computed with
`ARRIVAL_MOMENT=LAST_BEFORE_ADVANCE`, an on-time window of −60s to +300s, and
`MAX_TRAIL_GAP=120s`, against feed version `2026_08_August_20260806`.

## The headline

| figure | value | count | never quote without |
|---|---|---|---|
| on time | **60.1%** | 65,696 of 109,230 matched | the period, and the day table |
| median delay | +126s | 109,230 | — |
| early (> 1 min) | 9.2% | 109,230 | — |
| late (> 5 min) | 30.7% | 109,230 | — |
| arrivals derived | 113,841 | over 3–10 Oct | 99.2% matched a published time |
| arrivals unmatched | 887 | 0.8% | counted, never stored with a null delay |
| implausible delays refused | 3 | — | ADR-019 |

## Variation — the frame for everything else

| figure | value | kind | note |
|---|---|---|---|
| best–worst **day** spread | **21.3 points** | max−min over 7 days | not like-for-like with a systematic shift |
| best–worst **hour** spread | **34.1 points** | max−min over 22 hours | 79.4% at 06:00 vs 45.3% at 17:00 |
| definitional gap | **17.5 points** | systematic shift | 40.9% vs 60.1%, same data |
| margin of error | **8.7 points** | systematic shift | 60.1% vs 49.7% de-biased |

## By service date

| date | on time | median delay | arrivals |
|---|---|---|---|
| Sun 4 Oct | 54.9% | +181s | 16,142 |
| Mon 5 Oct | 61.8% | +117s | 14,067 |
| Tue 6 Oct | 48.7% | +212s | 18,588 |
| Wed 7 Oct | 59.9% | +126s | 21,832 |
| Thu 8 Oct | 62.3% | +107s | 6,100 |
| Fri 9 Oct | 66.4% | +104s | 14,568 |
| Sat 10 Oct | 70.0% | +74s | 17,933 |

**Caveat that travels with this table:** collection was uneven and not random with
respect to the hour. Thursday 8 October has **no** observed arrivals between 07:00 and
18:59, so its figure is evening service only. The share of each day's arrivals in the
16:00–17:00 peak ranges from 0% to 29.8%. The day-of-week pattern is **not** published
as a finding for that reason.

## By hour of the local day

| hour | on time | median delay | arrivals |
|---|---|---|---|
| 06:00 | 79.4% | +37s | 2,788 |
| 08:00 | 52.8% | +185s | 4,124 |
| 12:00 | 59.0% | +143s | 5,483 |
| 15:00 | 51.1% | +200s | 6,900 |
| 17:00 | 45.3% | +267s | 8,312 |
| 19:00 | 60.8% | +146s | 7,106 |
| 22:00 | 79.1% | +52s | 5,370 |

Computed across all days at once, so unlike the day table it does not depend on which
days were watched.

## By kind of service

| | arrivals | on time | median delay | worst tenth |
|---|---|---|---|---|
| Express routes | 3,776 | 48.0% | +245s | +1,296s |
| All other routes | 105,375 | 60.6% | +124s | +872s |
| Ferries | 79 | 97.5% | +82s | +178s |

## Routes

| figure | value | note |
|---|---|---|
| routes observed | 81 | |
| routes rankable (≥ 23 arrivals) | 78 | 96.3% |
| median arrivals per route | 302 | |
| most punctual rankable | FerD, 97.2% of 72 | a ferry |
| least punctual rankable | 136 Farnham Gate Express, 26.3% of 175 | median +560s |

## Detection and its ceiling

| figure | value | note |
|---|---|---|
| stop updates per trip | **6.8 of 38.3 stops** | 17.8% of the trip visible |
| scheduled stop-events with a derived arrival | **17.5%** | the ceiling is the feed, not the code |
| stops unobserved | 663,346 | **served, not missed** — never presented as missed service |
| trails broken by the gap rule | 1,866 | nothing derived across a collection gap |
| arrival-producing windows holding one position | **50.5%** | why the median moves 74s while the headline moves 17.5 points |

## Calibration of the arrival moment

| figure | value | note |
|---|---|---|
| `current_status` present | 12.42% of 966,775 positions | 87.58% absent, 0.40% `STOPPED_AT` |
| arrivals with any `STOPPED_AT` | **192 of 110,084 (0.17%)** | too few to be a rule |
| window end vs agency `STOPPED_AT` | **61s late** median | 597 windows |
| window start vs the same | 544s early median | nine times worse |
| bias by window width | 0s under 60s → 211s over 900s | the 61s is measured on layovers |
| calibration-window width | 815s median vs 32s for all windows | 25× skew, conservative not optimistic |
| stops ever reporting `STOPPED_AT` | 45 of 1,571 | 27 terminals or bays |

## Schedule matching

| figure | value | note |
|---|---|---|
| arrivals matched to a published time | **99.2%** | 112,951 of 113,841 |
| same stop named for the same stop number | **78.5%** | why the join is on the stop, not the number |
| error the sequence join would have caused | 94s median, 16 min worst | ADR-015 |
| live trip ids needing the `_N` suffix stripped | 4.4% | assumption we cannot verify |
| service-day mislabels repaired | 2,657 of 2,660 | ADR-019 |

## Collection

| figure | value | note |
|---|---|---|
| snapshots archived | 224,011 across three feeds | vehicle positions, trip updates, alerts |
| archive size | ~16 GB | 94% of it trip updates |
| archive growth | ~910 MB/day | ~27 GB/month |
| database | 353 MB | grows ~250 MB/day at full coverage |
| snapshots replayed into the database | 20,980 | 1,134,110 rows written |
| coverage, best day in the week | 78.9% | 3,408 of 4,320 expected snapshots, 4 Oct |
| coverage, worst day in the week | **32.2%** | 1,390 of 4,320, 8 Oct — the day with no daytime arrivals |

## Figures that are *not* published, and why

| | why not |
|---|---|
| day-of-week performance | confounded by uneven coverage by hour |
| bunching, ghost buses | designed, not built |
| prediction accuracy | needs trip updates loaded; 6.1M rows/day, deferred |
| the agency's own `delay` field | present on 34.8% of stop-time updates, 0% overnight |
| any percentage over fewer than 12 arrivals | one bus would move it further than our margin |
| any ranking over fewer than 23 arrivals | a comparison carries two figures' noise |
| a monthly hosting cost | not readable from the vendor's public site; pending |
