package com.app.ehps.repair.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

/**
 * Response shape for an approved-and-assigned repair alert, as seen by the assigned technician
 * (BEHAVIOR-BASELINE.md §12).
 */
@Getter
@AllArgsConstructor
public class ApprovedRepairAlertResponse {

    private Long alertId;
    private Long machineId;
    private String machineCode;
    private String problemMeasure;
    private String severity;
    private String status;
    private LocalDate raisedOn;
}
