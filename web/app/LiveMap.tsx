"use client";

// maplibre-gl 6 has no default export: everything is a named export.
import { GeoJSONSource, Map as MapLibreMap, NavigationControl } from "maplibre-gl";
import { useEffect, useRef, useState } from "react";
import "maplibre-gl/dist/maplibre-gl.css";

/** How often the page asks for positions. */
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

/** The three states the page must make visibly distinct, plus its startup state. */
type Status =
  | { kind: "loading" }
  | { kind: "live"; vehicles: number; newestAgeSeconds: number }
  | { kind: "no-buses"; newestAgeSeconds: number }
  | { kind: "no-data"; newestAgeSeconds: number | null }
  | { kind: "unreachable"; detail: string };

function ageSeconds(isoTimestamp: string, now: number): number {
  return Math.max(0, (now - Date.parse(isoTimestamp)) / 1000);
}

function describeAge(seconds: number | null): string {
  if (seconds === null) return "never";
  if (seconds < 90) return `${Math.round(seconds)}s ago`;
  return `${Math.round(seconds / 60)} min ago`;
}

export default function LiveMap() {
  const container = useRef<HTMLDivElement | null>(null);
  const map = useRef<MapLibreMap | null>(null);
  const [status, setStatus] = useState<Status>({ kind: "loading" });

  useEffect(() => {
    if (!container.current || map.current) return;

    const created = new MapLibreMap({
      container: container.current,
      style: BASEMAP_STYLE,
      center: HALIFAX,
      zoom: 11,
      attributionControl: { compact: false },
    });
    created.addControl(new NavigationControl(), "top-right");

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

    async function refresh() {
      try {
        // Same origin: next.config proxies /api to the Spring service, so there is
        // no CORS in play.
        const response = await fetch("/api/vehicles/live", { cache: "no-store" });
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
        <h1>Bus Truth — live map</h1>
        <span className="status">{summarise(status)}</span>
      </header>

      <div className="map-wrap">
        <div className="map" ref={container} />

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
    case "no-data":
      return `no recent data · newest ${describeAge(status.newestAgeSeconds)}`;
    case "unreachable":
      return "API unreachable";
  }
}
