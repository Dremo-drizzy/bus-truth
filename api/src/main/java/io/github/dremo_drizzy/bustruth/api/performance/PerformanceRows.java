package io.github.dremo_drizzy.bustruth.api.performance;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;

/**
 * The response shapes for the performance endpoints, together because they are one
 * vocabulary: a route, a stop, an hour and a day all answer the same question about
 * different slices.
 *
 * <p>Every one of them reports a {@link Share} rather than a loose percentage, and
 * every one reports the delay percentiles beside it. The percentiles matter because
 * "72% on time" and "72% on time, median delay four minutes" describe different
 * services — the window is five minutes wide, so a route can be mostly on time and
 * reliably slow at once.
 */
public final class PerformanceRows {

    private PerformanceRows() {
    }

    /**
     * A route for a dropdown.
     *
     * <p>Names come from the most recently loaded publication of the schedule, which
     * is the right label to show a passenger today. The on-time figures elsewhere were
     * computed against the publication in force on each service date, which is a
     * different question (ADR-015).
     */
    public record Route(
            @JsonProperty("route_id") String routeId,
            @JsonProperty("route_short_name") String routeShortName,
            @JsonProperty("route_long_name") String routeLongName) {
    }

    /**
     * A stop as the schedule knows it, whether or not it was ever observed.
     *
     * <p>Its own type rather than a bare name string, because a stop that exists with
     * no name and a stop that does not exist are different answers, and an
     * {@code Optional<String>} cannot tell them apart.
     */
    public record Stop(
            @JsonProperty("stop_id") String stopId,
            @JsonProperty("stop_name") String stopName) {
    }

    /**
     * One row of the leaderboard.
     *
     * @param rankable whether this route had enough arrivals to take a position in the
     *                 ranking. False rows are still returned — a route observed once is
     *                 a fact about this project's coverage — but they sort last, because
     *                 a table sorted on share alone ranks routes by how little was seen
     *                 of them: on 9 October that put 100% of two arrivals at the top
     */
    public record RoutePerformance(
            @JsonProperty("route_id") String routeId,
            @JsonProperty("route_short_name") String routeShortName,
            @JsonProperty("route_long_name") String routeLongName,
            @JsonProperty("trips_observed") int tripsObserved,
            @JsonProperty("arrivals") int arrivals,
            @JsonProperty("on_time") Share onTime,
            @JsonProperty("median_delay_seconds") Integer medianDelaySeconds,
            @JsonProperty("p90_delay_seconds") Integer p90DelaySeconds,
            @JsonProperty("rankable") boolean rankable) {
    }

    /** One hour of the day, aggregated across the range, in America/Halifax. */
    public record HourOfDay(
            @JsonProperty("hour") int hour,
            @JsonProperty("arrivals") int arrivals,
            @JsonProperty("on_time") Share onTime,
            @JsonProperty("median_delay_seconds") Integer medianDelaySeconds) {
    }

    /** One service date. */
    public record Day(
            @JsonProperty("service_date") LocalDate serviceDate,
            @JsonProperty("arrivals") int arrivals,
            @JsonProperty("on_time") Share onTime,
            @JsonProperty("median_delay_seconds") Integer medianDelaySeconds) {
    }

    /** One stop, within a route or within a stop's own breakdown. */
    public record StopRow(
            @JsonProperty("stop_id") String stopId,
            @JsonProperty("stop_name") String stopName,
            @JsonProperty("arrivals") int arrivals,
            @JsonProperty("on_time") Share onTime,
            @JsonProperty("median_delay_seconds") Integer medianDelaySeconds) {
    }

    /** One route, within a stop's breakdown. */
    public record RouteRow(
            @JsonProperty("route_id") String routeId,
            @JsonProperty("route_short_name") String routeShortName,
            @JsonProperty("arrivals") int arrivals,
            @JsonProperty("on_time") Share onTime,
            @JsonProperty("median_delay_seconds") Integer medianDelaySeconds) {
    }

    /**
     * Everything the route detail page draws.
     *
     * @param worstStops          the ten worst by on-time share, among stops with at
     *                            least {@code minArrivalsApplied} arrivals
     * @param stopsBelowThreshold how many stops were left out of that ranking for
     *                            having too few arrivals to rank. Reported so the page
     *                            can say so, rather than presenting ten stops as if
     *                            they were the whole route
     */
    public record RouteDetail(
            @JsonProperty("route_id") String routeId,
            @JsonProperty("route_short_name") String routeShortName,
            @JsonProperty("route_long_name") String routeLongName,
            @JsonProperty("arrivals") int arrivals,
            @JsonProperty("on_time") Share onTime,
            @JsonProperty("median_delay_seconds") Integer medianDelaySeconds,
            @JsonProperty("p90_delay_seconds") Integer p90DelaySeconds,
            @JsonProperty("by_hour") List<HourOfDay> byHour,
            @JsonProperty("by_day") List<Day> byDay,
            @JsonProperty("worst_stops") List<StopRow> worstStops,
            @JsonProperty("min_arrivals_applied") int minArrivalsApplied,
            @JsonProperty("stops_below_threshold") int stopsBelowThreshold) {
    }

    /**
     * The whole network for a range, with the days it averages.
     *
     * <p>The days are not decoration. On the published week the spread between the
     * best and worst day is 21 points — wider than the 17.5-point gap between the two
     * candidate arrival definitions and wider still than the 8.7-point margin of error
     * — so a single figure for a range, standing alone, implies a precision the data
     * does not have. Any page showing {@code onTime} shows {@code byDay} beside it.
     *
     * @param from the first service date included, so no figure travels without its
     *             period
     */
    public record NetworkSummary(
            @JsonProperty("from") LocalDate from,
            @JsonProperty("to") LocalDate to,
            @JsonProperty("arrivals") int arrivals,
            @JsonProperty("on_time") Share onTime,
            @JsonProperty("median_delay_seconds") Integer medianDelaySeconds,
            @JsonProperty("p90_delay_seconds") Integer p90DelaySeconds,
            @JsonProperty("by_day") List<Day> byDay,
            @JsonProperty("by_hour") List<HourOfDay> byHour) {
    }

    /** Everything the stop page draws. */
    public record StopReliability(
            @JsonProperty("stop_id") String stopId,
            @JsonProperty("stop_name") String stopName,
            @JsonProperty("arrivals") int arrivals,
            @JsonProperty("on_time") Share onTime,
            @JsonProperty("median_delay_seconds") Integer medianDelaySeconds,
            @JsonProperty("p90_delay_seconds") Integer p90DelaySeconds,
            @JsonProperty("by_route") List<RouteRow> byRoute) {
    }

    /**
     * One day of the project's own collection record.
     *
     * <p>Not decoration. Every figure elsewhere in this API is conditional on the
     * collector having been running, and this is the only endpoint that says when it
     * was not.
     *
     * @param snapshotsCaptured distinct feed snapshots that reached the database
     * @param snapshotsExpected how many a day of uninterrupted 20-second polling would
     *                          produce
     * @param coverage          captured as a share of expected
     * @param arrivalsDerived   arrivals derived for that service date
     */
    public record CoverageDay(
            @JsonProperty("service_date") LocalDate serviceDate,
            @JsonProperty("snapshots_captured") int snapshotsCaptured,
            @JsonProperty("snapshots_expected") int snapshotsExpected,
            @JsonProperty("coverage") Share coverage,
            @JsonProperty("arrivals_derived") int arrivalsDerived) {
    }
}
