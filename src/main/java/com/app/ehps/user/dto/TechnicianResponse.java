package com.app.ehps.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Response shape for a technician (BEHAVIOR-BASELINE.md §8).
 */
@Getter
@AllArgsConstructor
public class TechnicianResponse {

    private Long technicianId;
    private String name;
    private String email;
    private String phone;
    private String role;
    private String speciality;
}
