package io.github.dremo_drizzy.bustruth.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Lets the site call the API when the two are served from different origins.
 *
 * <p>This project had no CORS configuration anywhere, and that was a feature: behind
 * nginx or the Vite dev proxy the site fetches {@code /api/...} from its own origin, so
 * the browser never asks permission and there is nothing to misconfigure.
 *
 * <p>Hosting the frontend as a static site and the API as a separate service gives them
 * two different hostnames, which makes every request cross-origin. So the permission
 * has to be granted — narrowly:
 *
 * <ul>
 *   <li><b>one origin</b>, injected from the static site by the Blueprint, never a
 *       wildcard. A public read-only API is tempting to open to {@code *}; the moment
 *       it is, anybody's page can present this project's numbers as their own
 *   <li><b>GET only</b>, because every endpoint here is a read
 *   <li><b>no credentials</b>, because there are none — nothing authenticates
 * </ul>
 *
 * <p>With no origin configured, no CORS mapping is registered at all. That keeps local
 * development exactly as it was, and means a misconfigured deployment fails visibly in
 * the browser rather than quietly allowing everything.
 */
@Configuration(proxyBeanMethods = false)
public class WebOriginConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebOriginConfig.class);

    private final String webOrigin;

    // Both spellings on purpose. Spring maps bustruth.api.web-origin to the
    // BUSTRUTH_API_WEB_ORIGIN environment variable itself, but a deployment that
    // silently gets no origin fails as a browser CORS error — far from the cause — so
    // the environment name is also tried explicitly rather than trusted to relax.
    public WebOriginConfig(
            @Value("${bustruth.api.web-origin:${BUSTRUTH_API_WEB_ORIGIN:}}") String webOrigin) {
        this.webOrigin = webOrigin == null ? "" : webOrigin.trim();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (webOrigin.isEmpty()) {
            log.info("no web origin configured; serving same-origin only, no CORS headers");
            return;
        }
        // The Blueprint can only inject a hostname, not a URL, so a bare host is
        // normalised rather than rejected — a deployment should not fail because a
        // scheme was missing from a generated value.
        String origin = webOrigin.startsWith("http") ? webOrigin : "https://" + webOrigin;
        log.info("allowing cross-origin GETs from {}", origin);

        registry.addMapping("/api/**")
                .allowedOrigins(origin)
                .allowedMethods("GET")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
