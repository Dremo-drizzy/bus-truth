import { useEffect, useState } from "react";

/**
 * The api, typed, plus the one hook every page uses to call it.
 *
 * <p>The types mirror the Java records deliberately closely, snake_case and all. A
 * camelCase translation layer here would be one more place for the two sides to
 * disagree about what a field means, and the only thing it would buy is house style.
 */

/**
 * A percentage with the counts it came from.
 *
 * `percent` is null, not zero, when nothing was observed. Zero percent is a claim
 * about the service; null means the question has no answer for this range, and the two
 * must never render the same way.
 */
export type Share = {
  count: number;
  of: number;
  percent: number | null;
};

export type Route = {
  route_id: string;
  route_short_name: string | null;
  route_long_name: string | null;
};

export type RoutePerformance = Route & {
  trips_observed: number;
  arrivals: number;
  on_time: Share;
  median_delay_seconds: number | null;
  p90_delay_seconds: number | null;
  /** False when the route had too few arrivals to take a position in the ranking. */
  rankable: boolean;
};

export type HourOfDay = {
  hour: number;
  arrivals: number;
  on_time: Share;
  median_delay_seconds: number | null;
};

export type Day = {
  service_date: string;
  arrivals: number;
  on_time: Share;
  median_delay_seconds: number | null;
};

export type StopRow = {
  stop_id: string;
  stop_name: string | null;
  arrivals: number;
  on_time: Share;
  median_delay_seconds: number | null;
};

export type RouteRow = {
  route_id: string;
  route_short_name: string | null;
  arrivals: number;
  on_time: Share;
  median_delay_seconds: number | null;
};

export type RouteDetail = Route & {
  arrivals: number;
  on_time: Share;
  median_delay_seconds: number | null;
  p90_delay_seconds: number | null;
  by_hour: HourOfDay[];
  by_day: Day[];
  worst_stops: StopRow[];
  min_arrivals_applied: number;
  stops_below_threshold: number;
};

export type StopReliability = {
  stop_id: string;
  stop_name: string | null;
  arrivals: number;
  on_time: Share;
  median_delay_seconds: number | null;
  p90_delay_seconds: number | null;
  by_route: RouteRow[];
};

export type CoverageDay = {
  service_date: string;
  snapshots_captured: number;
  snapshots_expected: number;
  coverage: Share;
  arrivals_derived: number;
};

/**
 * Below this many observations a share is shown as "too few observations" rather than
 * as a number.
 *
 * Matches the api's own default for ranking, so the site and the API agree about what
 * counts as too thin. Arrival detection observes about 17.5% of scheduled stop-events,
 * so thin denominators are normal here rather than exceptional, and a page that prints
 * "0% on time" over two arrivals has published a false claim about a bus route.
 */
export const TOO_FEW = 5;

export function tooFew(share: Share): boolean {
  return share.of < TOO_FEW;
}

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number | null,
  ) {
    super(message);
  }
}

async function getJson<T>(path: string, signal: AbortSignal): Promise<T> {
  let response: Response;
  try {
    response = await fetch(path, { signal, cache: "no-store" });
  } catch (cause) {
    // A network-level failure, which is a different thing to tell the reader than a
    // 404: the API is unreachable rather than the route unknown.
    throw new ApiError((cause as Error).message, null);
  }
  if (!response.ok) {
    throw new ApiError(`HTTP ${response.status}`, response.status);
  }
  return (await response.json()) as T;
}

/** Loading, loaded or failed — the three states every page has to render. */
export type Loadable<T> =
  | { state: "loading" }
  | { state: "loaded"; data: T }
  | { state: "failed"; error: ApiError };


/**
 * Fetches a path and keeps the three states apart.
 *
 * The request is aborted on unmount and whenever the path changes, so a slow response
 * for last week's date range cannot arrive after a fast one for this week's and
 * overwrite it. That race is invisible in development and routine on a phone.
 */
export function useApi<T>(path: string | null): Loadable<T> {
  const [result, setResult] = useState<Loadable<T>>({ state: "loading" });

  useEffect(() => {
    if (path === null) {
      setResult({ state: "loading" });
      return;
    }
    const controller = new AbortController();
    setResult({ state: "loading" });

    getJson<T>(path, controller.signal)
      .then((data) => setResult({ state: "loaded", data }))
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        setResult({
          state: "failed",
          error:
            error instanceof ApiError ? error : new ApiError(String(error), null),
        });
      });

    return () => controller.abort();
  }, [path]);

  return result;
}

/** Formats a delay the way a passenger would say it. */
export function formatDelay(seconds: number | null): string {
  if (seconds === null) return "—";
  const rounded = Math.round(Math.abs(seconds));
  const minutes = Math.floor(rounded / 60);
  const remainder = rounded % 60;
  const magnitude = minutes === 0 ? `${remainder}s` : `${minutes}m ${remainder}s`;
  if (rounded === 0) return "on the minute";
  return seconds < 0 ? `${magnitude} early` : `${magnitude} late`;
}

export function routeLabel(route: {
  route_short_name: string | null;
  route_id: string;
}): string {
  return route.route_short_name ?? route.route_id;
}
