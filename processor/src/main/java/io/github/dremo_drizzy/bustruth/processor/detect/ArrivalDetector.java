package io.github.dremo_drizzy.bustruth.processor.detect;

import io.github.dremo_drizzy.bustruth.common.ArrivalEvent;
import io.github.dremo_drizzy.bustruth.common.VehiclePosition;
import java.util.List;

/**
 * Turns one vehicle's trail of positions into the arrivals it implies.
 *
 * <p>An interface with a single implementation, on purpose: it names the seam where a
 * different rule would go — one using {@code currentStatus}, say, or stop geometry —
 * and it keeps the rule testable without a database or a framework.
 */
public interface ArrivalDetector {

    /**
     * @param trail positions for one vehicle on one trip, in any order; the detector
     *              sorts them. A trail spanning more than one trip is not this
     *              method's business — the caller groups first.
     */
    List<ArrivalEvent> detect(List<VehiclePosition> trail);

    /** What the last call derived, skipped and refused. */
    DetectionCounts counts();
}
