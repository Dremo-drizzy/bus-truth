# Stage 4 — Live map

## What was built

One API endpoint and one page. `GET /api/vehicles/live` runs the latest-position-per-vehicle
query and returns a GeoJSON `FeatureCollection`; the Next.js page draws it with MapLibre and
refreshes every 15 seconds. Verified against live data: 106 vehicles, newest position 8
seconds old, longitude range −63.699 to −63.467 and latitude 44.571 to 44.768, which is
Halifax.

GeoJSON rather than a bespoke JSON shape because MapLibre consumes it natively as a source.
The browser hands the response straight to the map, so there is no translation layer that
could disagree with the server. The cost is that coordinates are `[longitude, latitude]` —
the reverse of how they are spoken — and getting that backwards renders happily with the
buses in Somalia, so there is a test for it.

The query is wrapped unchanged, including its ten-minute predicate. Without that predicate
`distinct on (vehicle_id)` scans every vehicle's entire history to find rows it then throws
away, and the cost grows with the archive rather than with the number of buses.

## Freshness is part of the response, not inferred from it

`generated_at`, `newest_position_at` and `vehicle_count` sit beside `features`, and
`newest_position_at` is deliberately read from the **whole table** rather than the
ten-minute window. That single decision is what lets the page distinguish three states that
otherwise look identical, because two of them return zero features:

1. **buses on the map** — features present
2. **no buses currently running** — no features, but `newest_position_at` is recent. At
   03:00 this is the truthful answer, not a fault
3. **no recent data** — `newest_position_at` older than five minutes, so the pipeline is the
   suspect rather than the buses

Each state has its own test, and the page renders a distinct banner for each.

## Per-dot ageing

Dot opacity is driven by each feature's own `vehicle_timestamp`, fading from fully opaque to
0.2 across the ten-minute window, rather than by a single page-level freshness indicator.
This came from a live observation: five of 119 vehicles were roughly ten minutes stale while
the rest were seconds old — ended trips or lost signal. A bus that stopped reporting nine
minutes ago must not look identical to one reporting now. The age is computed in JavaScript
on each refresh because MapLibre expressions cannot parse timestamps.

Refresh is 15 seconds, not 10: the feed itself only changes every 20 seconds, so a faster
poll spends half its requests fetching data it already has.

## No CORS

The browser only ever talks to the page's own origin. Next.js rewrites `/api/*` to the
Spring service, which means no CORS configuration on the API, and it matches how this would
deploy behind a reverse proxy rather than working only because a development rule is open.

## Four version traps, all found by building rather than reading

- **springdoc 2.x does not run on Spring Boot 4.** It fails at startup creating
  `swaggerWebMvcConfigurer`. The 3.x line is the one built for Boot 4.
- **`real` is float4, and the Postgres driver refuses to hand it to a `Double`** —
  "conversion to class java.lang.Double from float4 not supported". Coordinates are `Float`
  end to end, which also keeps 44.6492 from becoming 44.64920043945312 in the JSON.
- **maplibre-gl 6 removed the default export**, so `import maplibregl from "maplibre-gl"`
  compiles and fails at build. Its worker then failed to load under Next at runtime, with a
  `text/html` MIME type and no tiles ever requested, so the version was pinned back to 5.
- **maplibre-gl 5 is the mirror image**: the named exports exist in the type declarations
  but are `undefined` at runtime, so importing them type-checks, builds, and throws "not a
  constructor" in the browser. Version 5 uses the default export only.

The pattern across all four is the same one Stages 2 and 3 kept producing: it compiled, or
it started, and neither fact meant it worked.

## CI

A second job builds the web app with `npm ci && npm run build`, blocking rather than
advisory, because `next build` type-checks the whole app and a green check that means
nothing is worse than no check. `npm ci` installs exactly the committed lockfile and fails
if `package.json` and the lockfile disagree.

## Scope frozen here

Arrival detection, the trip-updates loader, the static GTFS loader and the dbt marts are not
built. The reasoning is in the README's "what I would do next": arrival detection is where
the judgement lives, and a rushed detector produces numbers that look authoritative and are
wrong.
