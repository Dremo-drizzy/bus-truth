package io.github.dremo_drizzy.bustruth.processor.detect;

/**
 * What a detection run produced, and what it declined to produce.
 *
 * <p>The refusals matter as much as the arrivals. A run that derives 10,000 arrivals
 * while breaking 4,000 trails is describing a dataset with holes in it, and the only
 * honest way to publish the first number is alongside the second.
 *
 * @param arrivalsDerived  arrivals emitted
 * @param stopsSkipped     stops the vehicle passed without being observed at them,
 *                         because its reported sequence jumped from N to beyond N+1.
 *                         Deliberately not interpolated: a stop nobody saw a bus at is
 *                         not an arrival, and inventing one would be inventing data
 * @param trailsBroken     places where consecutive positions were further apart than
 *                         maxTrailGap, so nothing was derived across the gap
 * @param positionsIgnored positions with no stop sequence, which carry no information
 *                         about arrival
 */
public record DetectionCounts(
        int arrivalsDerived,
        int stopsSkipped,
        int trailsBroken,
        int positionsIgnored) {

    public static DetectionCounts zero() {
        return new DetectionCounts(0, 0, 0, 0);
    }

    public DetectionCounts plus(DetectionCounts other) {
        return new DetectionCounts(
                arrivalsDerived + other.arrivalsDerived,
                stopsSkipped + other.stopsSkipped,
                trailsBroken + other.trailsBroken,
                positionsIgnored + other.positionsIgnored);
    }

    @Override
    public String toString() {
        return "%,d arrivals derived, %,d stops skipped, %,d trails broken, %,d positions ignored"
                .formatted(arrivalsDerived, stopsSkipped, trailsBroken, positionsIgnored);
    }
}
