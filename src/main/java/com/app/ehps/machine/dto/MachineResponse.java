package com.app.ehps.machine.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

/**
 * Response shape for a machine (BEHAVIOR-BASELINE.md §7). Plain carrier — mapping happens in
 * services.
 */
@Getter
@AllArgsConstructor
public class MachineResponse {

    private Long machineId;
    private String machineCode;
    private Long typeId;
    private String typeName;
    private LocalDate installDate;
    private Long fabCoordinatorId;
}
