import { Link, useParams } from "react-router-dom";
import type { StopReliability } from "../api";
import { useApi } from "../api";
import { Delay, Empty, Failed, Loading, SharePercent } from "../components/Figures";
import { rangeQuery, useRange } from "../range";

/**
 * One stop, broken down by the routes that call there.
 *
 * <p>The thinnest page on the site by construction, and it says so. Arrival detection
 * observes about 17.5% of scheduled stop-events, because the feed reports a trip's
 * position roughly 6.8 times across 38.3 stops — a route has thousands of arrivals to
 * average, a single stop often has a handful. Hiding that would make this the page
 * most likely to mislead; stating it makes it the page that explains the rest.
 */
export default function StopPage() {
  const { stopId = "" } = useParams();
  const range = useRange();
  const query = rangeQuery(range);
  const result = useApi<StopReliability>(
    query === null
      ? null
      : `/api/stops/${encodeURIComponent(stopId)}/reliability?${query}`,
  );

  if (result.state === "loading")
    return <Loading what={`stop ${stopId}`} slow={result.slow} />;
  if (result.state === "failed") {
    if (result.error.status === 404) {
      return (
        <Empty>
          The published schedule has no stop {stopId}.{" "}
          <Link to="/routes">Back to the routes</Link>.
        </Empty>
      );
    }
    return <Failed what={`stop ${stopId}`} detail={result.error.message} />;
  }

  const stop = result.data;

  return (
    <>
      <p className="crumb">
        <Link to="/routes">← all routes</Link>
      </p>
      <h1>{stop.stop_name ?? `Stop ${stop.stop_id}`}</h1>
      <p className="lede">
        Stop {stop.stop_id} · {range.from} to {range.to}
      </p>

      <section className="cards">
        <div className="card">
          <h2>On time</h2>
          <SharePercent share={stop.on_time} />
        </div>
        <div className="card">
          <h2>Median delay</h2>
          <p className="figure">
            <Delay seconds={stop.median_delay_seconds} />
          </p>
        </div>
        <div className="card">
          <h2>Worst 10% of arrivals</h2>
          <p className="figure">
            <Delay seconds={stop.p90_delay_seconds} />
          </p>
        </div>
      </section>

      {stop.arrivals === 0 ? (
        <Empty>
          No arrivals were derived at this stop between {range.from} and {range.to}.
          Stop-level coverage is thin by nature — the feed reports a bus's position
          about 6.8 times across a 38-stop trip, so most stops are never named on most
          trips. See <Link to="/about">how this is measured</Link>.
        </Empty>
      ) : (
        <section>
          <h2>By route</h2>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th scope="col">Route</th>
                  <th scope="col">On time</th>
                  <th scope="col">Median</th>
                </tr>
              </thead>
              <tbody>
                {stop.by_route.map((route) => (
                  <tr key={route.route_id}>
                    <th scope="row">
                      <Link to={`/routes/${encodeURIComponent(route.route_id)}`}>
                        <span className="badge">
                          {route.route_short_name ?? route.route_id}
                        </span>
                      </Link>
                    </th>
                    <td>
                      <SharePercent share={route.on_time} />
                    </td>
                    <td>
                      <Delay seconds={route.median_delay_seconds} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </>
  );
}
