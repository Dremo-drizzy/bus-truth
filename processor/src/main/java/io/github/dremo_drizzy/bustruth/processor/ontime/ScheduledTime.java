package io.github.dremo_drizzy.bustruth.processor.ontime;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Turns a published GTFS time into the instant it actually means.
 *
 * <p>This is the one piece of arithmetic in the project that is genuinely easy to get
 * wrong, and getting it wrong would shift every delay on two days of the year by an
 * hour — turning a punctual morning into a disaster and publishing it as a finding.
 *
 * <h2>GTFS times can exceed 24:00:00</h2>
 *
 * A trip leaving at 00:40 on the night of the 14th is published as {@code 24:40:00} on
 * service date 2026-10-14, because it belongs to the 14th's service day. {@code
 * 25:10:00} is equally legal. Postgres's {@code time} type rejects both, which is why
 * the column is text, and {@code LocalTime} rejects them too — so the value is read as
 * a <em>duration since the start of the service day</em>, never as a time of day.
 *
 * <h2>The service day starts at noon minus twelve hours, not at midnight</h2>
 *
 * This looks like pedantry and is not. The GTFS specification defines a service day's
 * times as elapsed time from <em>noon minus twelve hours</em> on that date, and on the
 * two days a year the clocks change, that is not midnight.
 *
 * <p>Halifax moved to ADT at 02:00 on 8 March 2026, so that date is 23 hours long. Its
 * midnight is 04:00 UTC; its noon minus twelve hours is 03:00 UTC. Take a bus
 * published for {@code 08:00:00} that day:
 *
 * <ul>
 *   <li>from midnight: 04:00 UTC + 8h = 12:00 UTC, which reads 09:00 locally — an hour
 *       after the time printed in the timetable, so every bus that morning would be
 *       recorded as an hour early
 *   <li>from noon minus twelve hours: 03:00 UTC + 8h = 11:00 UTC, which reads 08:00
 *       locally, which is what the timetable says
 * </ul>
 *
 * <p>The 25-hour day, 1 November 2026, is wrong by an hour in the other direction. On
 * the other 363 days the two bases are the same instant, which is exactly why the bug
 * would have survived testing and shown up as two inexplicable days in the published
 * data.
 *
 * <p>So: the base instant comes from local noon, and the published offset is then added
 * as elapsed time. Nothing here re-reads a wall clock mid-calculation, because the
 * schedule's offsets are already measured against that one fixed point.
 */
public final class ScheduledTime {

    public static final ZoneId HALIFAX = ZoneId.of("America/Halifax");

    /** Noon is the anchor because it never falls inside a clock change. */
    private static final Duration HALF_A_DAY = Duration.ofHours(12);

    private ScheduledTime() {
    }

    /**
     * @param serviceDate the agency's service date
     * @param gtfsTime    "HH:mm:ss", where HH may be 24 or more
     * @return the instant that time refers to, or null if the text is unusable
     */
    public static Instant toInstant(LocalDate serviceDate, String gtfsTime) {
        Duration sinceServiceDayStart = parse(gtfsTime);
        if (sinceServiceDayStart == null) {
            return null;
        }
        return startOfServiceDay(serviceDate).plus(sinceServiceDayStart);
    }

    /**
     * Noon minus twelve hours, local — the instant GTFS measures a service day from.
     *
     * <p>Noon is used rather than midnight because noon exists on every date in every
     * zone: a clock change at 02:00 can delete or duplicate midnight-adjacent local
     * times, and java.time then has to pick for us. Noon is never ambiguous, so
     * subtracting twelve elapsed hours from it is a single, unarguable instant.
     */
    static Instant startOfServiceDay(LocalDate serviceDate) {
        return serviceDate.atTime(12, 0).atZone(HALIFAX).toInstant().minus(HALF_A_DAY);
    }

    /** Reads "25:10:00" as 25 hours 10 minutes, which no time type will accept. */
    static Duration parse(String gtfsTime) {
        if (gtfsTime == null) {
            return null;
        }
        String[] parts = gtfsTime.trim().split(":");
        if (parts.length != 3) {
            return null;
        }
        try {
            return Duration.ofHours(Long.parseLong(parts[0]))
                    .plusMinutes(Long.parseLong(parts[1]))
                    .plusSeconds(Long.parseLong(parts[2]));
        } catch (NumberFormatException unusable) {
            return null;
        }
    }
}
