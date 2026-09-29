package io.github.dremo_drizzy.bustruth.collector.config;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Everything the collector reads from configuration, bound from the
 * {@code bustruth.collector} section of application.yml.
 *
 * <p>A record, so the values are fixed once the application starts: config that can
 * change under a running poller is a source of confusing behaviour.
 *
 * @param archiveDir  where raw feed bytes are written. Points at data/dev-archive
 *                    while the collector is being built, deliberately NOT the
 *                    data/raw tree that scripts/save_feeds.py owns — two writers
 *                    with different skip rules would make that archive impossible
 *                    to reason about. Switch it when the collector replaces the
 *                    script.
 * @param httpTimeout how long to wait for one feed response
 * @param feeds       url and destination topic for each of the three feeds
 */
@ConfigurationProperties(prefix = "bustruth.collector")
public record CollectorProperties(
        Path archiveDir,
        Duration httpTimeout,
        Feed vehiclePositions,
        Feed tripUpdates,
        Feed alerts) {

    /**
     * One feed's source and destination.
     *
     * @param url   the GTFS-Realtime endpoint
     * @param topic the Kafka topic its records are published to
     */
    public record Feed(URI url, String topic) {
    }
}
