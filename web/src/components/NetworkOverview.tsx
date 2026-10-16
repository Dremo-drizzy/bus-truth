import type { NetworkSummary } from "../api";
import { useApi } from "../api";
import { Delay, Failed, Loading, SharePercent } from "./Figures";

/**
 * The network's figure for the range, and every day inside it.
 *
 * The two are inseparable here by design. Halifax's buses varied by about 21 points
 * between the best and worst day of the published week — wider than the 17.5-point gap
 * between the two candidate definitions of "arrived", and wider still than the
 * 8.7-point margin of error left once that choice was settled. So no page leads with a
 * single percentage: it leads with the period, the figure, and the days the figure
 * averages, because the variation between those days is itself the finding.
 */
export function NetworkOverview({ query }: { query: string | null }) {
  const result = useApi<NetworkSummary>(
    query === null ? null : `/api/performance/summary?${query}`,
  );

  if (result.state === "loading")
    return <Loading what="the network summary" slow={result.slow} />;
  if (result.state === "failed") {
    return <Failed what="the network summary" detail={result.error.message} />;
  }

  const summary = result.data;
  if (summary.arrivals === 0) return null;

  const percentages = summary.by_day
    .map((day) => day.on_time.percent)
    .filter((percent): percent is number => percent !== null);
  const spread =
    percentages.length > 1
      ? Math.max(...percentages) - Math.min(...percentages)
      : null;

  return (
    <section className="overview">
      <div className="overview-figure">
        <h2>
          On time, {summary.from} to {summary.to}
        </h2>
        <SharePercent share={summary.on_time} />
        <p className="muted">
          median <Delay seconds={summary.median_delay_seconds} />, worst tenth{" "}
          <Delay seconds={summary.p90_delay_seconds} />
        </p>
      </div>

      <div className="overview-days">
        <h2>By hour of the day</h2>
        <ol className="daybars">
          {summary.by_hour
            .filter((hour) => hour.arrivals >= 100)
            .map((hour) => (
              <li key={hour.hour}>
                <span className="daybar-label">
                  {String(hour.hour).padStart(2, "0")}:00
                </span>
                <span className="daybar-track" aria-hidden="true">
                  <span
                    className="daybar-fill"
                    style={{ width: `${hour.on_time.percent ?? 0}%` }}
                  />
                </span>
                <span className="daybar-value">
                  {hour.on_time.percent === null
                    ? "—"
                    : `${hour.on_time.percent.toFixed(0)}%`}
                  <small className="muted"> of {hour.arrivals.toLocaleString()}</small>
                </span>
              </li>
            ))}
        </ol>
        <p className="muted">
          This is the clearest pattern in the data, and it has an obvious cause: the
          afternoon peak. Hours with fewer than 100 arrivals are left out.
        </p>
      </div>

      <div className="overview-days">
        <h2>By service date</h2>
        <ol className="daybars">
          {summary.by_day.map((day) => (
            <li key={day.service_date}>
              {/* Noon, so the label cannot slip a day: a date-only string parses as
                  UTC midnight, which is the previous evening in Halifax. */}
              <span className="daybar-label">
                {new Date(`${day.service_date}T12:00:00`).toLocaleDateString(undefined, {
                  weekday: "short",
                  day: "numeric",
                  month: "short",
                })}
              </span>
              <span className="daybar-track" aria-hidden="true">
                <span
                  className="daybar-fill"
                  style={{ width: `${day.on_time.percent ?? 0}%` }}
                />
              </span>
              <span className="daybar-value">
                {day.on_time.percent === null
                  ? "—"
                  : `${day.on_time.percent.toFixed(0)}%`}
                <small className="muted"> of {day.arrivals.toLocaleString()}</small>
              </span>
            </li>
          ))}
        </ol>
        {spread !== null && (
          <p className="muted">
            <strong>{spread.toFixed(1)} points</strong> between the best and worst of
            these {summary.by_day.length} days — a range over a handful of days, not a
            margin of error, so it is not like-for-like with this project's 8.7-point
            margin or the 17.5 points the definition of &ldquo;arrived&rdquo; is worth.
            Read it with the arrival counts beside it: the days were not watched
            equally, and a day observed mostly in the evening scores better than one
            observed through the afternoon peak, whatever its buses did.
          </p>
        )}
      </div>
    </section>
  );
}
