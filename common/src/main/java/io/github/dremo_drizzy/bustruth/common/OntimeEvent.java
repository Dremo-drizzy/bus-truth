package io.github.dremo_drizzy.bustruth.common;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One observed arrival set against the time the agency published for it.
 *
 * <p>This is the project's central comparison: everything the site claims about
 * reliability is an aggregate of these rows. Two fields exist only to stop that
 * aggregate becoming a lie. {@code feedVersion} says which publication of the schedule
 * the promise came from, because a trip id means different things in different
 * publications. {@code arrivalConfidence} carries the weakness of the underlying
 * observation forward, because an on-time verdict about a blurred arrival is itself
 * blurred, and an average that forgets this reports inference as measurement.
 *
 * @param tripId             the trip, already schedule-joinable (ADR-013)
 * @param stopId             the stop the bus was observed at, and the key the match to
 *                           the schedule was made on
 * @param stopSequence       the live feed's position for that stop within the trip
 * @param serviceDate        the agency's service date
 * @param feedVersion        the publication of the schedule in force on that date
 * @param routeId            the route, from that publication's trips table
 * @param observedArrivalAt  when the bus was judged to have arrived
 * @param scheduledArrivalAt when it was published to arrive, resolved to an instant in
 *                           America/Halifax
 * @param scheduledTimeText  the schedule's own text, which may read "25:10:00"
 * @param delaySeconds       observed minus scheduled; negative is early
 * @param onTime             inside the published window, which is an editorial
 *                           decision and not a fact (ADR-014)
 * @param arrivalConfidence  the arrival's confidence, carried through unchanged
 * @param scheduledStopSequence the schedule's own position for this stop. It differs
 *                           from {@code stopSequence} on about a fifth of arrivals,
 *                           which is why the two sources are joined on the stop and
 *                           not on the number (ADR-015)
 * @param stopMatchAmbiguous whether the trip calls at this stop more than once, so the
 *                           occurrence nearest the feed's number was taken and the
 *                           match rests on evidence this project has measured as weak
 */
public record OntimeEvent(
        String tripId,
        String stopId,
        int stopSequence,
        LocalDate serviceDate,
        String feedVersion,
        String routeId,
        Instant observedArrivalAt,
        Instant scheduledArrivalAt,
        String scheduledTimeText,
        int delaySeconds,
        boolean onTime,
        String arrivalConfidence,
        int scheduledStopSequence,
        boolean stopMatchAmbiguous) {

    /**
     * How far the schedule's numbering has drifted from the feed's at this stop.
     *
     * <p>Zero on most arrivals and negative on the rest: the feed's number runs ahead
     * because its trip contains stops the published zip does not list. Published as a
     * data-quality figure rather than corrected away, because neither source is wrong
     * — they describe different versions of the same trip.
     */
    public int sequenceOffset() {
        return scheduledStopSequence - stopSequence;
    }
}
