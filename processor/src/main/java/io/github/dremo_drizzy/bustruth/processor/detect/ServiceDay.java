package io.github.dremo_drizzy.bustruth.processor.detect;

import io.github.dremo_drizzy.bustruth.common.VehiclePosition;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Which service day an observation belongs to.
 *
 * <p><b>NEEDS DANIEL'S REVIEW</b> — part of the arrival rule, which the brief reserves
 * for him. Written by Claude Code; the measurements are real and reproducible.
 *
 * <p>A transit service day is not a calendar day. It starts in the morning and runs
 * past midnight, so a bus out at 00:16 is finishing the previous day's service, and the
 * schedule says so by publishing its time as {@code 24:16:00} rather than
 * {@code 00:16:00}.
 *
 * <h2>Why this is not simply the feed's start_date</h2>
 *
 * The feed carries a {@code start_date} per trip and it is present on every one of the
 * 1,205,076 positions collected for 4–10 October — none missing. The project treats it
 * as authoritative, and for the overwhelming majority of trips it is.
 *
 * <p>After midnight it is not. Measured: 2,660 arrivals came out with a delay beyond
 * minus twelve hours, which is not a delay but a disagreement about which day the trip
 * belongs to. Trip 4649655 is the clearest case — the feed labelled it
 * {@code start_date=20261004}, it was observed at 00:16 on 4 October, and its published
 * time for that stop is {@code 24:32:00}. A trip genuinely on the 4th's service day
 * would reach that stop at 00:32 on the <em>5th</em>. The observation belongs to the
 * 3rd's service day, and the feed's own label contradicts the agency's own schedule.
 *
 * <p>Re-resolving those 2,660 against the previous service day gives a median delay of
 * <b>+4 seconds</b>, with <b>2,659 of 2,660 inside half an hour</b> and no case in the
 * opposite direction. A mis-pairing would scatter; this lands on the minute. So the
 * previous service day is the right answer, and the feed's post-midnight
 * {@code start_date} is a calendar date rather than a service date.
 *
 * <h2>Why 04:00, and why that is measured rather than chosen</h2>
 *
 * The published schedule bounds the service day itself. Across every stop time in the
 * feed version in force, the earliest is <b>04:20:00</b> and the latest is
 * <b>26:01:00</b> — so a service day runs from about 04:20 to 02:01 the next morning,
 * and no trip on service date D has any scheduled stop before 04:20 on D. An
 * observation before 04:00 local therefore cannot belong to that calendar date's
 * service day, whatever the feed labels it. 04:00 sits in the twenty-minute gap below
 * the earliest scheduled stop, so the boundary cannot clip real early-morning service.
 *
 * <p>If Halifax ever publishes a stop before 04:00, this constant is wrong and the
 * {@code earliestScheduledStopIsAfterTheBoundary} test is what will say so.
 */
public final class ServiceDay {

    /** The service day is the agency's, and Halifax is not UTC. */
    public static final ZoneId HALIFAX = ZoneId.of("America/Halifax");

    /**
     * Before this local time, an observation belongs to the previous service day.
     *
     * <p>Measured from the schedule, not picked: the earliest scheduled stop in the
     * feed is 04:20 and the latest is 26:01.
     */
    public static final LocalTime SERVICE_DAY_START = LocalTime.of(4, 0);

    private ServiceDay() {
    }

    /**
     * The service day an observation belongs to.
     *
     * <p>The feed's {@code start_date} is trusted except in the one case it is
     * measurably wrong: a trip labelled with the same calendar date it is observed on,
     * observed before the service day could have started. Where the agency has already
     * labelled a post-midnight trip with the previous date — which it does for some —
     * that label disagrees with the clock and is kept, because it is the agency
     * asserting the service day rather than merely stamping today's date.
     */
    public static LocalDate of(VehiclePosition position) {
        ZonedDateTime local = position.timestamp().atZone(HALIFAX);
        LocalDate sent = parse(position.startDate());

        if (sent == null) {
            // No start_date to go on. Not reached by Halifax's feed today, which sends
            // one on every position, but a feed that stopped would otherwise silently
            // put every post-midnight arrival on the wrong day.
            return beforeServiceDayStarts(local) ? local.toLocalDate().minusDays(1)
                    : local.toLocalDate();
        }

        if (beforeServiceDayStarts(local) && sent.equals(local.toLocalDate())) {
            return sent.minusDays(1);
        }
        return sent;
    }

    private static boolean beforeServiceDayStarts(ZonedDateTime local) {
        return local.toLocalTime().isBefore(SERVICE_DAY_START);
    }

    /** The feed sends "yyyyMMdd"; anything else is treated as absent. */
    private static LocalDate parse(String startDate) {
        if (startDate == null || startDate.length() != 8) {
            return null;
        }
        try {
            return LocalDate.of(Integer.parseInt(startDate.substring(0, 4)),
                    Integer.parseInt(startDate.substring(4, 6)),
                    Integer.parseInt(startDate.substring(6, 8)));
        } catch (NumberFormatException notADate) {
            return null;
        }
    }
}
