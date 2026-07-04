package com.app.ehps.auth;

import com.app.ehps.auth.dto.AuthResponse;
import com.app.ehps.auth.dto.LoginRequest;
import com.app.ehps.auth.dto.RegisterRequest;
import com.app.ehps.auth.dto.UserResponse;
import com.app.ehps.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication endpoints (docs/API-CONTRACT.md "Auth").
 */
@RestController
@RequestMapping("/api/auth")
@SecurityRequirements
@Tag(name = "Authentication", description = "Public registration and login endpoints (no bearer token required).")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(
            summary = "Register a new user",
            description = "Creates a new user account with a role (technician, manager, or fab_coordinator). "
                    + "Public endpoint, no authentication required. Returns the created user without a token."
    )
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse userResponse = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of("Registration successful", userResponse));
    }

    @Operation(
            summary = "Log in and obtain a JWT",
            description = "Authenticates a user by email/password and returns a bearer JWT plus the user profile. "
                    + "Public endpoint, no authentication required."
    )
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse authResponse = authService.login(request);
        return ResponseEntity.ok(ApiResponse.of("Login successful", authResponse));
    }
}
