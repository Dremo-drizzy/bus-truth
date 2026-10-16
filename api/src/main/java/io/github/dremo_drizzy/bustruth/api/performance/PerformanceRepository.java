package io.github.dremo_drizzy.bustruth.api.performance;

import io.github.dremo_drizzy.bustruth.api.performance.PerformanceRows.CoverageDay;
import io.github.dremo_drizzy.bustruth.api.performance.PerformanceRows.Day;
import io.github.dremo_drizzy.bustruth.api.performance.PerformanceRows.HourOfDay;
import io.github.dremo_drizzy.bustruth.api.performance.PerformanceRows.Route;
import io.github.dremo_drizzy.bustruth.api.performance.PerformanceRows.RoutePerformance;
import io.github.dremo_drizzy.bustruth.api.performance.PerformanceRows.RouteRow;
import io.github.dremo_drizzy.bustruth.api.performance.PerformanceRows.Stop;
import io.github.dremo_drizzy.bustruth.api.performance.PerformanceRows.StopRow;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Every aggregate the site publishes, as SQL.
 *
 * <p>In SQL rather than in Java because these are set operations over a few hundred
 * thousand rows, and because an aggregate expressed as a query can be run by hand
 * against the database when someone disputes a number — which is the only way to
 * defend a figure. The architecture reserves metrics for SQL for that reason.
 *
 * <p>Two conventions run through all of it. Counts always accompany percentages, so
 * every query returns both the numerator and the denominator and never a ratio. And
 * {@code percentile_disc} is used rather than {@code percentile_cont}: it returns a
 * delay that some bus actually had, instead of interpolating between two buses to
 * produce a number no passenger experienced.
 */
@Repository
public class PerformanceRepository {

    /**
     * Route and stop names come from the most recently loaded publication.
     *
     * <p>Deliberately not the publication each row was compared against. A passenger
     * reading the site wants today's name for the route; the comparison that produced
     * the delay used the publication in force on the day (ADR-015). Mixing the two up
     * would mean a route renamed last month appearing under both names.
     */
    private static final String LATEST_ROUTES = """
            select distinct on (r.route_id)
                   r.route_id, r.route_short_name, r.route_long_name
            from gtfs_routes r
            join gtfs_feed_versions v using (feed_version)
            order by r.route_id, v.loaded_at desc
            """;

    private static final String LATEST_STOPS = """
            select distinct on (s.stop_id) s.stop_id, s.stop_name
            from gtfs_stops s
            join gtfs_feed_versions v using (feed_version)
            order by s.stop_id, v.loaded_at desc
            """;

    private final JdbcClient jdbcClient;

    public PerformanceRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** Every route in the newest publication, for a dropdown. */
    public List<Route> routes() {
        return jdbcClient.sql("""
                        with named as (%s)
                        select route_id, route_short_name, route_long_name
                        from named
                        -- Short names are mostly numbers held as text, so a plain sort
                        -- puts 10 before 2. Sorting numerically where possible keeps
                        -- the dropdown in the order a passenger expects.
                        order by nullif(regexp_replace(
                                     coalesce(route_short_name, ''), '\\D', '', 'g'), '')::bigint
                                 nulls last,
                                 route_short_name, route_id
                        """.formatted(LATEST_ROUTES))
                .query((rs, rowNum) -> new Route(
                        rs.getString("route_id"),
                        rs.getString("route_short_name"),
                        rs.getString("route_long_name")))
                .list();
    }

    /**
     * The leaderboard: every route with observations in the range, ranked.
     *
     * <p>Ranking by on-time share alone produces nonsense, and the live data says so.
     * On 9 October the top three routes by share were at 100% of eleven, four and two
     * arrivals, and the bottom two at 0% of one. Sorted that way, the leaderboard
     * ranks routes by how little was observed of them.
     *
     * <p>So routes with at least {@code minArrivals} arrivals sort first, and only
     * within each group does the share decide, with the count breaking ties. Nothing
     * is dropped — "every route" means every route, and a route observed once is a
     * fact about this project's coverage that it should not hide — but a thinly
     * observed one cannot top the table, and {@code rankable} lets the page show it
     * apart rather than inline.
     */
    public List<RoutePerformance> routePerformance(LocalDate from, LocalDate to,
                                                   int minArrivals) {
        return jdbcClient.sql("""
                        with named as (%s)
                        select o.route_id,
                               n.route_short_name,
                               n.route_long_name,
                               count(distinct (o.trip_id, o.service_date)) as trips_observed,
                               count(*) as arrivals,
                               count(*) filter (where o.on_time) as on_time,
                               count(*) >= :minArrivals as rankable,
                               percentile_disc(0.5) within group (order by o.delay_seconds)
                                   as median_delay,
                               percentile_disc(0.9) within group (order by o.delay_seconds)
                                   as p90_delay
                        from ontime_events o
                        left join named n on n.route_id = o.route_id
                        where o.service_date between :from and :to
                          and o.route_id is not null
                        group by o.route_id, n.route_short_name, n.route_long_name
                        order by count(*) >= :minArrivals desc,
                                 count(*) filter (where o.on_time)::numeric / count(*) desc,
                                 count(*) desc
                        """.formatted(LATEST_ROUTES))
                .param("from", from).param("to", to).param("minArrivals", minArrivals)
                .query((rs, rowNum) -> new RoutePerformance(
                        rs.getString("route_id"),
                        rs.getString("route_short_name"),
                        rs.getString("route_long_name"),
                        rs.getInt("trips_observed"),
                        rs.getInt("arrivals"),
                        Share.of(rs.getInt("on_time"), rs.getInt("arrivals")),
                        rs.getObject("median_delay", Integer.class),
                        rs.getObject("p90_delay", Integer.class),
                        rs.getBoolean("rankable")))
                .list();
    }

    /** The whole network's totals for a range. */
    public Totals networkTotals(LocalDate from, LocalDate to) {
        return jdbcClient.sql("""
                        select count(*) as arrivals,
                               count(*) filter (where on_time) as on_time,
                               percentile_disc(0.5) within group (order by delay_seconds)
                                   as median_delay,
                               percentile_disc(0.9) within group (order by delay_seconds)
                                   as p90_delay
                        from ontime_events
                        where service_date between :from and :to
                        """)
                .param("from", from).param("to", to)
                .query((rs, rowNum) -> new Totals(
                        rs.getInt("arrivals"),
                        rs.getInt("on_time"),
                        rs.getObject("median_delay", Integer.class),
                        rs.getObject("p90_delay", Integer.class)))
                .single();
    }

    /**
     * The whole network, one row per service date.
     *
     * <p>Exists so that no page has to lead with a single percentage. The day-to-day
     * spread across the published week is 21 points — wider than any methodological
     * choice in this project — so a figure for a range is only honest beside the days
     * it averages.
     */
    public List<Day> networkByDay(LocalDate from, LocalDate to) {
        return jdbcClient.sql("""
                        select service_date,
                               count(*) as arrivals,
                               count(*) filter (where on_time) as on_time,
                               percentile_disc(0.5) within group (order by delay_seconds)
                                   as median_delay
                        from ontime_events
                        where service_date between :from and :to
                        group by 1
                        order by 1
                        """)
                .param("from", from).param("to", to)
                .query((rs, rowNum) -> new Day(
                        rs.getObject("service_date", LocalDate.class),
                        rs.getInt("arrivals"),
                        Share.of(rs.getInt("on_time"), rs.getInt("arrivals")),
                        rs.getObject("median_delay", Integer.class)))
                .list();
    }

    /**
     * The whole network, one row per hour of the local day.
     *
     * <p>This is the structure worth publishing. Across the published week the share
     * on time runs from 45.3% at 17:00 to 79.4% at 06:00, with the median delay rising
     * from 37 seconds to 267 — a 34-point swing with an obvious mechanism. The
     * day-to-day spread looks similar in size but is partly an artefact of which hours
     * each day was observed, so the hour is the honest breakdown and the day needs its
     * coverage stated beside it (ADR-019).
     */
    public List<HourOfDay> networkByHour(LocalDate from, LocalDate to) {
        return jdbcClient.sql("""
                        select extract(hour from
                                   observed_arrival_at at time zone 'America/Halifax')::int as hour,
                               count(*) as arrivals,
                               count(*) filter (where on_time) as on_time,
                               percentile_disc(0.5) within group (order by delay_seconds)
                                   as median_delay
                        from ontime_events
                        where service_date between :from and :to
                        group by 1
                        order by 1
                        """)
                .param("from", from).param("to", to)
                .query((rs, rowNum) -> new HourOfDay(
                        rs.getInt("hour"),
                        rs.getInt("arrivals"),
                        Share.of(rs.getInt("on_time"), rs.getInt("arrivals")),
                        rs.getObject("median_delay", Integer.class)))
                .list();
    }

    /** One route's totals for the range, or empty when it was never observed. */
    public Optional<Totals> routeTotals(String routeId, LocalDate from, LocalDate to) {
        return totals("""
                select count(*) as arrivals,
                       count(*) filter (where on_time) as on_time,
                       percentile_disc(0.5) within group (order by delay_seconds) as median_delay,
                       percentile_disc(0.9) within group (order by delay_seconds) as p90_delay
                from ontime_events
                where service_date between :from and :to and route_id = :id
                """, routeId, from, to);
    }

    public Optional<Totals> stopTotals(String stopId, LocalDate from, LocalDate to) {
        return totals("""
                select count(*) as arrivals,
                       count(*) filter (where on_time) as on_time,
                       percentile_disc(0.5) within group (order by delay_seconds) as median_delay,
                       percentile_disc(0.9) within group (order by delay_seconds) as p90_delay
                from ontime_events
                where service_date between :from and :to and stop_id = :id
                """, stopId, from, to);
    }

    private Optional<Totals> totals(String sql, String id, LocalDate from, LocalDate to) {
        Totals totals = jdbcClient.sql(sql)
                .param("id", id).param("from", from).param("to", to)
                .query((rs, rowNum) -> new Totals(
                        rs.getInt("arrivals"),
                        rs.getInt("on_time"),
                        rs.getObject("median_delay", Integer.class),
                        rs.getObject("p90_delay", Integer.class)))
                .single();
        // An aggregate over no rows still returns one row, of zeros. Empty says "this
        // route was never observed", which the endpoint answers with a 404 rather than
        // a page of zeros that looks like a service that never ran.
        return totals.arrivals() == 0 ? Optional.empty() : Optional.of(totals);
    }

    /**
     * One route's on-time share by hour of the local day.
     *
     * <p>The hour is taken in America/Halifax, not UTC. In UTC the evening peak lands
     * three or four hours later and straddles midnight, which would put the worst hour
     * of the day on the wrong day.
     */
    public List<HourOfDay> routeByHour(String routeId, LocalDate from, LocalDate to) {
        return jdbcClient.sql("""
                        select extract(hour from
                                   observed_arrival_at at time zone 'America/Halifax')::int as hour,
                               count(*) as arrivals,
                               count(*) filter (where on_time) as on_time,
                               percentile_disc(0.5) within group (order by delay_seconds)
                                   as median_delay
                        from ontime_events
                        where service_date between :from and :to and route_id = :id
                        group by 1
                        order by 1
                        """)
                .param("id", routeId).param("from", from).param("to", to)
                .query((rs, rowNum) -> new HourOfDay(
                        rs.getInt("hour"),
                        rs.getInt("arrivals"),
                        Share.of(rs.getInt("on_time"), rs.getInt("arrivals")),
                        rs.getObject("median_delay", Integer.class)))
                .list();
    }

    public List<Day> routeByDay(String routeId, LocalDate from, LocalDate to) {
        return jdbcClient.sql("""
                        select service_date,
                               count(*) as arrivals,
                               count(*) filter (where on_time) as on_time,
                               percentile_disc(0.5) within group (order by delay_seconds)
                                   as median_delay
                        from ontime_events
                        where service_date between :from and :to and route_id = :id
                        group by 1
                        order by 1
                        """)
                .param("id", routeId).param("from", from).param("to", to)
                .query((rs, rowNum) -> new Day(
                        rs.getObject("service_date", LocalDate.class),
                        rs.getInt("arrivals"),
                        Share.of(rs.getInt("on_time"), rs.getInt("arrivals")),
                        rs.getObject("median_delay", Integer.class)))
                .list();
    }

    /**
     * A route's worst stops by on-time share.
     *
     * <p>Stops with fewer than {@code minArrivals} arrivals are excluded from the
     * ranking, not because their data is wrong but because a list of "worst stops"
     * sorted by a percentage over two observations is a list of the stops with the
     * fewest observations. The threshold is reported back to the caller and the number
     * excluded is counted, so the page can say what it left out rather than present
     * ten stops as the whole story.
     */
    public List<StopRow> routeWorstStops(String routeId, LocalDate from, LocalDate to,
                                         int minArrivals, int limit) {
        return jdbcClient.sql("""
                        with named as (%s)
                        select o.stop_id,
                               n.stop_name,
                               count(*) as arrivals,
                               count(*) filter (where o.on_time) as on_time,
                               percentile_disc(0.5) within group (order by o.delay_seconds)
                                   as median_delay
                        from ontime_events o
                        left join named n on n.stop_id = o.stop_id
                        where o.service_date between :from and :to and o.route_id = :id
                        group by o.stop_id, n.stop_name
                        having count(*) >= :minArrivals
                        order by count(*) filter (where o.on_time)::numeric / count(*) asc,
                                 count(*) desc
                        limit :limit
                        """.formatted(LATEST_STOPS))
                .param("id", routeId).param("from", from).param("to", to)
                .param("minArrivals", minArrivals).param("limit", limit)
                .query((rs, rowNum) -> new StopRow(
                        rs.getString("stop_id"),
                        rs.getString("stop_name"),
                        rs.getInt("arrivals"),
                        Share.of(rs.getInt("on_time"), rs.getInt("arrivals")),
                        rs.getObject("median_delay", Integer.class)))
                .list();
    }

    /** How many of a route's stops were too thinly observed to rank. */
    public int routeStopsBelowThreshold(String routeId, LocalDate from, LocalDate to,
                                        int minArrivals) {
        return jdbcClient.sql("""
                        select count(*) from (
                            select o.stop_id
                            from ontime_events o
                            where o.service_date between :from and :to and o.route_id = :id
                            group by o.stop_id
                            having count(*) < :minArrivals) as thin
                        """)
                .param("id", routeId).param("from", from).param("to", to)
                .param("minArrivals", minArrivals)
                .query(Integer.class).single();
    }

    /** One stop's reliability broken down by the routes calling there. */
    public List<RouteRow> stopByRoute(String stopId, LocalDate from, LocalDate to) {
        return jdbcClient.sql("""
                        with named as (%s)
                        select o.route_id,
                               n.route_short_name,
                               count(*) as arrivals,
                               count(*) filter (where o.on_time) as on_time,
                               percentile_disc(0.5) within group (order by o.delay_seconds)
                                   as median_delay
                        from ontime_events o
                        left join named n on n.route_id = o.route_id
                        where o.service_date between :from and :to and o.stop_id = :id
                        group by o.route_id, n.route_short_name
                        order by count(*) desc
                        """.formatted(LATEST_ROUTES))
                .param("id", stopId).param("from", from).param("to", to)
                .query((rs, rowNum) -> new RouteRow(
                        rs.getString("route_id"),
                        rs.getString("route_short_name"),
                        rs.getInt("arrivals"),
                        Share.of(rs.getInt("on_time"), rs.getInt("arrivals")),
                        rs.getObject("median_delay", Integer.class)))
                .list();
    }

    /**
     * A route as the schedule knows it, regardless of whether it was ever observed.
     *
     * <p>This is what separates "no such route" from "this route ran and we saw
     * nothing". The first is a 404; the second is a real answer about a gap in the
     * data, and must not be dressed up as a missing page.
     */
    public Optional<Route> route(String routeId) {
        return jdbcClient.sql("""
                        with named as (%s)
                        select route_id, route_short_name, route_long_name
                        from named where route_id = :id
                        """.formatted(LATEST_ROUTES))
                .param("id", routeId)
                .query((rs, rowNum) -> new Route(
                        rs.getString("route_id"),
                        rs.getString("route_short_name"),
                        rs.getString("route_long_name")))
                .optional();
    }

    /** A stop as the schedule knows it; empty means no such stop, not an unnamed one. */
    public Optional<Stop> stop(String stopId) {
        return jdbcClient.sql("""
                        with named as (%s)
                        select stop_id, stop_name from named where stop_id = :id
                        """.formatted(LATEST_STOPS))
                .param("id", stopId)
                .query((rs, rowNum) -> new Stop(rs.getString("stop_id"), rs.getString("stop_name")))
                .optional();
    }

    /**
     * The project's own collection record, per day.
     *
     * <p>A full outer join, because the two halves fail independently: a day can have
     * snapshots and no derived arrivals if detection has not been run over it, and —
     * once the archive is replayed — arrivals exist only where snapshots do. Showing
     * either side alone would hide one of those failures.
     *
     * <p>Snapshots are counted as distinct {@code first_feed_timestamp} values. The
     * collector stores one feed timestamp per snapshot and skips snapshots whose
     * contents have not changed, so this counts snapshots that reached the database —
     * which is the number every other figure on the site depends on.
     */
    public List<CoverageDay> coverage(int expectedPerDay) {
        return jdbcClient.sql("""
                        with captured as (
                            select (vehicle_timestamp at time zone 'America/Halifax')::date as day,
                                   count(distinct first_feed_timestamp) as snapshots
                            from raw_vehicle_positions
                            group by 1),
                        derived as (
                            select service_date as day, count(*) as arrivals
                            from arrival_events
                            group by 1)
                        select coalesce(c.day, d.day) as day,
                               coalesce(c.snapshots, 0) as snapshots,
                               coalesce(d.arrivals, 0) as arrivals
                        from captured c
                        full join derived d on d.day = c.day
                        order by 1
                        """)
                .query((rs, rowNum) -> {
                    int captured = rs.getInt("snapshots");
                    return new CoverageDay(
                            rs.getObject("day", LocalDate.class),
                            captured,
                            expectedPerDay,
                            Share.of(captured, expectedPerDay),
                            rs.getInt("arrivals"));
                })
                .list();
    }

    /**
     * One slice's aggregate figures.
     *
     * @param medianDelaySeconds null when nothing was observed, since a median of no
     *                           delays is not zero
     */
    public record Totals(int arrivals, int onTime,
                         Integer medianDelaySeconds, Integer p90DelaySeconds) {
    }
}
