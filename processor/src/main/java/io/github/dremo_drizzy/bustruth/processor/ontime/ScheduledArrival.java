package io.github.dremo_drizzy.bustruth.processor.ontime;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A derived arrival with whatever the schedule had to say about it.
 *
 * <p>The schedule fields are null when nothing matched. The query returns those rows
 * on purpose — counting what failed to match is the point, and an inner join would
 * report a flattering match rate of 100% by construction.
 *
 * @param stopSequence          the live feed's number for this stop
 * @param scheduledStopSequence the schedule's number for the same stop, null when
 *                              unmatched. Routinely different from the feed's
 *                              (ADR-015)
 * @param scheduledOccurrences  how many times the published trip calls at this stop.
 *                              More than one means the sequence had to break the tie
 */
public record ScheduledArrival(
        String tripId,
        String stopId,
        int stopSequence,
        LocalDate serviceDate,
        Instant observedArrivalAt,
        String arrivalConfidence,
        String scheduledTimeText,
        Integer scheduledStopSequence,
        int scheduledOccurrences,
        String routeId) {

    public boolean matched() {
        return scheduledTimeText != null && !scheduledTimeText.isBlank()
                && scheduledStopSequence != null;
    }

    public boolean ambiguous() {
        return scheduledOccurrences > 1;
    }
}
