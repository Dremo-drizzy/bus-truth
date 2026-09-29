package io.github.dremo_drizzy.bustruth.collector.feed;

import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import io.github.dremo_drizzy.bustruth.collector.archive.RawArchiver;
import io.github.dremo_drizzy.bustruth.collector.config.CollectorProperties;
import io.github.dremo_drizzy.bustruth.collector.mapper.VehiclePositionMapper;
import io.github.dremo_drizzy.bustruth.common.VehiclePosition;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Polls the vehicle positions feed. */
@Component
public class VehiclePositionPoller extends FeedPoller<VehiclePosition> {

    private final CollectorProperties.Feed feed;
    private final VehiclePositionMapper mapper;

    public VehiclePositionPoller(HttpClient httpClient,
                                 RawArchiver archiver,
                                 KafkaTemplate<String, Object> kafkaTemplate,
                                 CollectorProperties properties,
                                 VehiclePositionMapper mapper) {
        super(httpClient, archiver, kafkaTemplate, properties.httpTimeout());
        this.feed = properties.vehiclePositions();
        this.mapper = mapper;
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
    protected String topic() {
        return feed.topic();
    }

    @Override
    protected List<VehiclePosition> map(FeedMessage message) {
        return mapper.map(message);
    }

    /** Per ADR-003: one vehicle's messages stay ordered by keying on the vehicle. */
    @Override
    protected String keyOf(VehiclePosition record) {
        return record.vehicleId();
    }
}
