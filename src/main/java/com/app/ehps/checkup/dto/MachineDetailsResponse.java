package com.app.ehps.checkup.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

/**
 * Machine details as seen by a technician performing a checkup or repair (BEHAVIOR-BASELINE.md
 * §9, §12). Plain carrier — mapping happens in services.
 */
@Getter
@AllArgsConstructor
public class MachineDetailsResponse {

    private Long machineId;
    private String machineCode;
    private Long typeId;
    private String typeName;
    private LocalDate installDate;
    private Long fabCoordinatorId;
}
