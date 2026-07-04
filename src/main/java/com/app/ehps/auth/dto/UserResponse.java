package com.app.ehps.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Public-facing user shape returned by register/login (BEHAVIOR-BASELINE.md §2, §3).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private Long empId;
    private String name;
    private String email;
    private String phone;
    private String role;
    private String speciality;
}
