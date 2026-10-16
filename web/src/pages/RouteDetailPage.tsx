import { Link, useParams } from "react-router-dom";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { RouteDetail } from "../api";
import { routeLabel, useApi } from "../api";
import { Delay, Empty, Failed, Loading, SharePercent } from "../components/Figures";
import { rangeQuery, useRange } from "../range";

/**
 * One route: by hour of the local day, by service date, and its worst stops.
 *
 * <p>The charts carry the same honesty rule as the tables. An hour with fewer than
 * {@link TOO_FEW} arrivals is drawn in a muted colour and its tooltip gives the count,
 * because a bar at 0% built on one observation reads identically to a bar at 0% built
 * on fifty, and the first means nothing.
 */
export default function RouteDetailPage() {
  const { routeId = "" } = useParams();
  const range = useRange();
  const displayFloor = range.displayFloor;
  const query = rangeQuery(range);
  const result = useApi<RouteDetail>(
    query === null
      ? null
      : `/api/routes/${encodeURIComponent(routeId)}/performance?${query}`,
  );

  if (result.state === "loading") return <Loading what={`route ${routeId}`} />;
  if (result.state === "failed") {
    if (result.error.status === 404) {
      return (
        <Empty>
          The published schedule has no route {routeId}.{" "}
          <Link to="/routes">Back to the routes</Link>.
        </Empty>
      );
    }
    return <Failed what={`route ${routeId}`} detail={result.error.message} />;
  }

  const route = result.data;
  const hours = route.by_hour.map((hour) => ({
    ...hour,
    label: `${String(hour.hour).padStart(2, "0")}:00`,
    percent: hour.on_time.percent,
  }));
  const days = route.by_day.map((day) => ({
    ...day,
    percent: day.on_time.percent,
  }));

  return (
    <>
      <p className="crumb">
        <Link to="/routes">← all routes</Link>
      </p>
      <h1>
        <span className="badge big">{routeLabel(route)}</span>{" "}
        {route.route_long_name}
      </h1>

      <section className="cards">
        <div className="card">
          <h2>On time</h2>
          <SharePercent share={route.on_time} />
        </div>
        <div className="card">
          <h2>Median delay</h2>
          <p className="figure">
            <Delay seconds={route.median_delay_seconds} />
          </p>
        </div>
        <div className="card">
          <h2>Worst 10% of arrivals</h2>
          <p className="figure">
            <Delay seconds={route.p90_delay_seconds} />
          </p>
        </div>
      </section>

      {route.arrivals === 0 ? (
        <Empty>
          This route is in the published schedule, but no arrivals were derived for it
          between {range.from} and {range.to}. That is a gap in our collection rather
          than a route that did not run — see{" "}
          <Link to="/about">how this is measured</Link>.
        </Empty>
      ) : (
        <>
          <section>
            <h2>By hour of the day</h2>
            <p className="muted">
              Local time in Halifax. Faded bars had fewer than {displayFloor} arrivals,
              too few for a percentage to mean much.
            </p>
            <div className="chart">
              <ResponsiveContainer width="100%" height={240}>
                <BarChart data={hours} margin={{ top: 8, right: 8, bottom: 0, left: -20 }}>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} />
                  <XAxis dataKey="label" interval="preserveStartEnd" tick={{ fontSize: 12 }} />
                  <YAxis domain={[0, 100]} unit="%" tick={{ fontSize: 12 }} />
                  <Tooltip
                    formatter={(value, _name, entry) => {
                      const hour = entry.payload as (typeof hours)[number];
                      return [
                        `${value}% of ${hour.on_time.of} arrivals`,
                        "on time",
                      ];
                    }}
                  />
                  <Bar dataKey="percent" radius={[3, 3, 0, 0]}>
                    {hours.map((hour) => (
                      <Cell
                        key={hour.hour}
                        fill={hour.on_time.of < displayFloor ? "#c7d2fe" : "#1d4ed8"}
                      />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </div>
          </section>

          <section>
            <h2>By day</h2>
            <div className="chart">
              <ResponsiveContainer width="100%" height={220}>
                <LineChart data={days} margin={{ top: 8, right: 8, bottom: 0, left: -20 }}>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} />
                  <XAxis dataKey="service_date" tick={{ fontSize: 12 }} />
                  <YAxis domain={[0, 100]} unit="%" tick={{ fontSize: 12 }} />
                  <Tooltip
                    formatter={(value, _name, entry) => {
                      const day = entry.payload as (typeof days)[number];
                      return [`${value}% of ${day.on_time.of} arrivals`, "on time"];
                    }}
                  />
                  <Line
                    type="monotone"
                    dataKey="percent"
                    stroke="#1d4ed8"
                    strokeWidth={2}
                    dot={{ r: 3 }}
                    connectNulls={false}
                  />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </section>

          <section>
            <h2>Worst stops</h2>
            <p className="muted">
              Ranked among stops with at least {route.min_arrivals_applied} derived
              arrivals.
              {route.stops_below_threshold > 0 && (
                <>
                  {" "}
                  {route.stops_below_threshold} further{" "}
                  {route.stops_below_threshold === 1 ? "stop was" : "stops were"} left
                  out for having fewer.
                </>
              )}
            </p>
            {route.worst_stops.length === 0 ? (
              <Empty>
                No stop on this route has {route.min_arrivals_applied} or more derived
                arrivals in this range.
              </Empty>
            ) : (
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th scope="col">Stop</th>
                      <th scope="col">On time</th>
                      <th scope="col">Median</th>
                    </tr>
                  </thead>
                  <tbody>
                    {route.worst_stops.map((stop) => (
                      <tr key={stop.stop_id}>
                        <th scope="row">
                          <Link to={`/stops/${encodeURIComponent(stop.stop_id)}`}>
                            {stop.stop_name ?? stop.stop_id}
                          </Link>
                        </th>
                        <td>
                          <SharePercent share={stop.on_time} />
                        </td>
                        <td>
                          <Delay seconds={stop.median_delay_seconds} />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        </>
      )}
    </>
  );
}
