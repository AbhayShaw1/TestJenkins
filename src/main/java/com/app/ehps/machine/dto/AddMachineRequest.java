package com.app.ehps.machine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Add-machine payload — validation per BEHAVIOR-BASELINE.md §7.
 */
@Getter
@Setter
public class AddMachineRequest {

    @NotBlank(message = "Machine code is required")
    private String machineCode;

    @NotNull(message = "Machine type is required")
    private Long typeId;

    private LocalDate installDate;
}
