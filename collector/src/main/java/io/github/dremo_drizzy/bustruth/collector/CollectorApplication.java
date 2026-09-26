package io.github.dremo_drizzy.bustruth.collector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Polls the three Halifax GTFS-Realtime feeds and publishes them to Kafka.
 *
 * <p>{@code @EnableScheduling} switches on the @Scheduled methods in the pollers;
 * {@code @ConfigurationPropertiesScan} binds CollectorProperties without each
 * component having to register it.
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class CollectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(CollectorApplication.class, args);
    }
}
