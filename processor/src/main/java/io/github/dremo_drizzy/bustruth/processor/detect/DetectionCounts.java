package io.github.dremo_drizzy.bustruth.processor.detect;

/**
 * What a detection run produced, and what it declined to produce.
 *
 * <p>The refusals matter as much as the arrivals. A run that derives 10,000 arrivals
 * while breaking 4,000 trails is describing a dataset with holes in it, and the only
 * honest way to publish the first number is alongside the second.
 *
 * @param arrivalsDerived  arrivals emitted
 * @param stopsUnobserved  stops the bus served that the feed never reported. Halifax
 *                         updates current_stop_sequence about 6.8 times across a
 *                         38.3-stop trip, so the sequence jumps well past N+1 and the
 *                         stops between are never mentioned. The bus called at them:
 *                         this is not missed service, and publishing it as such would
 *                         be a false claim about a transit agency. Not interpolated
 *                         either, because an arrival nobody observed is not an arrival
 * @param trailsBroken     places where consecutive positions were further apart than
 *                         maxTrailGap, so nothing was derived across the gap
 * @param positionsIgnored positions with no stop sequence, which carry no information
 *                         about arrival
 */
public record DetectionCounts(
        int arrivalsDerived,
        int stopsUnobserved,
        int trailsBroken,
        int positionsIgnored) {

    public static DetectionCounts zero() {
        return new DetectionCounts(0, 0, 0, 0);
    }

    public DetectionCounts plus(DetectionCounts other) {
        return new DetectionCounts(
                arrivalsDerived + other.arrivalsDerived,
                stopsUnobserved + other.stopsUnobserved,
                trailsBroken + other.trailsBroken,
                positionsIgnored + other.positionsIgnored);
    }

    @Override
    public String toString() {
        return "%,d arrivals derived, %,d stops unobserved, %,d trails broken, %,d positions ignored"
                .formatted(arrivalsDerived, stopsUnobserved, trailsBroken, positionsIgnored);
    }
}
