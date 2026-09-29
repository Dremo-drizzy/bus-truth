package io.github.dremo_drizzy.bustruth.loader;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Proves the application context starts: every bean can be created and wired.
 * The empty test body is intentional — if startup fails, the test fails.
 */
@SpringBootTest(properties = {
        // No broker in this test; the listener would sit retrying a connection.
        "spring.kafka.listener.auto-startup=false",
        // application.yml gives the password no default on purpose, and
        // @ServiceConnection replaces the datasource anyway, but the placeholder
        // still has to resolve for the context to build.
        "POSTGRES_PASSWORD=unused"
})
@Import(TestcontainersConfig.class)
class LoaderApplicationTests {

    @Test
    void contextLoads() {
    }
}
