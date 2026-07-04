package com.app.ehps.checkup.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * Result of performing a checkup — health score, per-parameter statuses, and whether a risk
 * alert was auto-generated (BEHAVIOR-BASELINE.md §9). Plain carrier — mapping happens in
 * services.
 */
@Getter
@AllArgsConstructor
public class PerformCheckupResult {

    private Long machineId;
    private String machineType;
    private int finalHealth;
    private List<String> statuses;
    private boolean riskAlertCreated;
    private Long riskAlertId;
    private String severity;
}
