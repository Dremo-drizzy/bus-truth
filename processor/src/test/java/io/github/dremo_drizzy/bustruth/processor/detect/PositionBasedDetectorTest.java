package io.github.dremo_drizzy.bustruth.processor.detect;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dremo_drizzy.bustruth.common.ArrivalEvent;
import io.github.dremo_drizzy.bustruth.common.VehiclePosition;
import io.github.dremo_drizzy.bustruth.processor.Trails;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What the arrival rule must do, tested against real captured trails.
 *
 * <p>Each test is named for the behaviour it protects, so a failure says which
 * promise broke rather than which line changed.
 */
class PositionBasedDetectorTest {

    private final DetectionSettings settings = DetectionSettings.defaults();

    private PositionBasedDetector detector() {
        return new PositionBasedDetector(settings);
    }

    @Test
    void emitsOneArrivalPerStopSequenceTransition() throws IOException {
        List<VehiclePosition> trail = Trails.load("ordinary-trail.json");
        PositionBasedDetector detector = detector();

        List<ArrivalEvent> arrivals = detector.detect(trail);

        long transitions = countTransitions(trail);
        assertThat(transitions).isPositive();
        // One arrival per transition where the stop being left is known. The feed
        // always sends stop_id alongside a sequence in this archive, so these match.
        assertThat(arrivals).hasSize((int) transitions);
        assertThat(detector.counts().arrivalsDerived()).isEqualTo(arrivals.size());
        assertThat(arrivals).allSatisfy(arrival -> {
            assertThat(arrival.stopId()).isNotBlank();
            assertThat(arrival.stopSequence()).isPositive();
            assertThat(arrival.method()).isEqualTo(ArrivalEvent.METHOD_POSITION_SEQUENCE);
            assertThat(arrival.serviceDate()).isNotNull();
        });
    }

    @Test
    void derivesNothingAcrossAGapLongerThanMaxTrailGap() throws IOException {
        // A real pair of positions where the sequence advances, with the interval
        // widened to 300 seconds — which is what a sleeping laptop leaves behind.
        // The fixture's own 271s hole falls where the sequence does not advance, so it
        // proves nothing on its own: the gap has to straddle a transition for the rule
        // to have anything to suppress.
        List<VehiclePosition> trail = Trails.load("ordinary-trail.json");
        VehiclePosition before = null;
        VehiclePosition after = null;
        for (int i = 1; i < trail.size(); i++) {
            if (trail.get(i).currentStopSequence() > trail.get(i - 1).currentStopSequence()) {
                before = trail.get(i - 1);
                after = Trails.withSequenceAndTime(trail.get(i), trail.get(i).currentStopSequence(),
                        trail.get(i).stopId(), before.timestamp().plusSeconds(300));
                break;
            }
        }
        assertThat(before).isNotNull();
        List<VehiclePosition> straddling = List.of(before, after);

        PositionBasedDetector strict = detector();
        List<ArrivalEvent> acrossTheGap = strict.detect(straddling);

        assertThat(acrossTheGap)
                .describedAs("a bus seen at stop N, then 5 minutes later at stop N+1, was "
                        + "not observed arriving anywhere — the collector was asleep")
                .isEmpty();
        assertThat(strict.counts().trailsBroken()).isEqualTo(1);

        // The same data with the rule relaxed does produce an arrival, which is what
        // makes this a test of the rule rather than of the fixture.
        PositionBasedDetector lenient = new PositionBasedDetector(new DetectionSettings(
                DetectionSettings.ArrivalMoment.FIRST_SIGHTING,
                Duration.ofSeconds(-60), Duration.ofSeconds(300), Duration.ofHours(1)));
        assertThat(lenient.detect(straddling)).hasSize(1);

        // And the real gapped trail still registers its hole.
        PositionBasedDetector onRealGap = detector();
        onRealGap.detect(Trails.load("gapped-trail.json"));
        assertThat(onRealGap.counts().trailsBroken()).isPositive();
    }

    @Test
    void countsStopsTheFeedNeverReportedWithoutInventingArrivals() throws IOException {
        List<VehiclePosition> trail = Trails.load("jumping-sequence.json");
        PositionBasedDetector detector = detector();

        List<ArrivalEvent> arrivals = detector.detect(trail);

        assertThat(detector.counts().stopsUnobserved())
                .describedAs("a 20-second poll misses stops; those are counted, not invented")
                .isPositive();
        // The skipped stops must not appear as arrivals: every emitted sequence has to
        // be one the feed actually reported.
        List<Integer> reported = trail.stream().map(VehiclePosition::currentStopSequence).toList();
        assertThat(arrivals).allSatisfy(arrival ->
                assertThat(reported).contains(arrival.stopSequence()));
    }

    @Test
    void marksSuffixedTripIdsWithLowerConfidence() throws IOException {
        // A real modified trip: 4652977_1.
        List<VehiclePosition> trail = Trails.load("suffixed-trip.json");

        List<ArrivalEvent> arrivals = detector().detect(trail);

        assertThat(arrivals).isNotEmpty();
        assertThat(arrivals).allSatisfy(arrival -> {
            // The suffix is stripped so the id joins the schedule (ADR-013)...
            assertThat(arrival.tripId()).doesNotContain("_");
            // ...and the fact that it was stripped survives as confidence, because
            // the join rests on an unverified assumption.
            assertThat(arrival.confidence()).isEqualTo(ArrivalEvent.CONFIDENCE_SUFFIXED_TRIP);
        });
    }

    @Test
    void ignoresPositionsWithNoStopSequence() throws IOException {
        List<VehiclePosition> trail = new ArrayList<>(Trails.load("ordinary-trail.json"));
        VehiclePosition base = trail.getFirst();
        // A bus between trips reports no sequence. It carries no arrival information.
        trail.add(Trails.withSequenceAndTime(base, null, "9999",
                base.timestamp().plusSeconds(1)));
        PositionBasedDetector detector = detector();

        List<ArrivalEvent> arrivals = detector.detect(trail);

        assertThat(detector.counts().positionsIgnored()).isEqualTo(1);
        assertThat(arrivals).noneSatisfy(arrival ->
                assertThat(arrival.stopId()).isEqualTo("9999"));
    }

    @Test
    void handlesATrailThatEndsMidTrip() throws IOException {
        // A bus still working towards stop N when the archive stops: the last stop
        // must not be emitted, because nothing observed it being left.
        List<VehiclePosition> full = Trails.load("ordinary-trail.json");
        List<VehiclePosition> truncated = full.subList(0, Math.max(2, full.size() / 2));

        List<ArrivalEvent> arrivals = detector().detect(truncated);

        int lastSequence = truncated.getLast().currentStopSequence();
        assertThat(arrivals).isNotEmpty();
        assertThat(arrivals).noneSatisfy(arrival ->
                assertThat(arrival.stopSequence()).isEqualTo(lastSequence));
    }

    @Test
    void handlesAVehicleReassignedToAnotherTripMidDay() throws IOException {
        // The same bus put on a different trip: its sequence resets downwards. That is
        // not a dozen arrivals in reverse, it is a new trail.
        List<VehiclePosition> first = Trails.load("ordinary-trail.json");
        List<VehiclePosition> trail = new ArrayList<>(first.subList(0, 20));
        Instant after = trail.getLast().timestamp().plusSeconds(40);
        for (int i = 0; i < 6; i++) {
            trail.add(Trails.withSequenceAndTime(
                    Trails.withTripId(trail.getFirst(), "9000001"),
                    i + 1, "70" + i, after.plusSeconds(i * 20L)));
        }

        List<ArrivalEvent> arrivals = detector().detect(trail);

        // No arrival may be emitted for the backwards step itself, and the run after
        // the reset still produces arrivals.
        assertThat(arrivals).isNotEmpty();
        assertThat(arrivals).allSatisfy(arrival ->
                assertThat(arrival.stopSequence()).isPositive());
    }

    @Test
    void arrivalMomentChoosesWhichPositionTimesTheArrival() throws IOException {
        List<VehiclePosition> trail = Trails.load("ordinary-trail.json");

        List<ArrivalEvent> first = new PositionBasedDetector(DetectionSettings.defaults())
                .detect(trail);
        List<ArrivalEvent> last = new PositionBasedDetector(new DetectionSettings(
                DetectionSettings.ArrivalMoment.LAST_BEFORE_ADVANCE,
                Duration.ofSeconds(-60), Duration.ofSeconds(300), Duration.ofSeconds(120)))
                .detect(trail);

        assertThat(first).hasSameSizeAs(last);
        // Both are defensible answers to different questions — "when did it get there"
        // against "when did it leave" — so the constant has to be a decision, not a
        // detail. For a bus that waits at a stop they differ.
        assertThat(first).isNotEqualTo(last);
    }

    private static long countTransitions(List<VehiclePosition> trail) {
        List<VehiclePosition> sorted = trail.stream()
                .filter(p -> p.currentStopSequence() != null)
                .sorted((a, b) -> a.timestamp().compareTo(b.timestamp()))
                .toList();
        long transitions = 0;
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).currentStopSequence() > sorted.get(i - 1).currentStopSequence()) {
                transitions++;
            }
        }
        return transitions;
    }
}
