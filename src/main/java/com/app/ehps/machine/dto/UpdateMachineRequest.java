package com.app.ehps.machine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Update-machine payload — same fields/validation as {@link AddMachineRequest}
 * (BEHAVIOR-BASELINE.md §7).
 */
@Getter
@Setter
public class UpdateMachineRequest {

    @NotBlank(message = "Machine code is required")
    private String machineCode;

    @NotNull(message = "Machine type is required")
    private Long typeId;

    private LocalDate installDate;
}
