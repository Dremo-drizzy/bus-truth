package io.github.dremo_drizzy.bustruth.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ApiApplication {

    public static void main(String[] args) {
        // Before the context starts, because the datasource is built from these
        // properties. Managed hosts give one DATABASE_URL and JDBC cannot use it; see
        // DatabaseUrl for why the host cannot simply be wired in the Blueprint instead.
        // No DATABASE_URL means local development, where nothing here fires.
        DatabaseUrl.applyIfPresent(System.getenv("DATABASE_URL"));
        SpringApplication.run(ApiApplication.class, args);
    }
}
