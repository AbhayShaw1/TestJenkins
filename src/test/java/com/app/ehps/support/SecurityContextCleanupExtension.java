package com.app.ehps.support;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Clears the {@link SecurityContextHolder} before every test.
 *
 * <p>The {@code SecurityContextHolder} is a static {@code ThreadLocal}. Plain unit tests (including
 * the legacy {@code com.app.ehps_api.*} service tests) populate it and, in the case of the legacy
 * suite, never clear it. Surefire runs all tests in one JVM on one thread, so a stale authentication
 * can leak into a later MockMvc integration test: our {@code JwtAuthenticationFilter} only sets the
 * JWT-derived authentication when the context is empty, so a leaked (wrong-role) principal survives
 * and causes spurious 403s. This never happens in production — the Spring Security filter chain
 * clears the context after every real request — so the fix belongs purely in test setup.
 *
 * <p>Auto-registered for the whole module via {@code junit-platform.properties}
 * ({@code junit.jupiter.extensions.autodetection.enabled=true}) + the {@code META-INF/services}
 * service file, so no individual test class needs to opt in. It runs before each test's own
 * {@code @BeforeEach}, so tests that legitimately seed the context still work.
 */
public class SecurityContextCleanupExtension implements BeforeEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        SecurityContextHolder.clearContext();
    }
}
