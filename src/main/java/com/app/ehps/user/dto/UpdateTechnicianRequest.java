package com.app.ehps.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

/**
 * Update-technician payload — updates name/email/phone/speciality only (NOT role/password), per
 * BEHAVIOR-BASELINE.md §8.
 */
@Getter
public class UpdateTechnicianRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @Pattern(regexp = "^[A-Za-z0-9._%+-]+@ehps\\.com$", message = "Only @ehps.com email addresses are allowed")
    private String email;

    @Pattern(regexp = "^\\d{10}$", message = "Phone number must be exactly 10 digits")
    private String phone;

    @NotBlank(message = "Speciality is required")
    private String speciality;
}
