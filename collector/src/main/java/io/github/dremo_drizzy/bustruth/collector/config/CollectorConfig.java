package io.github.dremo_drizzy.bustruth.collector.config;

import io.github.dremo_drizzy.bustruth.collector.archive.RawArchiver;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Beans the collector builds from configuration rather than component scanning. */
@Configuration
public class CollectorConfig {

    /**
     * One shared JDK HttpClient for all three pollers — no HTTP library added. One
     * instance rather than one per poll, because it pools connections: the three
     * feeds reuse the TLS handshake to gtfs.halifax.ca instead of repeating it every
     * 20 seconds.
     */
    @Bean
    HttpClient feedHttpClient() {
        return HttpClient.newBuilder()
                // Covers establishing the connection; FeedPoller's per-request
                // timeout covers waiting for the response.
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Bean
    RawArchiver rawArchiver(CollectorProperties properties) {
        return new RawArchiver(properties.archiveDir());
    }

}
