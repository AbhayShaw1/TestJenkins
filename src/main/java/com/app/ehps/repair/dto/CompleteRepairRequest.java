package com.app.ehps.repair.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Repair-completion payload (BEHAVIOR-BASELINE.md §12).
 */
@Getter
@Setter
public class CompleteRepairRequest {

    private String changesDone;

    private String observations;
}
