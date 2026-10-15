# 001 — Maven multi-module monorepo

Context: Bus Truth is five Java pieces — common, collector, loader, processor and api.
They are separate applications that run separately, but they are similar pieces of code
working on the same data types: the records a collector produces are the ones the loader
writes and the processor reads. Something had to decide how they are built, versioned and
kept in step.

Options considered:
A. One repository, one module — a single application that does everything.
B. A Maven multi-module monorepo: one parent POM, five modules.
C. A separate repository per service, sharing code through a published `common` artifact.

Decision: B. One repository with a parent POM (`packaging: pom`) that lists common,
collector, loader, processor and api as modules. Everything is built together even though
each application runs on its own.

Why:
- The shared domain records live in `common` and the other modules depend on it directly.
  With separate repositories (C) every change to a record would mean releasing a new
  `common` version and updating four dependents before anything could be tested together.
- One build command, `./mvnw verify`, compiles and tests all five. CI is one workflow.
- The parent POM fixes the Java version and inherits Spring Boot's dependency management,
  so all five modules use the same tested library versions. Version drift between services
  is a whole class of bug that simply cannot happen here.
- A change that spans modules — a new field on a record plus the code that reads it — is
  one commit and one pull request, and is therefore reviewed as one thing.
- One application (A) was never right: the collector must keep polling every 20 seconds
  while the processor re-runs whole date ranges. They have different jobs and lifecycles.

What I gave up:
- Independent versioning. Everything moves together as `0.1.0-SNAPSHOT`; I cannot ship
  collector 2.0 while api stays on 1.4.
- Independent release cadence. A release releases all of it.
- Build time. Every module is rebuilt and retested even when only one changed.
- Easy separation of ownership: handing one service to a different team would mean
  extracting it from this repository first.

Revisit if: one service needs its own release cycle or belongs to someone else; the full
build gets slow enough to be annoying in CI; or two services start needing genuinely
different versions of the same library.
