package com.app.ehps.alert.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Repair-assignment payload (BEHAVIOR-BASELINE.md §10).
 */
@Getter
@Setter
public class AssignRepairRequest {

    @NotNull(message = "Technician id is required")
    private Long technicianId;

    private LocalDate repairDate;
}
