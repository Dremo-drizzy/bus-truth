package io.github.dremo_drizzy.bustruth.collector.feed;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dremo_drizzy.bustruth.collector.config.CollectorProperties;
import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Each feed can be switched off on its own, and the switch is declared where Spring
 * reads it.
 *
 * <p>This matters because the host and the laptop will not collect the same feeds. A
 * trip-update snapshot averages 202 kB against 3.8 kB for vehicle positions, so
 * archiving all three feeds on a 160 GB host fills it in roughly five months; trip
 * updates therefore stay on the laptop until the archive can be written to object
 * storage (ADR-018). Getting that wrong means either a full disk or a silent hole in
 * the one dataset that cannot be collected retrospectively.
 *
 * <p>These assertions pin the declaration rather than exercising the container, and
 * that is deliberate. Proving the condition by booting the context would mean starting
 * it with {@code polling-enabled=true}, and the project's own convention forbids that
 * in a test: the pollers would fetch live data from the agency and write rows
 * underneath the assertions. So the test checks that both keys are declared and
 * default to on, which is the part a future edit could quietly break.
 */
class PerFeedPollingTest {

    @ParameterizedTest(name = "{0} is gated on {1}")
    @CsvSource({
            "VehiclePositionPoller, bustruth.collector.vehicle-positions.enabled",
            "TripUpdatePoller, bustruth.collector.trip-updates.enabled",
            "ServiceAlertPoller, bustruth.collector.alerts.enabled",
    })
    @DisplayName("every poller answers to the global switch and to its own")
    void everyPollerHasBothSwitches(String pollerName, String perFeedKey) throws Exception {
        Class<?> poller = Class.forName(
                "io.github.dremo_drizzy.bustruth.collector.feed." + pollerName);

        ConditionalOnProperty condition = poller.getAnnotation(ConditionalOnProperty.class);

        assertThat(condition).as("%s is not conditional at all", pollerName).isNotNull();
        assertThat(condition.name())
                .as("%s must answer to both the global switch and its own", pollerName)
                .containsExactlyInAnyOrder("bustruth.collector.polling-enabled", perFeedKey);
        // Default-on: a deployment that says nothing collects everything, and only an
        // explicit false turns a feed off. The opposite default would mean a config
        // typo silently collecting nothing, which is the failure this project can
        // least afford — the archive cannot be backfilled from the agency later.
        assertThat(condition.matchIfMissing())
                .as("%s must default to collecting", pollerName)
                .isTrue();
    }

    @Test
    @DisplayName("a feed with no enabled flag is collected")
    void feedDefaultsToEnabled() {
        CollectorProperties.Feed unspecified =
                new CollectorProperties.Feed(URI.create("https://example.test/feed.pb"), null);

        assertThat(unspecified.isEnabled()).isTrue();
        assertThat(unspecified.enabled()).isTrue();
    }

    @Test
    @DisplayName("a feed switched off stays off")
    void feedCanBeDisabled() {
        CollectorProperties.Feed off =
                new CollectorProperties.Feed(URI.create("https://example.test/feed.pb"), false);

        assertThat(off.isEnabled()).isFalse();
    }
}
