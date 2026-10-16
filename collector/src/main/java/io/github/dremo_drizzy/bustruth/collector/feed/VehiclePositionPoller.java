package io.github.dremo_drizzy.bustruth.collector.feed;

import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import io.github.dremo_drizzy.bustruth.collector.archive.RawArchiver;
import io.github.dremo_drizzy.bustruth.collector.config.CollectorProperties;
import io.github.dremo_drizzy.bustruth.collector.mapper.VehiclePositionMapper;
import io.github.dremo_drizzy.bustruth.collector.store.VehiclePositionWriter;
import io.github.dremo_drizzy.bustruth.common.VehiclePosition;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Polls the vehicle positions feed and writes it to PostgreSQL. */
@Component
// Polling is on unless switched off. A test that boots this application does not
// want three schedulers fetching live data and writing rows underneath its
// assertions, and an operator may want the service up without it collecting.
// Two switches, both default-on, and either one off stops this poller.
// polling-enabled is the global one, off in every test that boots the context.
// vehicle-positions.enabled is per feed, because the host and the laptop do not collect the
// same feeds: archiving trip updates on the host would fill its disk in about
// five months, so that feed stays on the laptop (ADR-018). Listing both names in
// one annotation means every one of them must be absent or true.
@ConditionalOnProperty(
        name = {"bustruth.collector.polling-enabled",
                "bustruth.collector.vehicle-positions.enabled"},
        matchIfMissing = true)
public class VehiclePositionPoller extends FeedPoller<VehiclePosition> {

    private static final Logger log = LoggerFactory.getLogger(VehiclePositionPoller.class);

    private final CollectorProperties.Feed feed;
    private final VehiclePositionMapper mapper;
    private final VehiclePositionWriter writer;
    private final Counter attempted;
    private final Counter written;

    public VehiclePositionPoller(HttpClient httpClient,
                                 RawArchiver archiver,
                                 CollectorProperties properties,
                                 VehiclePositionMapper mapper,
                                 VehiclePositionWriter writer,
                                 MeterRegistry meterRegistry) {
        super(httpClient, archiver, properties.httpTimeout());
        this.feed = properties.vehiclePositions();
        this.mapper = mapper;
        this.writer = writer;
        // Two counters rather than one plus a ratio: a rate computed here could not be
        // re-aggregated over a different window, and the absorption rate is the whole
        // point of measuring this.
        this.attempted = Counter.builder("collector.positions.insert.attempted")
                .description("Insert statements issued for vehicle positions")
                .register(meterRegistry);
        this.written = Counter.builder("collector.positions.insert.written")
                .description("Inserts that actually created a row; the rest were duplicates")
                .register(meterRegistry);
    }

    /**
     * fixedRate, not fixedDelay: the interval is measured from the start of each run,
     * so the cadence holds instead of drifting by however long the work took — the
     * same fixed grid scripts/save_feeds.py uses. Spring will not run this method
     * concurrently with itself, so an overrun delays the next run rather than
     * stacking two.
     */
    @Scheduled(fixedRateString = "${bustruth.collector.poll-interval}")
    public void poll() {
        pollOnce();
    }

    @Override
    protected String feedName() {
        return "vehicle-positions";
    }

    @Override
    protected URI url() {
        return feed.url();
    }

    @Override
    protected List<VehiclePosition> map(FeedMessage message) {
        return mapper.map(message);
    }

    /**
     * Writes each position, letting the database drop the ones it already has.
     *
     * <p>The insert is idempotent on (vehicle_id, vehicle_timestamp), so a feed that
     * repeats an observation costs one statement and no row. That guarantee was
     * written for at-least-once Kafka delivery and still earns its place: a poll that
     * fails after some rows are written is simply retried by the next one.
     *
     * <p>The Kafka coordinates are null now — the columns were made nullable for
     * exactly this case, a row with no delivery behind it.
     */
    @Override
    protected void store(List<VehiclePosition> records) {
        int rowsWritten = 0;
        for (VehiclePosition record : records) {
            if (writer.insert(record, null, null)) {
                rowsWritten++;
            }
        }
        attempted.increment(records.size());
        written.increment(rowsWritten);

        // Attempted against written, every poll, so the duplicate-absorption rate is
        // measured rather than estimated. The numbers are also a health signal: 0%
        // absorbed means the content hash is letting unchanged snapshots through, and
        // 100% means the feed has stopped producing new observations while still
        // answering.
        int absorbed = records.size() - rowsWritten;
        log.info("positions: {} inserts attempted, {} rows written, {} duplicates absorbed ({}%)",
                records.size(), rowsWritten, absorbed,
                records.isEmpty() ? 0 : Math.round(absorbed * 100.0 / records.size()));
    }
}
