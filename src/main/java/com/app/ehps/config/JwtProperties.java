package com.app.ehps.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code jwt.secret} / {@code jwt.expiration-ms} (BEHAVIOR-BASELINE.md §4).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String secret;

    private long expirationMs;
}
