package com.app.ehps.common.error;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Error body shape — EXACTLY matching legacy {@code ApiErrorResponse} (BEHAVIOR-BASELINE.md §5):
 * { success:false, message, status, path, timestamp, errors }.
 */
@Getter
@AllArgsConstructor
public class ApiError {

    private final boolean success;
    private final String message;
    private final int status;
    private final String path;
    private final LocalDateTime timestamp;
    private final Map<String, String> errors;

    /**
     * Convenience factory: success is always false and timestamp is always "now".
     */
    public static ApiError of(String message, int status, String path, Map<String, String> errors) {
        return new ApiError(false, message, status, path, LocalDateTime.now(), errors);
    }
}
