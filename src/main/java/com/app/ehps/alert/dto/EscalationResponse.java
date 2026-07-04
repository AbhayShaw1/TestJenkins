package com.app.ehps.alert.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Response for the fab "send to manager" escalation action (BEHAVIOR-BASELINE.md §10).
 */
@Getter
@AllArgsConstructor
public class EscalationResponse {

    private Long alertId;
    private String updatedStatus;
}
