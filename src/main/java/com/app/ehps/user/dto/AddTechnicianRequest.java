package com.app.ehps.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

/**
 * Add-technician payload — validation per BEHAVIOR-BASELINE.md §8 (email/phone/password patterns
 * match {@code auth.dto.RegisterRequest} exactly).
 */
@Getter
public class AddTechnicianRequest {

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

    @NotBlank(message = "Speciality is required")
    private String speciality;
}
