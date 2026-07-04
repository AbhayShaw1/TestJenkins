package com.app.ehps.alert.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

/**
 * Response for the fab repair-assignment action (BEHAVIOR-BASELINE.md §10).
 */
@Getter
@AllArgsConstructor
public class AssignRepairResponse {

    private Long alertId;
    private Long technicianId;
    private Long machineId;
    private String workType;
    private LocalDate workDate;
}
