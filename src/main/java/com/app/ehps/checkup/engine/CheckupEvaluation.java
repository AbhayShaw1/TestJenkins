package com.app.ehps.checkup.engine;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Immutable result of {@link CheckupEngine#evaluate(int, float[])}.
 *
 * Pure value object: no Spring, no entities, no DB — safe to construct in unit tests.
 */
@Getter
@AllArgsConstructor
public class CheckupEvaluation {

    private final String[] statuses;
    private final int finalHealth;
    private final int badCount;
    private final int warningCount;
    private final boolean alertNeeded;
    private final String severity;
}
