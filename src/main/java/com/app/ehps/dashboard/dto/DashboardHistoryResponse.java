package com.app.ehps.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

/**
 * Response shape for an equipment dashboard history row (BEHAVIOR-BASELINE.md §14). Mirrors the
 * legacy {@code EquipmentDashboardDtos.DashboardHistoryResponse} field set. Plain carrier —
 * mapping happens in services.
 */
@Getter
@AllArgsConstructor
public class DashboardHistoryResponse {

    private Long historyId;
    private Long machineId;
    private String machineCode;
    private String machineType;
    private Long technicianId;
    private String technicianName;
    private Long alertId;
    private LocalDate historyDate;
    private String issue;
    private String repairAction;
    private String observations;
}
