package io.github.dremo_drizzy.bustruth.api.performance;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A percentage that cannot be serialised without the counts it came from.
 *
 * <p>The rule is that every percentage this API reports carries its denominator,
 * because 100% of two arrivals is not a reliability figure and a frontend given only
 * the number has no way to know that. Making that a type rather than a convention
 * means a new endpoint cannot quietly break it: there is no way to return a bare
 * percentage from here.
 *
 * <p>{@code percent} is null, not zero, when nothing was observed. Zero percent is a
 * claim about the service; null says the question has no answer yet, and the two must
 * not render the same way.
 *
 * @param count   how many met the condition
 * @param of      how many were observed in total
 * @param percent count as a percentage of of, to one decimal place, or null when
 *                {@code of} is zero
 */
public record Share(
        @JsonProperty("count") int count,
        @JsonProperty("of") int of,
        @JsonProperty("percent") Double percent) {

    public static Share of(int count, int of) {
        return new Share(count, of, of == 0 ? null : Math.round(count * 1000.0 / of) / 10.0);
    }
}
