package com.app.ehps.auth.dto;

import com.app.ehps.common.constant.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * Registration payload — validation EXACTLY per BEHAVIOR-BASELINE.md §2.
 */
@Getter
@Setter
public class RegisterRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @Pattern(regexp = "^[A-Za-z0-9._%+-]+@ehps\\.com$", message = "Only @ehps.com email addresses are allowed")
    private String email;

    @Pattern(regexp = "^\\d{10}$", message = "Phone number must be exactly 10 digits")
    private String phone;

    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
            message = "Password must be at least 8 characters long and include uppercase, lowercase, number, and special character"
    )
    private String password;

    @NotNull(message = "Role is required")
    private Role role;

    private String speciality;
}
