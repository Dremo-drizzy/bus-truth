package io.github.dremo_drizzy.bustruth.collector.feed;

import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import io.github.dremo_drizzy.bustruth.collector.archive.RawArchiver;
import io.github.dremo_drizzy.bustruth.collector.config.CollectorProperties;
import io.github.dremo_drizzy.bustruth.collector.mapper.ServiceAlertMapper;
import io.github.dremo_drizzy.bustruth.common.ServiceAlert;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Polls the service alerts feed. */
@Component
public class ServiceAlertPoller extends FeedPoller<ServiceAlert> {

    private final CollectorProperties.Feed feed;
    private final ServiceAlertMapper mapper;

    public ServiceAlertPoller(HttpClient httpClient,
                              RawArchiver archiver,
                                CollectorProperties properties,
                              ServiceAlertMapper mapper) {
        super(httpClient, archiver, properties.httpTimeout());
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
    protected List<ServiceAlert> map(FeedMessage message) {
        return mapper.map(message);
    }

    /**
     * Deliberately does nothing.
     *
     * <p>Alerts are archived and nothing queries them. The archive costs almost nothing
     * (content changed on 0% of 180 consecutive polls) and keeps the option open.
     */
    @Override
    protected void store(List<ServiceAlert> records) {
        // Archived by FeedPoller before this point; nothing reads a database row.
    }
}
