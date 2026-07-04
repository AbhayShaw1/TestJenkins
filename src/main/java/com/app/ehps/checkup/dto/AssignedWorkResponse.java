package com.app.ehps.checkup.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

/**
 * Response shape for a technician's assigned checkup work (BEHAVIOR-BASELINE.md §11). Plain
 * carrier — mapping happens in services.
 */
@Getter
@AllArgsConstructor
public class AssignedWorkResponse {

    private Long workId;
    private Long machineId;
    private String machineCode;
    private Long fabCoordinatorId;
    private String workType;
    private LocalDate workDate;
}
