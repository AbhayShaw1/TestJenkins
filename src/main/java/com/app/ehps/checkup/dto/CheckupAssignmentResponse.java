package com.app.ehps.checkup.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

/**
 * Response shape for a created checkup assignment (BEHAVIOR-BASELINE.md §11). Plain carrier —
 * mapping happens in services.
 */
@Getter
@AllArgsConstructor
public class CheckupAssignmentResponse {

    private Long workId;
    private Long machineId;
    private Long technicianId;
    private String workType;
    private LocalDate workDate;
}
