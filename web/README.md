# web — Vite + React + React Router, MapLibre GL and Recharts

Five pages: the live map, the route leaderboard, one route in detail, one stop, and
how the numbers are made.

```bash
npm ci
npm run dev        # http://localhost:5173, /api proxied to localhost:8084
npm run build      # tsc --noEmit, then vite build
npm run preview    # serves dist/, /api proxied the same way
```

The api must be running on 8084 for anything but the method page to have data.

## Three things here are the way they are for a measured reason

**MapLibre is imported as the default export only.** maplibre-gl 5's named exports
exist in its type declarations and are `undefined` at runtime, so importing them
compiles and then throws "not a constructor" in the browser.

**`.map` has an explicit `width` and `height`.** maplibre-gl.css sets
`.maplibregl-map { position: relative }`, MapLibre adds that class to the container at
runtime, and that stylesheet is imported after ours at equal specificity — so it wins
and `inset: 0` alone collapses the map to zero height.

**The map and the charts load on demand.** They are most of the bundle and each is
needed on one page. Splitting them took the first load from 1,731 kB to 271 kB.

## The rule the pages exist to follow

A percentage is never shown without the count it came from, and below five
observations no percentage is shown at all — the page says "too few observations"
instead. Arrival detection observes about 17.5% of scheduled stop-events, so thin
denominators are the normal case here, and "0% on time" over two arrivals would be a
false claim about a bus route rather than a measurement of one.
