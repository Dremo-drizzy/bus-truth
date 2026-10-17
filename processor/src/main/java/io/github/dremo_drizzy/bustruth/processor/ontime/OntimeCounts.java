package io.github.dremo_drizzy.bustruth.processor.ontime;

/**
 * What a punctuality run compared, and what it could not.
 *
 * <p>The match rate is the number that decides whether the on-time figures mean
 * anything. If only half the derived arrivals can be matched to a published time, then
 * "82% on time" describes the half that happened to match, and the half that did not
 * is exactly where unusual service hides. So the rate is reported with the counts it
 * came from, and no percentage leaves this class without them.
 *
 * @param arrivalsConsidered   derived arrivals in the range
 * @param matched              arrivals that found a published time for the same trip
 *                             and stop sequence in the schedule in force that day
 * @param unmatched            arrivals with no such row: a trip the zip does not
 *                             contain, or a stop sequence beyond the published trip
 * @param unusableScheduleTime matched rows whose published time could not be read as
 *                             a time at all; counted separately because a parse
 *                             failure is a bug in us, while an unmatched row is a
 *                             disagreement between two sources
 * @param implausibleDelay     matched rows whose delay exceeded half a day, which is
 *                             not a delay but the service date and the published time
 *                             disagreeing about which day the trip belongs to. Counted
 *                             and not stored: a bus is never twenty-four hours early,
 *                             and a row saying so would poison every average computed
 *                             over it
 * @param onTime               inside the window
 * @param early                earlier than the window allows
 * @param late                 later than the window allows
 * @param sequenceDisagreements matched rows where the schedule numbers this stop
 *                             differently from the live feed. Not an error and not
 *                             corrected: the two sources describe different versions
 *                             of the same trip, and the size of this count is why the
 *                             match is made on the stop rather than the number
 *                             (ADR-015)
 * @param ambiguousStopMatches matched rows where the published trip calls at the stop
 *                             more than once, so the drifting sequence number had to
 *                             break the tie. The weakest rows here
 * @param daysWithoutSchedule  service dates with no publication covering them, which
 *                             are skipped rather than compared against a schedule
 *                             that was not in force
 */
public record OntimeCounts(
        int arrivalsConsidered,
        int matched,
        int unmatched,
        int unusableScheduleTime,
        int implausibleDelay,
        int onTime,
        int early,
        int late,
        int sequenceDisagreements,
        int ambiguousStopMatches,
        int daysWithoutSchedule) {

    public static OntimeCounts zero() {
        return new OntimeCounts(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public OntimeCounts plus(OntimeCounts other) {
        return new OntimeCounts(
                arrivalsConsidered + other.arrivalsConsidered,
                matched + other.matched,
                unmatched + other.unmatched,
                unusableScheduleTime + other.unusableScheduleTime,
                implausibleDelay + other.implausibleDelay,
                onTime + other.onTime,
                early + other.early,
                late + other.late,
                sequenceDisagreements + other.sequenceDisagreements,
                ambiguousStopMatches + other.ambiguousStopMatches,
                daysWithoutSchedule + other.daysWithoutSchedule);
    }

    /** Matched as a share of arrivals considered, or 0 when nothing was considered. */
    public double matchRate() {
        return arrivalsConsidered == 0 ? 0 : (double) matched / arrivalsConsidered;
    }

    /** On time as a share of matched arrivals — never of all arrivals. */
    public double onTimeRate() {
        return matched == 0 ? 0 : (double) onTime / matched;
    }

    @Override
    public String toString() {
        return ("%,d arrivals, %,d matched (%.1f%% of %,d), %,d unmatched, "
                + "%,d on time (%.1f%% of %,d matched), %,d early, %,d late, "
                + "%,d numbered differently by the schedule, %,d ambiguous stop matches, "
                + "%,d implausible delays refused, %,d days without a schedule")
                .formatted(arrivalsConsidered, matched, matchRate() * 100, arrivalsConsidered,
                        unmatched, onTime, onTimeRate() * 100, matched, early, late,
                        sequenceDisagreements, ambiguousStopMatches, implausibleDelay,
                        daysWithoutSchedule);
    }
}
