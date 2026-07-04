package com.app.ehps.checkup.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Checkup assignment payload (BEHAVIOR-BASELINE.md §11).
 */
@Getter
@Setter
public class AssignCheckupRequest {

    @NotNull(message = "Machine id is required")
    private Long machineId;

    @NotNull(message = "Technician id is required")
    private Long technicianId;

    private LocalDate workDate;
}
