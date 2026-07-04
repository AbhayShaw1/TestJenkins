package com.app.ehps.machine.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Shared machine-type reference data shape (BEHAVIOR-BASELINE.md §6).
 */
@Getter
@AllArgsConstructor
public class MachineTypeResponse {

    private Long typeId;
    private String typeName;
}
