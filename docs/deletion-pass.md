# Deletion pass — proposal only, nothing applied

Daniel's objection was that the codebase feels like too much, and he is owed an answer
in lines rather than reassurance. Here are the lines, then a list of what could go.

**Nothing in this document has been deleted.** Daniel chooses. The senior reviewer's own
additions are on the list; nothing is exempt because a reviewer asked for it.

## What is actually here

| | lines | files |
|---|---|---|
| **main Java** | **4,455** | 51 |
| — of which code | **2,504** | |
| — of which comment | 1,541 (38%) | |
| — of which blank | 410 | |
| test Java | 3,196 | 25 |
| frontend (`web/src`, `web/tests`) | 2,382 | |
| docs (markdown) | 3,167 | 28 |
| Python scripts | 763 | |
| infra and CI | 493 | |
| **total** | **~14,500** | |

Main Java by module: `common` 332, `collector` 1,253, `processor` 1,481, `api` 1,389.

**The honest headline: the logic is 2,504 lines.** Four Spring Boot services, a
protobuf pipeline, a detector, a schedule matcher and eight endpoints, in about two and
a half thousand lines of code. The reason it *feels* like more is that there are 1,541
lines of comment, 3,196 lines of test and 3,167 lines of decision record around it —
and those exist because this project's requirement is that every line be defensible in
an interview, not because the logic is large.

So the question worth asking is not "is this too much code" but "is any of it carrying
no weight".

## Tier 1 — deletable with nothing lost

### 1. The mapping layer for two of the three feeds — **504 lines**

`TripUpdatePoller.store()` and `ServiceAlertPoller.store()` are both empty:

```java
protected void store(List<TripUpdate> records) {
    // Archived by FeedPoller before this point; nothing reads a database row.
}
```

Archiving happens in `FeedPoller` *before* mapping, so every trip update and every
service alert is decoded from protobuf, mapped into a record, and dropped. At 34,000
stop-time updates per trip-updates snapshot, every twenty seconds, that is the largest
piece of pure waste in the system.

Deletable: `common/TripUpdate`, `common/ServiceAlert`, `TripUpdateMapper`,
`ServiceAlertMapper`, `TripUpdateMapperTest` (7 tests), `ServiceAlertMapperTest` (6
tests) — 504 lines and 13 tests, losing no published figure and no endpoint.

**What it costs:** the records are the design for R9. When prediction accuracy is built,
`TripUpdate` and its mapper come back — and they come back better informed, because the
dedupe key ADR-009 would need is known now and was not when these were written. The
archive is untouched either way; this deletes the mapping, not the data.

**My recommendation: delete the alerts pair, keep the trip-updates pair.** Alerts
changed on 0 of 180 consecutive polls and have no planned consumer; trip updates have
one, named, with a measured row count.

### 2. `ProcessorController` — **51 lines**

`POST /processor/run` and `POST /processor/ontime`. No test covers them, nothing calls
them, and every run in this project's history went through `ProcessorCli`. They are not
among the eight public endpoints.

**What it costs:** the brief asked for them explicitly ("Expose it as
`POST /processor/run?from=&to=`"), so deleting them is a departure from the brief rather
than a tidy-up. Worth asking the reviewer rather than assuming.

### 3. Two methods used only by their own tests — **~12 lines**

`CollectorProperties.Feed.isEnabled()` and `DetectionSettings.defaults()` are each
called only from tests. The `enabled` field itself must stay — Spring binds it and
`@ConditionalOnProperty` reads the property — but the accessor is decoration.
`defaults()` duplicates the canonical constructor's defaults, which is a second place
for the four constants to live.

## Tier 2 — deletable, at a stated cost

### 4. `ArrivalMoment.FIRST_SIGHTING` — **~15 lines**

The losing definition. Keeping both implementations is what makes the published 17.5
points reproducible: delete it and the 40.9% column becomes a number in a document that
nobody can re-derive. **Recommend keeping**, and the reason is the rule — a published
figure must be re-computable.

### 5. `/api/meta` — **~45 lines**

The reviewer's requirement, and it earns its place: it is what stops the display floor
existing in two codebases. Deleting it means the frontend holds its own copy of a
derived number, which is precisely the drift that produced ADR-017's re-derivation.
**Recommend keeping.** Listed because nothing is exempt.

### 6. Unread columns on `VehiclePosition` — **~20 lines plus a migration**

`odometer`, `occupancyStatus`, `occupancyPercentage`, `speed` and `vehicleLabel` are
mapped and stored; nothing downstream reads any of them. They are 5 of 18 fields on the
hot path and five columns on a 1.4-million-row table.

**What it costs:** the raw archive keeps them regardless, so nothing is permanently
lost — but re-deriving them means replaying the archive. Cheap to keep, so
**recommend keeping**; listed for completeness.

### 7. `docs/BUILD-BRIEF.md` and `docs/BUILD-BRIEF-AMENDMENT.md` — **810 lines**

Instructions to me, not documentation of the system. They are the largest single
documentation artefact and a stranger reading the repo does not need them. **Recommend
keeping until the project is finished**, then archiving: they are the best evidence of
how the work was actually directed, which is itself interesting in an interview.

## Tier 3 — things I would not delete, and why

- **The 1,541 lines of comment.** They are the project's purpose. A reader who deletes
  the comment above `.map { width: 100%; height: 100% }` will delete the rule next.
- **The 3,196 lines of test.** Every one of the four bugs this project caught late —
  the stale config, the `@types/node` divergence, the service-day mislabel, the fixture
  shape — was caught by a test or a check, not by reading.
- **`docs/decisions/` (17 records).** The reversed decisions are the most valuable
  documents in the repo. ADR-002 and ADR-012 together are better evidence of judgement
  than either alone.
- **`docs/published-figures.md`.** It exists because three figures went stale by being
  copied forward.

## If Daniel wants the single biggest cut

Delete the alerts feed end to end — poller, mapper, record, tests, and the feed's entry
in the collector config: **about 260 lines and 6 tests, for a feed that changed on 0 of
180 polls and has no consumer.** The archive already holds 74,574 alert snapshots, so
the history survives even if collection stops.

That is the only cut on this page I would make without asking anybody.
