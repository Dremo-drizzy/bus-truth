package io.github.dremo_drizzy.bustruth.collector.feed;

import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import io.github.dremo_drizzy.bustruth.collector.archive.RawArchiver;
import io.github.dremo_drizzy.bustruth.collector.config.CollectorProperties;
import io.github.dremo_drizzy.bustruth.collector.mapper.ServiceAlertMapper;
import io.github.dremo_drizzy.bustruth.common.ServiceAlert;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Polls the service alerts feed. */
@Component
public class ServiceAlertPoller extends FeedPoller<ServiceAlert> {

    private final CollectorProperties.Feed feed;
    private final ServiceAlertMapper mapper;

    public ServiceAlertPoller(HttpClient httpClient,
                              RawArchiver archiver,
                              KafkaTemplate<String, Object> kafkaTemplate,
                              CollectorProperties properties,
                              ServiceAlertMapper mapper) {
        super(httpClient, archiver, kafkaTemplate, properties.httpTimeout());
        this.feed = properties.alerts();
        this.mapper = mapper;
    }

    @Scheduled(fixedRateString = "${bustruth.collector.poll-interval}")
    public void poll() {
        pollOnce();
    }

    @Override
    protected String feedName() {
        return "alerts";
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
    protected List<ServiceAlert> map(FeedMessage message) {
        return mapper.map(message);
    }

    /**
     * No key. Alerts have no entity whose ordering matters, so Kafka spreads them
     * across partitions rather than forcing them all down one.
     */
    @Override
    protected String keyOf(ServiceAlert record) {
        return null;
    }
}
