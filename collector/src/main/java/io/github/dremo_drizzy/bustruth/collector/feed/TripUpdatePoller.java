package io.github.dremo_drizzy.bustruth.collector.feed;

import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import io.github.dremo_drizzy.bustruth.collector.archive.RawArchiver;
import io.github.dremo_drizzy.bustruth.collector.config.CollectorProperties;
import io.github.dremo_drizzy.bustruth.collector.mapper.TripUpdateMapper;
import io.github.dremo_drizzy.bustruth.common.TripUpdate;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Polls the trip updates feed. */
@Component
public class TripUpdatePoller extends FeedPoller<TripUpdate> {

    private final CollectorProperties.Feed feed;
    private final TripUpdateMapper mapper;

    public TripUpdatePoller(HttpClient httpClient,
                            RawArchiver archiver,
                            KafkaTemplate<String, Object> kafkaTemplate,
                            CollectorProperties properties,
                            TripUpdateMapper mapper) {
        super(httpClient, archiver, kafkaTemplate, properties.httpTimeout());
        this.feed = properties.tripUpdates();
        this.mapper = mapper;
    }

    @Scheduled(fixedRateString = "${bustruth.collector.poll-interval}")
    public void poll() {
        pollOnce();
    }

    @Override
    protected String feedName() {
        return "trip-updates";
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
    protected List<TripUpdate> map(FeedMessage message) {
        return mapper.map(message);
    }

    /** Per ADR-003: one trip's predictions stay ordered by keying on the trip. */
    @Override
    protected String keyOf(TripUpdate record) {
        return record.tripId();
    }
}
