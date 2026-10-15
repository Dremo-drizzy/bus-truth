package io.github.dremo_drizzy.bustruth.collector;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Proves the application context starts: every bean can be created and wired.
 * The empty test body is intentional — if startup fails, the test fails.
 */
@SpringBootTest(properties = {
        // A build should not reach out to the agency's servers.
        "bustruth.collector.polling-enabled=false",
        // application.yml gives the password no default on purpose, and
        // @ServiceConnection replaces the datasource anyway, but the placeholder
        // still has to resolve for the context to build.
        "POSTGRES_PASSWORD=unused"
})
@Import(TestcontainersConfig.class)
class CollectorApplicationTests {

    @Test
    void contextLoads() {
    }
}
