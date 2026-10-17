package io.github.dremo_drizzy.bustruth.common;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One observation that a vehicle reached a stop, derived from its trail of positions.
 *
 * <p>Derived, not reported: the feed never says "arrived". It says which stop a bus is
 * approaching, and an arrival is inferred from that number changing. So every field
 * here is a conclusion, and {@code confidence} records how much the conclusion rests
 * on assumptions.
 *
 * @param tripId             the trip, with any modified-trip suffix stripped so it
 *                           joins the published schedule (ADR-013)
 * @param stopId             the stop reached
 * @param stopSequence       its position within the trip
 * @param serviceDate        the agency's service date, which is not the calendar date
 *                           of {@code observedArrivalAt}: a trip running at 00:30 in
 *                           Halifax belongs to the previous service day
 * @param observedArrivalAt  when the arrival is judged to have happened
 * @param method             how it was derived; "POSITION_SEQUENCE" is the only
 *                           implementation today, and naming it leaves room for a
 *                           second without rewriting the table
 * @param confidence         HIGH, SUFFIXED_TRIP when the trip id carried an _N suffix
 *                           and the schedule was reached through its base id, or
 *                           SPARSE_TRAIL when the positions either side of the
 *                           transition were far enough apart to blur the timing
 */
public record ArrivalEvent(
        String tripId,
        String stopId,
        int stopSequence,
        LocalDate serviceDate,
        Instant observedArrivalAt,
        String method,
        String confidence) {

    public static final String METHOD_POSITION_SEQUENCE = "POSITION_SEQUENCE";

    public static final String CONFIDENCE_HIGH = "HIGH";
    public static final String CONFIDENCE_SUFFIXED_TRIP = "SUFFIXED_TRIP";
    public static final String CONFIDENCE_SPARSE_TRAIL = "SPARSE_TRAIL";
}
