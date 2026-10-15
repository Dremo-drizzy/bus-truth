import { createContext, useContext, useMemo } from "react";
import { useSearchParams } from "react-router-dom";
import type { CoverageDay } from "./api";
import { useApi } from "./api";

/**
 * The date range every performance page is asked about, and where its default comes
 * from.
 *
 * <p>The default is not "the last seven days". Collection has run on a laptop that
 * slept, so most recent dates have no derived arrivals at all, and a site that
 * defaulted to the last week would open on an empty page and look broken. Instead the
 * default is the span the project actually has arrivals for, taken from
 * {@code /api/coverage} — the same endpoint that publishes the gaps.
 *
 * <p>The chosen range lives in the URL, so a link to a finding carries the range it
 * was found in. A figure without its range is not reproducible.
 */

type Range = {
  from: string;
  to: string;
  /** Null while coverage is still loading, so pages can wait rather than guess. */
  ready: boolean;
  setRange: (from: string, to: string) => void;
  coverage: CoverageDay[];
};

const RangeContext = createContext<Range | null>(null);

export function RangeProvider({ children }: { children: React.ReactNode }) {
  const [params, setParams] = useSearchParams();
  const coverage = useApi<CoverageDay[]>("/api/coverage");

  const days = coverage.state === "loaded" ? coverage.data : [];
  const withArrivals = days.filter((day) => day.arrivals_derived > 0);

  const value = useMemo<Range>(() => {
    const first = withArrivals.at(0)?.service_date;
    const last = withArrivals.at(-1)?.service_date;
    return {
      from: params.get("from") ?? first ?? "",
      to: params.get("to") ?? last ?? "",
      // Ready once either the URL says what to ask for, or coverage has told us what
      // there is to ask about. Until then a page would be requesting an empty range.
      ready:
        (params.has("from") && params.has("to")) ||
        (coverage.state === "loaded" && first !== undefined),
      setRange: (from, to) => {
        const next = new URLSearchParams(params);
        next.set("from", from);
        next.set("to", to);
        setParams(next, { replace: true });
      },
      coverage: days,
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [params, coverage.state, withArrivals.length, days.length]);

  return <RangeContext.Provider value={value}>{children}</RangeContext.Provider>;
}

export function useRange(): Range {
  const range = useContext(RangeContext);
  if (range === null) throw new Error("useRange outside RangeProvider");
  return range;
}

/** The query string for a performance request, or null while the range is unknown. */
export function rangeQuery(range: Range): string | null {
  if (!range.ready || range.from === "" || range.to === "") return null;
  return `from=${range.from}&to=${range.to}`;
}

export function RangePicker() {
  const range = useRange();
  const dates = range.coverage
    .filter((day) => day.arrivals_derived > 0)
    .map((day) => day.service_date);

  return (
    <form className="range" onSubmit={(event) => event.preventDefault()}>
      <label>
        From
        <input
          type="date"
          value={range.from}
          min={dates.at(0)}
          max={dates.at(-1)}
          onChange={(event) => range.setRange(event.target.value, range.to)}
        />
      </label>
      <label>
        To
        <input
          type="date"
          value={range.to}
          min={dates.at(0)}
          max={dates.at(-1)}
          onChange={(event) => range.setRange(range.from, event.target.value)}
        />
      </label>
      {dates.length > 0 && (
        <small className="muted">
          arrivals derived for {dates.length} {dates.length === 1 ? "day" : "days"}
        </small>
      )}
    </form>
  );
}
