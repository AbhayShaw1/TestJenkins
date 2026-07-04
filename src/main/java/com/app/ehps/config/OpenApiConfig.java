package com.app.ehps.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger/OpenAPI metadata + JWT bearer auth scheme for the rebuilt API.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "EHPS API",
                version = "2.0",
                description = "Equipment Health & Performance System — semiconductor fab equipment maintenance. "
                        + "Covers fab-coordinator machine/technician management, routine checkups, alert "
                        + "escalation and approval, repair assignment/completion, and the equipment health "
                        + "dashboard. All endpoints (except /api/auth/**) require a bearer JWT obtained from "
                        + "POST /api/auth/login, and are scoped by role: FAB_COORDINATOR, MANAGER, or TECHNICIAN. "
                        + "See docs/API-CONTRACT.md for the full endpoint contract."
        )
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {
}
