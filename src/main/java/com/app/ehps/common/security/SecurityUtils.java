package com.app.ehps.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Centralizes the logged-in-user lookup (legacy scattered {@code SecurityContextHolder} calls
 * everywhere). The authentication name is the JWT subject, i.e. the user's email.
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * Returns the currently authenticated user's email (JWT subject).
     *
     * @throws NullPointerException if there is no authentication in the security context.
     */
    public static String currentUserEmail() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    /**
     * Null-safe variant of {@link #currentUserEmail()}.
     */
    public static Optional<String> currentUserEmailOptional() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(authentication.getName());
    }
}
