package io.github.dremo_drizzy.bustruth.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Proves the application context starts: every bean can be created and wired.
 * The empty test body is intentional — if startup fails, the test fails.
 */
@SpringBootTest(properties = "POSTGRES_PASSWORD=unused")
@Import(ApiTestcontainers.class)
class ApiApplicationTests {

    @Test
    void contextLoads() {
    }
}
