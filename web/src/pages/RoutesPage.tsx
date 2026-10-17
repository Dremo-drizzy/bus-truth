import { Link } from "react-router-dom";
import type { RoutePerformance } from "../api";
import { routeLabel, useApi } from "../api";
import { Delay, Empty, Failed, Loading, ShareInline } from "../components/Figures";
import { rangeQuery, useRange } from "../range";

/**
 * Every route, ranked by on-time share.
 *
 * <p>Split into two tables rather than one. The api returns thinly observed routes
 * with {@code rankable: false} and sorts them last; showing them inline would invite
 * exactly the misreading the flag exists to prevent — on 9 October the top three
 * routes by raw share had eleven, four and two arrivals between them. Below the table
 * they are visible as what they are: routes this project barely saw.
 */
export default function RoutesPage() {
  const range = useRange();
  const query = rangeQuery(range);
  const result = useApi<RoutePerformance[]>(
    query === null ? null : `/api/routes/performance?${query}`,
  );

  if (result.state === "loading") return <Loading what="route performance" />;
  if (result.state === "failed") {
    return <Failed what="route performance" detail={result.error.message} />;
  }

  const ranked = result.data.filter((route) => route.rankable);
  const thin = result.data.filter((route) => !route.rankable);

  return (
    <>
      <h1>Routes by on-time performance</h1>
      <p className="lede">
        {range.from} to {range.to}. A bus counts as on time from one minute early to
        five minutes late, measured against the schedule published for that day. Every
        percentage shows the number of arrivals behind it.
      </p>

      {ranked.length === 0 && thin.length === 0 && (
        <Empty>
          No arrivals were derived in this range. That is a gap in our collection, not
          an absence of buses — see <Link to="/about">how this is measured</Link>.
        </Empty>
      )}

      {ranked.length > 0 && <RouteTable routes={ranked} />}

      {thin.length > 0 && (
        <section className="aside">
          <h2>Too thinly observed to rank</h2>
          <p className="muted">
            {thin.length} {thin.length === 1 ? "route" : "routes"} had fewer than five
            derived arrivals in this range. Their percentages are arithmetically true
            and tell you nothing about the service, so they are not ranked.
          </p>
          <RouteTable routes={thin} />
        </section>
      )}
    </>
  );
}

function RouteTable({ routes }: { routes: RoutePerformance[] }) {
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th scope="col">Route</th>
            <th scope="col">On time</th>
            <th scope="col">Median</th>
            <th scope="col">Worst 10%</th>
            <th scope="col">Trips</th>
          </tr>
        </thead>
        <tbody>
          {routes.map((route) => (
            <tr key={route.route_id}>
              <th scope="row">
                <Link to={`/routes/${encodeURIComponent(route.route_id)}`}>
                  <span className="badge">{routeLabel(route)}</span>
                  <span className="route-name">{route.route_long_name}</span>
                </Link>
              </th>
              <td>
                <ShareInline share={route.on_time} />
              </td>
              <td>
                <Delay seconds={route.median_delay_seconds} />
              </td>
              <td>
                <Delay seconds={route.p90_delay_seconds} />
              </td>
              <td>{route.trips_observed.toLocaleString()}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
