package com.app.ehps.machine.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Per-type parameter metadata (name, unit, good/warning/bad ranges) for the parameter rules
 * endpoint (BEHAVIOR-BASELINE.md §9).
 */
@Getter
@AllArgsConstructor
public class ParameterRuleResponse {

    private int paramIndex;
    private String paramName;
    private String unit;
    private String good;
    private String warning;
    private String bad;
}
