package io.github.dremo_drizzy.bustruth.processor.ontime;

import io.github.dremo_drizzy.bustruth.common.OntimeEvent;
import io.github.dremo_drizzy.bustruth.processor.detect.DetectionSettings;
import io.github.dremo_drizzy.bustruth.processor.store.OntimeEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sets each derived arrival against the time it was published for.
 *
 * <p>Re-runnable in the same way detection is, and for the same reason: the on-time
 * window is an editorial decision that will be argued about, and changing it must mean
 * re-running a range rather than patching rows. The range is cleared inside the
 * transaction before it is rebuilt.
 *
 * <p>Three refusals shape what this writes.
 *
 * <p><b>It will not compare against a schedule that was not in force.</b> A service
 * date with no publication covering it is skipped and counted, not quietly measured
 * against the newest zip on disk.
 *
 * <p><b>It will not store an unmatched arrival.</b> A row with a null delay sitting
 * next to rows with real delays is one careless average away from a false claim about
 * a transit agency. Unmatched arrivals are counted and reported instead, which is why
 * the match rate is published with every on-time figure.
 *
 * <p><b>It will not guess at a published time it cannot read.</b> A schedule value
 * that will not parse is counted separately from an unmatched one, because the first
 * is a bug here and the second is a disagreement between two sources.
 *
 * <p><b>And it will not reconcile the two sources' stop numbering.</b> The live feed
 * and the published zip number the stops of a trip differently on about a fifth of
 * arrivals, so the match is made on the stop itself and the disagreement is recorded
 * on every row (ADR-015). Choosing a winner would mean deciding which of two
 * publishers is right about their own trip, which is not a question this project is in
 * a position to answer.
 */
@Service
public class OntimeRun {

    private static final Logger log = LoggerFactory.getLogger(OntimeRun.class);

    private final OntimeEventRepository repository;
    private final DetectionSettings settings;

    public OntimeRun(OntimeEventRepository repository, DetectionSettings settings) {
        this.repository = repository;
        this.settings = settings;
    }

    @Transactional
    public OntimeCounts run(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to (" + to + ") is before from (" + from + ")");
        }

        int cleared = repository.deleteRange(from, to);
        log.info("cleared {} existing on-time rows for {} .. {}", cleared, from, to);

        OntimeCounts total = OntimeCounts.zero();

        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            String feedVersion = repository.feedVersionInForce(day).orElse(null);
            if (feedVersion == null) {
                // Loudly, because this is the one failure that would otherwise look
                // like a day on which no buses ran.
                log.warn("{}: no published schedule covers this date, skipping", day);
                total = total.plus(new OntimeCounts(0, 0, 0, 0, 0, 0, 0, 0, 0, 1));
                continue;
            }

            List<ScheduledArrival> arrivals = repository.arrivalsWithSchedule(day, feedVersion);
            List<OntimeEvent> events = new ArrayList<>();
            OntimeCounts dayCounts = compare(arrivals, feedVersion, events);

            int written = repository.insertAll(events);
            total = total.plus(dayCounts);

            log.info("{} against {}: {} — {} rows written",
                    day, feedVersion, dayCounts, written);
        }

        log.info("on-time run complete for {} .. {}: {}", from, to, total);
        return total;
    }

    /** Compares one day's arrivals, appending the matched ones to {@code events}. */
    private OntimeCounts compare(List<ScheduledArrival> arrivals, String feedVersion,
                                 List<OntimeEvent> events) {
        int matched = 0;
        int unmatched = 0;
        int unusable = 0;
        int onTime = 0;
        int early = 0;
        int late = 0;
        int sequenceDisagreements = 0;
        int ambiguous = 0;

        for (ScheduledArrival arrival : arrivals) {
            if (!arrival.matched()) {
                unmatched++;
                continue;
            }

            Instant scheduled = ScheduledTime.toInstant(
                    arrival.serviceDate(), arrival.scheduledTimeText());
            if (scheduled == null) {
                unusable++;
                continue;
            }

            Duration delay = Duration.between(scheduled, arrival.observedArrivalAt());
            boolean tooEarly = delay.compareTo(settings.onTimeEarly()) < 0;
            boolean tooLate = delay.compareTo(settings.onTimeLate()) > 0;

            matched++;
            if (tooEarly) {
                early++;
            } else if (tooLate) {
                late++;
            } else {
                onTime++;
            }

            // Recorded, not corrected. The feed and the zip describe different
            // versions of the same trip, and which of them "should" number a stop
            // fourth is not a question this project can answer.
            if (arrival.scheduledStopSequence() != arrival.stopSequence()) {
                sequenceDisagreements++;
            }
            if (arrival.ambiguous()) {
                ambiguous++;
            }

            events.add(new OntimeEvent(
                    arrival.tripId(),
                    arrival.stopId(),
                    arrival.stopSequence(),
                    arrival.serviceDate(),
                    feedVersion,
                    arrival.routeId(),
                    arrival.observedArrivalAt(),
                    scheduled,
                    arrival.scheduledTimeText(),
                    // Seconds, not milliseconds: the input is timestamped to the
                    // second, so anything finer would be false precision.
                    (int) delay.toSeconds(),
                    !tooEarly && !tooLate,
                    arrival.arrivalConfidence(),
                    arrival.scheduledStopSequence(),
                    arrival.ambiguous()));
        }

        return new OntimeCounts(arrivals.size(), matched, unmatched, unusable,
                onTime, early, late, sequenceDisagreements, ambiguous, 0);
    }
}
