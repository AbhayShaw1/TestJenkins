package com.app.ehps.checkup.engine;

import org.springframework.stereotype.Component;

/**
 * PURE checkup scoring engine — verbatim port of the legacy
 * {@code com.app.ehps_api.service.TechnicianCheckupService} private methods:
 * {@code getParameterCount}, {@code calculateStatus}, {@code lithographyStatus},
 * {@code etcherStatus}, {@code cvdStatus}, {@code ionImplanterStatus}, {@code cmpStatus},
 * {@code inspectionStatus}, {@code calculateFinalHealth}, {@code countStatus}.
 *
 * This is the CROWN JEWEL of the rebuild (BEHAVIOR-BASELINE.md §9). Every branch below —
 * comparison operator, boundary constant, and fall-through default — mirrors the legacy
 * source EXACTLY. Do not "simplify" or generalize; verbatim structure minimizes regression risk.
 *
 * Deliberately framework-free (no Spring types on the API surface beyond {@code @Component}
 * itself, no entities, no DB) so it is trivially unit-testable.
 */
@Component
public class CheckupEngine {

    /**
     * PORT of legacy {@code getParameterCount}: types 1,2 -> 5; types 3-6 -> 4; else 0.
     */
    public int parameterCount(int typeId) {
        if (typeId == 1 || typeId == 2) {
            return 5;
        }

        if (typeId == 3 || typeId == 4 || typeId == 5 || typeId == 6) {
            return 4;
        }

        return 0;
    }

    /**
     * PORT of legacy {@code calculateStatus}: dispatches to the per-type status method.
     * Unknown typeId -> "bad" (as legacy).
     */
    public String classify(int typeId, int paramNumber, float value) {
        if (typeId == 1) {
            return lithographyStatus(paramNumber, value);
        }

        if (typeId == 2) {
            return etcherStatus(paramNumber, value);
        }

        if (typeId == 3) {
            return cvdStatus(paramNumber, value);
        }

        if (typeId == 4) {
            return ionImplanterStatus(paramNumber, value);
        }

        if (typeId == 5) {
            return cmpStatus(paramNumber, value);
        }

        if (typeId == 6) {
            return inspectionStatus(paramNumber, value);
        }

        return "bad";
    }

    /**
     * PORT of legacy {@code calculateFinalHealth}.
     * weight = statuses.length==5 ? 20.0 : 25.0; good-&gt;+weight, warning-&gt;+weight/2, bad-&gt;+0;
     * return (int) Math.round(total).
     */
    public int finalHealth(String[] statuses) {
        double weight = statuses.length == 5 ? 20.0 : 25.0;
        double total = 0;

        for (String status : statuses) {
            if ("good".equals(status)) {
                total += weight;
            } else if ("warning".equals(status)) {
                total += weight / 2;
            }
        }

        return (int) Math.round(total);
    }

    /**
     * PORT of legacy {@code countStatus}.
     */
    public int countStatus(String[] statuses, String neededStatus) {
        int count = 0;

        for (String status : statuses) {
            if (neededStatus.equals(status)) {
                count++;
            }
        }

        return count;
    }

    /**
     * Orchestrates a full checkup evaluation for a machine type given its raw parameter values.
     *
     * statuses[i] = classify(typeId, i+1, values[i]) for i in 0..count-1
     * finalHealth = finalHealth(statuses)
     * badCount/warningCount via countStatus
     * alertNeeded = badCount&gt;0 || warningCount&gt;3
     * severity = badCount&gt;0 ? "high" : (alertNeeded ? "medium" : null)
     */
    public CheckupEvaluation evaluate(int typeId, float[] values) {
        int count = values.length;
        String[] statuses = new String[count];

        for (int i = 0; i < count; i++) {
            statuses[i] = classify(typeId, i + 1, values[i]);
        }

        int health = finalHealth(statuses);

        int badCount = countStatus(statuses, "bad");
        int warningCount = countStatus(statuses, "warning");

        boolean alertNeeded = badCount > 0 || warningCount > 3;
        String severity = badCount > 0 ? "high" : (alertNeeded ? "medium" : null);

        return new CheckupEvaluation(statuses, health, badCount, warningCount, alertNeeded, severity);
    }

    // ------------------------------------------------------------------
    // Per-type status methods — verbatim ports, kept separate for clarity
    // (do NOT collapse into a generic evaluator).
    // ------------------------------------------------------------------

    private String lithographyStatus(int p, float v) {
        if (p == 1) {
            if (v >= 80 && v <= 100) return "good";
            if (v >= 60 && v < 80) return "warning";
            if (v < 60) return "bad";
            return "warning";
        }

        if (p == 2) {
            if (v >= 20 && v <= 25) return "good";
            if (v >= 26 && v <= 28) return "warning";
            if (v > 28) return "bad";
            return "warning";
        }

        if (p == 3) {
            if (v < 3) return "good";
            if (v <= 5) return "warning";
            return "bad";
        }

        if (p == 4) {
            if (v < 2) return "good";
            if (v <= 4) return "warning";
            return "bad";
        }

        if (p == 5) {
            if (v < 10) return "good";
            if (v <= 20) return "warning";
            return "bad";
        }

        return "bad";
    }

    private String etcherStatus(int p, float v) {
        if (p == 1) {
            if (v >= 450 && v <= 500) return "good";
            if (v >= 400 && v < 450) return "warning";
            if (v < 400) return "bad";
            return "warning";
        }

        if (p == 2) {
            if (v >= 20 && v <= 30) return "good";
            if (v >= 31 && v <= 40) return "warning";
            return "bad";
        }

        if (p == 3) {
            if (v >= 60 && v <= 80) return "good";
            if (v >= 81 && v <= 90) return "warning";
            return "bad";
        }

        if (p == 4) {
            if (v >= 80 && v <= 100) return "good";
            if (v >= 60 && v < 80) return "warning";
            if (v < 60) return "bad";
            return "warning";
        }

        if (p == 5) {
            if (v >= 100 && v <= 120) return "good";
            if (v >= 85 && v < 100) return "warning";
            if (v < 85) return "bad";
            return "warning";
        }

        return "bad";
    }

    private String cvdStatus(int p, float v) {
        if (p == 1) {
            if (v >= 300 && v <= 350) return "good";
            if (v >= 351 && v <= 370) return "warning";
            return "bad";
        }

        if (p == 2) {
            if (v >= 100 && v <= 150) return "good";
            if (v >= 80 && v < 100) return "warning";
            if (v < 80) return "bad";
            return "warning";
        }

        if (p == 3) {
            if (v >= 0.5 && v <= 1) return "good";
            if (v > 1 && v <= 2) return "warning";
            return "bad";
        }

        if (p == 4) {
            if (v > 95) return "good";
            if (v >= 90 && v <= 95) return "warning";
            return "bad";
        }

        return "bad";
    }

    private String ionImplanterStatus(int p, float v) {
        if (p == 1) {
            if (v >= 10 && v <= 12) return "good";
            if (v >= 8 && v < 10) return "warning";
            if (v < 8) return "bad";
            return "warning";
        }

        if (p == 2) {
            if (v >= 40 && v <= 50) return "good";
            if (v >= 30 && v < 40) return "warning";
            if (v < 30) return "bad";
            return "warning";
        }

        if (p == 3) {
            if (v <= 0.000001f) return "good";
            if (v <= 0.00001f) return "warning";
            if (v > 0.0001f) return "bad";
            return "warning";
        }

        if (p == 4) {
            if (v >= 18 && v <= 22) return "good";
            if (v >= 23 && v <= 25) return "warning";
            if (v > 25) return "bad";
            return "warning";
        }

        return "bad";
    }

    private String cmpStatus(int p, float v) {
        if (p == 1) {
            if (v >= 150 && v <= 180) return "good";
            if (v >= 120 && v < 150) return "warning";
            if (v < 120) return "bad";
            return "warning";
        }

        if (p == 2) {
            if (v >= 3 && v <= 5) return "good";
            if (v > 5 && v <= 6) return "warning";
            if (v > 6) return "bad";
            return "warning";
        }

        if (p == 3) {
            if (v >= 60 && v <= 80) return "good";
            if (v >= 81 && v <= 95) return "warning";
            return "bad";
        }

        if (p == 4) {
            if (v >= 25 && v <= 35) return "good";
            if (v >= 36 && v <= 40) return "warning";
            return "bad";
        }

        return "bad";
    }

    private String inspectionStatus(int p, float v) {
        if (p == 1) {
            if (v >= 90 && v <= 100) return "good";
            if (v >= 75 && v < 90) return "warning";
            if (v < 75) return "bad";
            return "warning";
        }

        if (p == 2) {
            if (v >= 0 && v <= 2) return "good";
            if (v >= 3 && v <= 5) return "warning";
            return "bad";
        }

        if (p == 3) {
            if (v >= 0 && v <= 10) return "good";
            if (v >= 11 && v <= 20) return "warning";
            return "bad";
        }

        if (p == 4) {
            if (v < 10) return "good";
            if (v >= 10 && v <= 50) return "warning";
            return "bad";
        }

        return "bad";
    }
}
