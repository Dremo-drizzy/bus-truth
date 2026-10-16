// maplibre-gl 5 exposes its API on the default export only: the named exports exist
// in the type declarations but are undefined at runtime, so importing them compiles
// and then throws "not a constructor" in the browser. Types are imported separately
// because `import type` is erased.
import maplibregl from "maplibre-gl";
import type { GeoJSONSource } from "maplibre-gl";
import { useEffect, useRef, useState } from "react";
import "maplibre-gl/dist/maplibre-gl.css";

/**
 * Whether this build watches a live feed or shows one named minute.
 *
 * `snapshot` is for a deployment with no collector: the hosted database holds only the
 * derived tables, so the live endpoint is permanently empty there and a map showing "no
 * recent data" for ever reads as broken rather than honest. In that mode the page draws
 * a curated minute and labels it with the date and time, the way every other figure on
 * this site carries its period.
 *
 * `live` is the default, so local development against a running collector behaves
 * exactly as before — including all three of the states below, which are correct there
 * and simply unreachable on the hosted site.
 */
/**
 * Where the API lives, same rule as api.ts: empty means same-origin, a bare hostname
 * gets https:// and a trailing slash is trimmed. Repeated here rather than imported
 * because this page predates the helper and fetches directly — and a map pointing at
 * the wrong origin is the one failure that looks like "no buses are running".
 */
const API_BASE = ((raw: string | undefined): string => {
  const value = (raw ?? "").trim();
  if (value === "") return "";
  const withScheme = value.startsWith("http") ? value : `https://${value}`;
  return withScheme.replace(/\/+$/, "");
})(import.meta.env.VITE_API_BASE_URL);

const MAP_MODE: "live" | "snapshot" =
  import.meta.env.VITE_MAP_MODE === "snapshot" ? "snapshot" : "live";

/** How often the page asks for positions. A fixed minute never changes, so it does not. */
const REFRESH_MS = 15_000;

/**
 * The api's window is ten minutes, so a dot can legitimately be up to ten minutes
 * old. Opacity fades across exactly that range.
 */
const WINDOW_SECONDS = 600;

/**
 * Past this, the newest position in the whole table is old enough that the pipeline
 * itself is suspect rather than the buses being parked. Five minutes is fifteen
 * missed collector polls.
 */
const PIPELINE_STALE_SECONDS = 300;

/** Keyless vector basemap: nothing here needs an API key. */
const BASEMAP_STYLE = "https://tiles.openfreemap.org/styles/liberty";

const HALIFAX: [number, number] = [-63.5752, 44.6488];

type VehicleProperties = {
  vehicle_id: string;
  route_id: string | null;
  trip_id: string | null;
  bearing: number | null;
  vehicle_timestamp: string;
};

type SnapshotResponse = {
  type: "FeatureCollection";
  snapshot_at: string | null;
  vehicle_count: number;
  features: {
    type: "Feature";
    geometry: { type: "Point"; coordinates: [number, number] };
    properties: VehicleProperties;
  }[];
};

type LiveResponse = {
  type: "FeatureCollection";
  generated_at: string;
  newest_position_at: string | null;
  vehicle_count: number;
  features: {
    type: "Feature";
    geometry: { type: "Point"; coordinates: [number, number] };
    properties: VehicleProperties;
  }[];
};

/**
 * The three live states the page must make visibly distinct, its startup state, and the
 * historical one.
 *
 * `snapshot` is not a failure or a fallback — it is what the hosted site shows, and it
 * carries the moment so the page can name it. It is deliberately a separate kind from
 * `live`: a single state covering both would let a historical timestamp be rendered as
 * freshness, which is the one dishonest thing this map could do.
 */
type Status =
  | { kind: "loading" }
  | { kind: "live"; vehicles: number; newestAgeSeconds: number }
  | { kind: "no-buses"; newestAgeSeconds: number }
  | { kind: "no-data"; newestAgeSeconds: number | null }
  | { kind: "snapshot"; vehicles: number; snapshotAt: string }
  | { kind: "unreachable"; detail: string };

/**
 * The moment, in words, for a reader rather than a log.
 *
 * Assembled from parts rather than handed to `toLocaleString`, for two reasons. The
 * ordering would otherwise follow the browser's locale — en-CA renders "October 6,
 * 2026" while every date written on this site reads "6 October 2026" — and a label the
 * test suite asserts should not depend on which ICU version the browser shipped with.
 *
 * Always Halifax time, because the moment belongs to the agency's clock and not the
 * reader's.
 */
function describeMoment(isoTimestamp: string): string {
  const parts = new Intl.DateTimeFormat("en-CA", {
    weekday: "long",
    day: "numeric",
    month: "long",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
    timeZone: "America/Halifax",
  }).formatToParts(new Date(isoTimestamp));

  const part = (type: Intl.DateTimeFormatPartTypes): string =>
    parts.find((candidate) => candidate.type === type)?.value ?? "";

  return `${part("weekday")} ${part("day")} ${part("month")} ${part("year")}, ${part("hour")}:${part("minute")}`;
}

function ageSeconds(isoTimestamp: string, now: number): number {
  return Math.max(0, (now - Date.parse(isoTimestamp)) / 1000);
}

function describeAge(seconds: number | null): string {
  if (seconds === null) return "never";
  if (seconds < 90) return `${Math.round(seconds)}s ago`;
  return `${Math.round(seconds / 60)} min ago`;
}

export default function LiveMapPage() {
  const container = useRef<HTMLDivElement | null>(null);
  const map = useRef<maplibregl.Map | null>(null);
  const [status, setStatus] = useState<Status>({ kind: "loading" });

  useEffect(() => {
    if (!container.current || map.current) return;

    const created = new maplibregl.Map({
      container: container.current,
      style: BASEMAP_STYLE,
      center: HALIFAX,
      zoom: 11,
      attributionControl: { compact: false },
    });
    created.addControl(new maplibregl.NavigationControl(), "top-right");

    created.on("load", () => {
      created.addSource("vehicles", {
        type: "geojson",
        data: { type: "FeatureCollection", features: [] },
      });

      created.addLayer({
        id: "vehicle-dots",
        type: "circle",
        source: "vehicles",
        paint: {
          "circle-radius": ["interpolate", ["linear"], ["zoom"], 9, 4, 14, 8],
          "circle-color": "#1d4ed8",
          "circle-stroke-width": 1,
          "circle-stroke-color": "#ffffff",
          // Each dot fades by its OWN age, not by the page's global freshness: a bus
          // that stopped reporting nine minutes ago must not look identical to one
          // reporting now. age_seconds is computed per refresh, because MapLibre
          // expressions cannot parse timestamps.
          "circle-opacity": [
            "interpolate",
            ["linear"],
            ["get", "age_seconds"],
            0,
            1,
            WINDOW_SECONDS,
            0.2,
          ],
          "circle-stroke-opacity": [
            "interpolate",
            ["linear"],
            ["get", "age_seconds"],
            0,
            0.9,
            WINDOW_SECONDS,
            0.15,
          ],
        },
      });
    });

    map.current = created;
    return () => {
      created.remove();
      map.current = null;
    };
  }, []);

  useEffect(() => {
    let cancelled = false;

    async function showSnapshot() {
      try {
        const response = await fetch(`${API_BASE}/api/vehicles/snapshot`, {
          cache: "no-store",
        });
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const body = (await response.json()) as SnapshotResponse;
        if (cancelled) return;

        if (body.snapshot_at === null || body.features.length === 0) {
          // The curated minute was never loaded. Said plainly rather than drawn as an
          // empty map, because an empty map in this mode means a missing restore.
          setStatus({ kind: "no-data", newestAgeSeconds: null });
          return;
        }

        // Every dot is from the same minute, so they are all equally fresh: age zero,
        // which draws them at full opacity. The fade exists to show one bus going
        // stale against others, and inside a single minute there is nothing to show.
        const source = map.current?.getSource("vehicles") as GeoJSONSource | undefined;
        source?.setData({
          type: "FeatureCollection",
          features: body.features.map((feature) => ({
            ...feature,
            properties: { ...feature.properties, age_seconds: 0 },
          })),
        });

        setStatus({
          kind: "snapshot",
          vehicles: body.vehicle_count,
          snapshotAt: body.snapshot_at,
        });
      } catch (error) {
        if (!cancelled) {
          setStatus({ kind: "unreachable", detail: (error as Error).message });
        }
      }
    }

    async function refresh() {
      try {
        // Same origin: vite.config proxies /api to the Spring service in
        // development and the two are served together in production, so there is
        // no CORS in play either way.
        const response = await fetch(`${API_BASE}/api/vehicles/live`, {
          cache: "no-store",
        });
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const body = (await response.json()) as LiveResponse;
        if (cancelled) return;

        const now = Date.now();
        const newestAge =
          body.newest_position_at === null ? null : ageSeconds(body.newest_position_at, now);

        const withAges = {
          type: "FeatureCollection" as const,
          features: body.features.map((feature) => ({
            ...feature,
            properties: {
              ...feature.properties,
              age_seconds: ageSeconds(feature.properties.vehicle_timestamp, now),
            },
          })),
        };

        const source = map.current?.getSource("vehicles") as GeoJSONSource | undefined;
        source?.setData(withAges);

        if (newestAge === null || newestAge > PIPELINE_STALE_SECONDS) {
          // No data is arriving. Distinct from "no buses": the buses may be running
          // perfectly well and the pipeline simply is not telling us.
          setStatus({ kind: "no-data", newestAgeSeconds: newestAge });
        } else if (body.vehicle_count === 0) {
          // Data is current and says nothing is moving — which at 03:00 is the
          // truthful answer, not a fault.
          setStatus({ kind: "no-buses", newestAgeSeconds: newestAge });
        } else {
          setStatus({
            kind: "live",
            vehicles: body.vehicle_count,
            newestAgeSeconds: newestAge,
          });
        }
      } catch (error) {
        if (!cancelled) {
          setStatus({ kind: "unreachable", detail: (error as Error).message });
        }
      }
    }

    if (MAP_MODE === "snapshot") {
      // Fetched once: a fixed minute cannot change, and polling it would be asking the
      // same question of history every fifteen seconds.
      void showSnapshot();
      return () => {
        cancelled = true;
      };
    }

    void refresh();
    // 15s, not 10: the feed itself only changes every 20s, so a faster poll spends
    // half its requests fetching data it already has.
    const timer = setInterval(refresh, REFRESH_MS);
    return () => {
      cancelled = true;
      clearInterval(timer);
    };
  }, []);

  return (
    <>
      <header className="masthead">
        <h1>{MAP_MODE === "snapshot" ? "The network, one minute" : "Live map"}</h1>
        <span className="status">{summarise(status)}</span>
      </header>

      <div className="map-wrap">
        <div className="map" ref={container} />

        {status.kind === "snapshot" && (
          <div className="notice moment" role="status">
            <strong>{describeMoment(status.snapshotAt)}</strong>
            <small>
              {status.vehicles} vehicles, as the agency reported them in that one minute
              — the busiest minute of 17:00, the network&rsquo;s worst hour, on its worst
              day of the published week. This is history, not a live feed: nothing is
              collecting positions for the hosted site.
            </small>
          </div>
        )}

        {status.kind === "no-buses" && (
          <div className="notice quiet" role="status">
            <strong>No buses currently running</strong>
            <small>
              The feed is current — newest position {describeAge(status.newestAgeSeconds)} — and it
              reports nothing on the road.
            </small>
          </div>
        )}

        {status.kind === "no-data" && (
          <div className="notice down" role="alert">
            <strong>No recent data</strong>
            <small>
              Newest position {describeAge(status.newestAgeSeconds)}. Buses may well be running;
              the collector or loader is probably not.
            </small>
          </div>
        )}

        {status.kind === "unreachable" && (
          <div className="notice down" role="alert">
            <strong>Cannot reach the API</strong>
            <small>{status.detail}</small>
          </div>
        )}
      </div>
    </>
  );
}

function summarise(status: Status): string {
  switch (status.kind) {
    case "loading":
      return "loading…";
    case "live":
      return `${status.vehicles} vehicles · newest ${describeAge(status.newestAgeSeconds)} · refreshes every 15s`;
    case "no-buses":
      return `0 vehicles · feed current (${describeAge(status.newestAgeSeconds)})`;
    case "snapshot":
      return `${status.vehicles} vehicles · ${describeMoment(status.snapshotAt)} · a recorded minute, not live`;
    case "no-data":
      return `no recent data · newest ${describeAge(status.newestAgeSeconds)}`;
    case "unreachable":
      return "API unreachable";
  }
}
