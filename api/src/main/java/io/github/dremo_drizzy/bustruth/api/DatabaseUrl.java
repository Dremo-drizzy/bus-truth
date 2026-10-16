package io.github.dremo_drizzy.bustruth.api;

import java.net.URI;
import java.util.Optional;

/**
 * Turns a managed host's {@code DATABASE_URL} into the three properties Spring wants.
 *
 * <p>Render — and Heroku, and most managed Postgres — hand you one variable:
 * {@code postgres://user:password@host:5432/database}. JDBC cannot use it. It needs
 * {@code jdbc:postgresql://host:5432/database} with the credentials supplied
 * separately, so something has to translate, and that something is here rather than in
 * a shell script nobody reads.
 *
 * <p>This exists because of a limitation worth knowing: a Render Blueprint can inject a
 * database's {@code connectionString}, {@code user}, {@code password} and
 * {@code database}, but <b>not its host or port</b>. The internal hostname is generated
 * when the database is created, so there is no way to write it into the Blueprint in
 * advance and no way to assemble a JDBC URL from the parts. Taking the whole connection
 * string and splitting it is the only route that keeps the credentials out of the
 * repository and out of anybody's hands.
 *
 * <p>Local development is unaffected: without {@code DATABASE_URL} nothing here fires,
 * and {@code application.yml}'s {@code POSTGRES_HOST}/{@code POSTGRES_PORT} defaults
 * still point at the compose database on 55432.
 */
public final class DatabaseUrl {

    private DatabaseUrl() {
    }

    /**
     * Translates {@code DATABASE_URL} into system properties, if it is set.
     *
     * <p>Called before the Spring context starts, so the properties are in place by the
     * time the datasource is built. Set as system properties rather than injected into
     * the environment by a post-processor: one line in {@code main} that anybody can
     * follow beats a registration file whose silent absence would look like a
     * connection failure.
     *
     * <p>Anything already set explicitly wins, so a deployment can always override this
     * by providing {@code spring.datasource.url} itself.
     */
    public static void applyIfPresent(String databaseUrl) {
        Translation translation = translate(databaseUrl).orElse(null);
        if (translation == null) {
            return;
        }
        setIfAbsent("spring.datasource.url", translation.jdbcUrl());
        setIfAbsent("spring.datasource.username", translation.username());
        setIfAbsent("spring.datasource.password", translation.password());
    }

    /**
     * Parses a {@code postgres://} or {@code postgresql://} URL.
     *
     * @return empty when there is nothing usable to translate, so that a missing or
     *         malformed variable leaves local configuration alone rather than
     *         half-applying
     */
    public static Optional<Translation> translate(String databaseUrl) {
        if (databaseUrl == null || databaseUrl.isBlank()) {
            return Optional.empty();
        }
        URI uri;
        try {
            uri = URI.create(databaseUrl.trim());
        } catch (IllegalArgumentException notAUrl) {
            return Optional.empty();
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equals("postgres") || scheme.equals("postgresql"))) {
            return Optional.empty();
        }
        if (uri.getHost() == null) {
            return Optional.empty();
        }

        // Managed hosts usually omit the port and mean 5432. The local compose database
        // is on 55432 and is reached through POSTGRES_PORT instead, never through here.
        int port = uri.getPort() == -1 ? 5432 : uri.getPort();
        String database = uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/", "");

        String userInfo = uri.getUserInfo();
        String username = "";
        String password = "";
        if (userInfo != null) {
            int split = userInfo.indexOf(':');
            username = split < 0 ? userInfo : userInfo.substring(0, split);
            password = split < 0 ? "" : userInfo.substring(split + 1);
        }

        // The query string is carried across: managed Postgres commonly appends
        // ?sslmode=require, and dropping it turns a working URL into a refused
        // connection that looks like a firewall problem.
        String query = uri.getQuery() == null ? "" : "?" + uri.getQuery();

        return Optional.of(new Translation(
                "jdbc:postgresql://" + uri.getHost() + ":" + port + "/" + database + query,
                username,
                password));
    }

    private static void setIfAbsent(String key, String value) {
        if (System.getProperty(key) == null && !value.isEmpty()) {
            System.setProperty(key, value);
        }
    }

    /** The three values Spring needs, with the password kept out of {@code toString}. */
    public record Translation(String jdbcUrl, String username, String password) {
        @Override
        public String toString() {
            return "Translation[jdbcUrl=%s, username=%s, password=***]"
                    .formatted(jdbcUrl, username);
        }
    }
}
