package com.app.ehps.repair.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Response for the technician repair-completion action (BEHAVIOR-BASELINE.md §12).
 */
@Getter
@AllArgsConstructor
public class CompleteRepairResponse {

    private Long alertId;
    private Long machineId;
    private String machineCode;
    private String alertStatus;
    private boolean repairRecorded;
    private boolean historyRecorded;
}
