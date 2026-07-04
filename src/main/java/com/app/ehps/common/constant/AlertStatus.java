package com.app.ehps.common.constant;

import lombok.Getter;

/**
 * Alert state machine statuses (BEHAVIOR-BASELINE.md §10). Persisted as lowercase strings.
 *
 * <pre>
 * pending --escalate(fab)--> sent_to_manager --approve(mgr)--> approved --assign+complete--> resolved
 *                                            \--reject(mgr)--> rejected
 * </pre>
 */
@Getter
public enum AlertStatus {

    PENDING("pending"),
    SENT_TO_MANAGER("sent_to_manager"),
    APPROVED("approved"),
    REJECTED("rejected"),
    RESOLVED("resolved");

    private final String dbValue;

    AlertStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public static AlertStatus fromDbValue(String dbValue) {
        for (AlertStatus status : values()) {
            if (status.dbValue.equalsIgnoreCase(dbValue)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown alert status db value: " + dbValue);
    }
}
