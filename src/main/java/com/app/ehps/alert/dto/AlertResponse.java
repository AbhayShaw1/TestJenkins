package com.app.ehps.alert.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

/**
 * Response shape for a risk alert, shared across fab/manager alert views
 * (BEHAVIOR-BASELINE.md §10). Plain carrier — mapping happens in services.
 */
@Getter
@AllArgsConstructor
public class AlertResponse {

    private Long alertId;
    private Long machineId;
    private String machineCode;
    private String problemMeasure;
    private String severity;
    private String status;
    private LocalDate raisedOn;
    private Long fabCoordinatorId;
    private Long approvedById;
    private Long assignedTechnicianId;
}
