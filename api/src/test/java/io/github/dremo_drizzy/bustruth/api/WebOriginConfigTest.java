package io.github.dremo_drizzy.bustruth.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The allowed origin has to be exactly scheme-and-host.
 *
 * <p>A browser compares it to the {@code Origin} header as a literal string, so a
 * trailing slash refuses every request — and the failure surfaces in the browser as a
 * CORS error, which reads as the API being down. Nothing anywhere says "trailing
 * slash". It is a one-character bug with a half-hour diagnosis, which is why it has a
 * test rather than a comment.
 */
class WebOriginConfigTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            // What Render's fromService actually injects: a bare hostname.
            "bus-truth-web.onrender.com, https://bus-truth-web.onrender.com",
            // A trailing slash, which is how this is usually typed by hand.
            "https://bus-truth-web.onrender.com/, https://bus-truth-web.onrender.com",
            "bus-truth-web.onrender.com/, https://bus-truth-web.onrender.com",
            "https://bus-truth-web.onrender.com///, https://bus-truth-web.onrender.com",
            // Already correct, and left alone.
            "https://bus-truth-web.onrender.com, https://bus-truth-web.onrender.com",
            // http on purpose, for a local or proxied deployment.
            "http://localhost:5173, http://localhost:5173",
            "  bus-truth-web.onrender.com  , https://bus-truth-web.onrender.com",
    })
    @DisplayName("normalises to scheme and host with no trailing slash")
    void normalises(String configured, String expected) {
        assertThat(WebOriginConfig.normalise(configured)).isEqualTo(expected);
    }
}
