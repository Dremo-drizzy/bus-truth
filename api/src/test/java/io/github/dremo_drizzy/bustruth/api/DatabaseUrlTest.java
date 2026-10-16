package io.github.dremo_drizzy.bustruth.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The one piece of translation between a managed host and Spring.
 *
 * <p>It gets a test because a mistake here does not fail loudly in a readable way: the
 * application starts, cannot reach the database, and the log reports a connection
 * refused that looks like a firewall rule. The parsing is three lines of string work
 * and all of it is somebody's deploy day.
 */
class DatabaseUrlTest {

    @Test
    @DisplayName("a managed connection string becomes a JDBC url with the credentials split out")
    void translatesAManagedUrl() {
        var translation = DatabaseUrl.translate(
                "postgres://bustruth:s3cret@dpg-abc123-a.virginia-postgres.render.com:5432/bustruth")
                .orElseThrow();

        assertThat(translation.jdbcUrl()).isEqualTo(
                "jdbc:postgresql://dpg-abc123-a.virginia-postgres.render.com:5432/bustruth");
        assertThat(translation.username()).isEqualTo("bustruth");
        assertThat(translation.password()).isEqualTo("s3cret");
    }

    @Test
    @DisplayName("a missing port means 5432, which is what managed hosts omit")
    void defaultsThePort() {
        var translation = DatabaseUrl.translate(
                "postgresql://user:pw@dpg-xyz-a/bustruth").orElseThrow();

        assertThat(translation.jdbcUrl()).isEqualTo("jdbc:postgresql://dpg-xyz-a:5432/bustruth");
    }

    @Test
    @DisplayName("the query string survives, because sslmode lives there")
    void keepsTheQueryString() {
        var translation = DatabaseUrl.translate(
                "postgres://user:pw@host:5432/db?sslmode=require").orElseThrow();

        // Dropping this turns a working URL into a refused connection that reads like a
        // network problem.
        assertThat(translation.jdbcUrl()).endsWith("/db?sslmode=require");
    }

    @Test
    @DisplayName("the password never appears in toString")
    void hidesThePassword() {
        var translation = DatabaseUrl.translate("postgres://user:hunter2@host/db").orElseThrow();

        assertThat(translation.toString()).doesNotContain("hunter2").contains("***");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "   ",
            "jdbc:postgresql://host:5432/db",   // already a JDBC url: not ours to touch
            "mysql://user:pw@host/db",          // another engine entirely
            "postgres://",                      // no host
            "not a url at all",
    })
    @DisplayName("anything else is left alone rather than half-applied")
    void ignoresWhatItCannotTranslate(String value) {
        assertThat(DatabaseUrl.translate(value)).isEmpty();
    }

    @Test
    void nullIsIgnored() {
        assertThat(DatabaseUrl.translate(null)).isEmpty();
    }

    @Test
    @DisplayName("applying it twice does not overwrite an explicit setting")
    void explicitSettingsWin() {
        System.setProperty("spring.datasource.url", "jdbc:postgresql://chosen:5432/by-hand");
        try {
            DatabaseUrl.applyIfPresent("postgres://user:pw@generated:5432/db");

            assertThat(System.getProperty("spring.datasource.url"))
                    .isEqualTo("jdbc:postgresql://chosen:5432/by-hand");
        } finally {
            System.clearProperty("spring.datasource.url");
            System.clearProperty("spring.datasource.username");
            System.clearProperty("spring.datasource.password");
        }
    }
}
