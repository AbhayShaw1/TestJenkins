package com.app.ehps.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * On an authenticated-but-forbidden request (URL-level or method-level authorization denial),
 * writes the standard 403 JSON body. Mirrors {@link JwtAuthEntryPoint} so both filter-chain
 * security errors return the same envelope shape as {@code GlobalExceptionHandler}
 * (BEHAVIOR-BASELINE.md §5).
 */
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> body = Map.of(
                "success", false,
                "message", "Access denied. You do not have permission."
        );

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
