package com.app.ehps.common.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Generic success envelope for every 2xx response.
 * See docs/API-CONTRACT.md — Success envelope: { success: true, message, data }.
 */
@Getter
@AllArgsConstructor
public class ApiResponse<T> {

    private final boolean success;
    private final String message;
    private final T data;

    /**
     * Build a success envelope with an explicit message.
     */
    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    /**
     * Build a success envelope with the default message "Success".
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "Success", data);
    }

    /**
     * Alias for {@link #ok(String, Object)} — reads naturally at call sites, e.g. {@code ApiResponse.of("Login successful", body)}.
     */
    public static <T> ApiResponse<T> of(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }
}
