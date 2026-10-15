# Amendment to docs/BUILD-BRIEF.md

**Issued 15 October 2026. This file overrides the brief wherever they disagree.
Read both; this one wins.**

Daniel's decision: build it all now, without comprehension gates. He will work
through the codebase with the curriculum after it is finished and working. The
teaching is deferred, not cancelled.

---

## 1 · All stop points and all questions are removed

The brief's "Summary of stop-and-explain points" table is **void**. So is every
instance of **STOP** in the brief.

**`CLAUDE.md` rule 9 — "after finishing a task, ask Daniel two short questions and
wait for his answers" — is SUSPENDED** for the whole of Phase R. Do not ask Daniel
comprehension questions. Do not wait for approval between stages.

Run **R0 → R8 continuously.** Report what you did at the end of each stage in a
few lines, then start the next one.

## 2 · The one kind of stop that remains

Stop only when a **measured number means the next stage would be built on
something broken.** That is engineering judgement about the code, not a check on
Daniel. Specifically:

| Stage | Stop if |
|---|---|
| R3 | fewer than 90% of live trip ids join the schedule after stripping the `_N` suffix |
| R4 | fewer than 50% of scheduled stop-events on a sampled day produce a derived arrival |
| R5 | fewer than 80% of arrivals match a scheduled time |
| any | `./mvnw verify` goes red and cannot be fixed within the stage |

If one of those trips, say so plainly with the number, say what you think the cause
is, and wait. Otherwise keep building.

## 3 · Human-first zones are suspended

The brief reserved the arrival rule, the on-time window, the four constants, the
About page text and the README findings for Daniel. **Write all of them yourself
for now**, using the brief's suggested values, and record each in its ADR marked:

> **NEEDS DANIEL'S REVIEW** — written by Claude Code to unblock the build; the
> reasoning here is not yet the author's own.

That marker matters. It tells him exactly what to revisit, and it stops the repo
claiming reasoning nobody has defended.

## 4 · Leave `docs/defence-notes.md` blank

Do **not** fill in any answers in that file, now or later in Phase R. Blank
questions are correct; answers in your words are worse than blank, because the
file then looks finished. Daniel answers them during the curriculum phase.

If the current version has answers you wrote, revert those to blank.

## 5 · New Stage R0 — a fresh repository

Runs **first**, before R2.

1. Confirm Daniel has backed up `data/` somewhere off this laptop. Ask once; do
   not proceed until he confirms. This is the only blocking question in Phase R
   and it is about data loss, not learning.
2. Daniel creates the new empty GitHub repo himself and gives you the URL. He is
   making the old one **private, not deleted.**
3. Merge `r1-remove-kafka` into `main` locally, then merge `r2-load-gtfs` on top.
4. Create the new history:
   - a fresh `git init` in a clean directory, or `git checkout --orphan main`
   - copy the working tree as it stands after R1 and R2
   - **verify `.gitignore` still excludes `data/`, `.env`, `target/`,
     `node_modules/` before the first commit** — a fresh history is a fresh chance
     to commit something that should not be committed
   - one commit, message: `Bus Truth — transit reliability pipeline`
   - push to the new remote
5. Re-add the GitHub Actions workflow and confirm it goes green on the new repo.
6. Confirm no secret, password, connection string, personal address or `.env` file
   is present anywhere in the new tree. Report explicitly that you checked.

The `v1-streaming` tag does **not** carry over. The old repo going private is what
preserves it.

## 6 · Then R2 through R8, straight through

Stage order is unchanged: R2 (schedule loader, already built — port it over), R3
(trip-id join check), R4 (arrival detector), R5 (scheduled vs actual), R6 (API),
R7 (Vite + React, five pages), R8 (cloud and ship).

**R9 (prediction accuracy) stays out of scope** unless everything else is finished
and working.

## 7 · What "done" means now

- `./mvnw verify` green
- CI green on the new repo
- the collector running, archiving and inserting
- the schedule loaded
- `arrival_events` and `ontime_events` populated for at least one week of archived
  data
- all six API endpoints responding
- all five React pages rendering real numbers
- hosted, with PostgreSQL not reachable from the internet
- README rewritten, every ADR present, every Claude-written ADR carrying the
  **NEEDS DANIEL'S REVIEW** marker
- `docs/defence-notes.md` present and blank

## 8 · Still non-negotiable

These are not comprehension gates. They are correctness and safety.

- **Never commit a secret.** `${POSTGRES_PASSWORD}` keeps no default, anywhere.
- **Never write into `data/`** except by appending new snapshots. Never delete from
  it, never reorganise it.
- **`MAX_TRAIL_GAP` is mandatory.** Coverage was measured at 73.7%, worst day
  42.4%. Deriving arrivals across a collection gap would publish invented
  cancellations. Without that rule the project's headline numbers are wrong.
- **Every percentage the API returns must carry the count it was computed from**,
  and the frontend must say "too few observations" rather than print a percentage
  from a handful of rows.
- **No authorship trailers, no "Generated with" lines, no session links** in
  commits, PRs or file contents.
- Small commits with real messages. A fresh history with one commit is fine for
  the import; everything after it should be readable.

## 9 · Deferred, not cancelled

Once the project is finished and working, Daniel works through the whole codebase
with the curriculum, answers all 23 defence notes in his own words, and rewrites
every ADR marked **NEEDS DANIEL'S REVIEW**.

Leave the markers in. They are the to-do list for that phase.
