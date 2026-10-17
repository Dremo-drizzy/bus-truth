# Defence notes

Answer these in your own words. Not Claude Code's, not the reviewer's — yours.
Short is fine. A sentence you can say out loud beats a paragraph you can only read.

The rule that produced this file: in the Stage 0 exam, the only two questions passed
were the two ADRs written personally. Everything written by someone else did not stick.

**The answers stay blank until the curriculum phase.** A file of answers in someone
else's words looks finished and is worse than an empty one.

---

## 1. The project in thirty seconds

What does Bus Truth do, and why does the feed need recording rather than just reading?

> 

What does it NOT do? (Say this out loud before anyone asks. Owning a scope boundary
reads as judgement; being caught at one reads as a gap.)

> 

---

## 2. The architecture

There is no queue between the collector and the database. There was one, and it was
removed. What did it buy, what did it cost, and what is now the thing that makes a
re-run safe?

> 

If the answer is "the insert is idempotent on its natural key", say which key and what
happens on the second attempt.

> 

Why are the collector, the processor and the api separate modules instead of one
application?

> 

The collector owns the Flyway migrations and the api never migrates. Why put the schema
with the writer?

> 

---

## 3. The archive

Why is every raw snapshot kept forever, compressed, when the database holds the same
data in queryable form?

> 

The archive held 152,036 snapshots and the database 3,374. What did that mean for every
number the project had published up to that point, and what fixed it?

> 

Why does the backfill replay through the same mapper and writer as the live path rather
than its own faster loader?

> 

---

## 4. The collector

`FeedPoller` is a template method: `pollOnce()` is final and subclasses implement the
parts that differ. What are the fixed steps, and why is that order not negotiable?

> 

A snapshot whose contents have not changed is skipped. How is "not changed" decided,
and what would break if it were decided on the feed's own timestamp instead?

> 

Two polls in the same second once overwrote each other in the archive, and the first
fix did not work on Windows. What was the fix that did?

> 

---

## 5. Arrival detection — the heart of it

The feed never says "arrived". What does it say, and how is an arrival inferred from
that?

> 

Why is the stop sequence the signal rather than `currentStatus`, which can literally
say `STOPPED_AT`?

> 

What is `MAX_TRAIL_GAP` and what would this project publish without it? (This one
matters more than any other answer in the file.)

> 

Only 17.5% of scheduled stop-events produce a derived arrival. Explain why that is a
ceiling imposed by the data source, and what number proves it.

> 

What happens to the stops between a sequence jumping from 4 to 19 — and why is
`stopsUnobserved` named that rather than `stopsSkipped`?

> 

`ARRIVAL_MOMENT` changes the headline on-time figure by 21 percentage points. Which
setting is the site using, why, and what do you say to someone who claims the figure is
therefore meaningless?

> 

---

## 6. Matching an arrival to the schedule

An arrival is joined to its scheduled time on `(trip_id, stop_id)`, not on the stop
sequence, even though both sources carry a sequence number. Why, and how big was the
error the obvious join would have introduced?

> 

A published time of `25:10:00` is legal GTFS. What instant does it mean, and why is the
service day measured from noon minus twelve hours rather than from midnight?

> 

Which publication of the schedule is an arrival compared against, and what goes wrong
if it is the newest one on disk?

> 

An arrival that cannot be matched is counted but never stored. Why not store it with a
null delay?

> 

4.4% of live trip ids carry an `_N` suffix. What does the project do with them, and what
is the assumption it cannot verify?

> 

---

## 7. The API and the pages

Every percentage the API returns carries the count it was computed from, and that is
enforced by a type rather than a convention. What is the type, and what does its
`percent` field do when nothing was observed?

> 

Ranked by on-time share alone, the three best routes on 9 October had eleven, four and
two arrivals. What does the API do about that now, and why not simply drop thin routes?

> 

What is `/api/coverage` for, and why is it not decoration?

> 

When does the API return 404, and why does a known route with no observations return a
body of zeros instead?

> 

The frontend imports MapLibre's default export only, and `.map` carries an explicit
width and height. What happens if either is "tidied up"?

> 

Why do the map and the charts load on demand rather than in the main bundle?

> 

---

## 8. The things that went wrong

Pick three and be able to tell each as a short story — what you saw, what you thought
it was, what it actually was, how you found out.

Candidates, all real:

- CI failed with 159 rows before a replay and 233 after, and passed locally every time.
- A dependency was present and the feature it provides never ran — twice, for two
  different libraries. The application was healthy both times.
- `psql` said the database was fine while the application said it was empty.
- The 17.5% figure was attributed to polling frequency. That explanation was wrong, and
  arithmetic disproved it before any code changed.
- A proposed rate rule was tested against real data and rejected 29 of 38 real
  transitions.
- `spring-boot:run` silently dropped its arguments, so a backfill started, found no
  dates and did nothing, successfully.

> 

> 

> 

What is the general lesson about "the application is healthy" as evidence?

> 

---

## 9. Judgement calls you would have to defend

Each of these is a decision someone could reasonably have made differently. For each:
what you chose, what you gave up, and when you would revisit it.

A one-minute-early to five-minutes-late on-time window.

> 

Keeping the raw archive forever rather than only the derived tables.

> 

Removing a technology from the CV (Kafka) and keeping its decision records struck
through rather than deleting them.

> 

Writing stop-level reliability at all, given that stop-level coverage is thin.

> 

---

## 10. Scope, and stopping

What is built, what is designed and not built, and why did prediction accuracy stop at
a measured row count rather than at an opinion?

> 

PostgreSQL publishes no port in the production compose file. Why is that expressed as
an absent key rather than a configuration value?

> 

Several decision records are marked **NEEDS DANIEL'S REVIEW**. What does that marker
mean, and why is it better than quietly presenting the reasoning as your own?

> 
