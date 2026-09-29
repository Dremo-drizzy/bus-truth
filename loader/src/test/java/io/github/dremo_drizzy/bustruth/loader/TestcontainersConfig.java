package io.github.dremo_drizzy.bustruth.loader;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A real PostgreSQL 18 for any test that needs the application context.
 *
 * <p>The same image as infra/docker-compose.yml, so tests and local development agree
 * on the engine — and Flyway applies the real migration into it, so the constraint the
 * tests lean on is the one that ships.
 *
 * <p>{@code @ServiceConnection} points spring.datasource at the container, replacing
 * whatever application.yml says.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:18-alpine");
    }
}
