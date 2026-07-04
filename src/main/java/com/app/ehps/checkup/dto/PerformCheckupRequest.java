package com.app.ehps.checkup.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Perform-checkup payload — raw parameter values (BEHAVIOR-BASELINE.md §9).
 */
@Getter
@Setter
public class PerformCheckupRequest {

    @NotNull(message = "Parameter values are required")
    private List<Float> values;
}
