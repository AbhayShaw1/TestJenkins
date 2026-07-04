package com.app.ehps.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Login payload — validation per BEHAVIOR-BASELINE.md §3.
 */
@Getter
@Setter
public class LoginRequest {

    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;
}
