package com.app.ehps.alert.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Response for manager alert actions (approve/reject) (BEHAVIOR-BASELINE.md §10).
 */
@Getter
@AllArgsConstructor
public class AlertActionResponse {

    private Long alertId;
    private String updatedStatus;
    private Long managerId;
}
