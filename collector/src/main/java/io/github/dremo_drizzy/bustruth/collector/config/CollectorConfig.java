package io.github.dremo_drizzy.bustruth.collector.config;

import io.github.dremo_drizzy.bustruth.collector.archive.RawArchiver;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

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

    /**
     * A KafkaTemplate typed String-to-Object.
     *
     * <p>Boot auto-configures one typed {@code <Object, Object>}, which would let a
     * poller pass anything at all as the message key. Keys decide partitions, and a
     * non-String key would be serialised by the StringSerializer configured in
     * application.yml and fail at runtime. Declaring the type here makes the compiler
     * enforce what the serializer already assumes.
     *
     * <p>The producer settings still come from the spring.kafka.* keys in
     * application.yml — this only rebuilds the factory with tighter generics.
     */
    @Bean
    KafkaTemplate<String, Object> collectorKafkaTemplate(KafkaProperties kafkaProperties) {
        return new KafkaTemplate<>(
                new DefaultKafkaProducerFactory<>(kafkaProperties.buildProducerProperties()));
    }
}
