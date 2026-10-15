package io.github.dremo_drizzy.bustruth.api;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A real PostgreSQL 18 for any api test that needs the application context.
 *
 * <p>The api reads a table the loader owns, so the schema is created by the test that
 * needs it rather than by Flyway here.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ApiTestcontainers {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:18-alpine");
    }
}
