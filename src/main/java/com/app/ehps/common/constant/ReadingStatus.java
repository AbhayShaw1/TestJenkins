package com.app.ehps.common.constant;

import lombok.Getter;

/**
 * Per-parameter checkup reading status (BEHAVIOR-BASELINE.md §9).
 */
@Getter
public enum ReadingStatus {

    GOOD("good"),
    WARNING("warning"),
    BAD("bad");

    private final String dbValue;

    ReadingStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public static ReadingStatus fromDbValue(String dbValue) {
        for (ReadingStatus status : values()) {
            if (status.dbValue.equalsIgnoreCase(dbValue)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown reading status db value: " + dbValue);
    }
}
