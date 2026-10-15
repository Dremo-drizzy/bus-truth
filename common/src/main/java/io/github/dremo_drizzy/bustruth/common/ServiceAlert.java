package io.github.dremo_drizzy.bustruth.common;

import java.time.Instant;
import java.util.List;

/**
 * A disruption the agency is announcing: a closed stop, a detour, a cancellation.
 *
 * <p>The feed carries every text as a list of translations. Halifax publishes only
 * English, so the mapper takes the English translation and falls back to the first
 * one present, rather than modelling a language map nothing would read.
 *
 * <p>The two nested records exist because an alert genuinely repeats these: one
 * alert applies to several route/stop pairs and can be active over several windows.
 * Flattening them would lose which route goes with which stop.
 *
 * @param alertId          the feed's entity id for the alert
 * @param cause            why, e.g. CONSTRUCTION
 * @param effect           what it does to service, e.g. DETOUR
 * @param severityLevel    INFO, WARNING or SEVERE
 * @param headerText       the short title
 * @param descriptionText  the long text
 * @param causeDetail      the agency's own wording for the cause
 * @param effectDetail     the agency's own wording for the effect
 * @param activePeriods    when the alert applies; empty means always
 * @param informedEntities what it applies to
 * @param feedTimestamp    when the feed was generated
 */
public record ServiceAlert(
        String alertId,
        String cause,
        String effect,
        String severityLevel,
        String headerText,
        String descriptionText,
        String causeDetail,
        String effectDetail,
        List<ActivePeriod> activePeriods,
        List<InformedEntity> informedEntities,
        Instant feedTimestamp) {

    public ServiceAlert {
        activePeriods = activePeriods == null ? List.of() : List.copyOf(activePeriods);
        informedEntities = informedEntities == null ? List.of() : List.copyOf(informedEntities);
    }

    /**
     * A window during which the alert applies. Either end may be null, meaning open.
     * Halifax often sends an end of 32503694400 (the year 3000) to mean "no end";
     * that sentinel is kept as sent rather than reinterpreted, because guessing which
     * far-future values are sentinels is how real end dates get thrown away.
     */
    public record ActivePeriod(Instant start, Instant end) {
    }

    /** What the alert applies to. Any field may be null; at least one is set. */
    public record InformedEntity(String agencyId, String routeId, String stopId, String tripId) {
    }
}
