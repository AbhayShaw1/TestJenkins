package com.app.ehps.common.constant;

import lombok.Getter;

/**
 * TechnicianWork type — checkup vs repair.
 */
@Getter
public enum WorkType {

    CHECKUP("checkup"),
    REPAIR("repair");

    private final String dbValue;

    WorkType(String dbValue) {
        this.dbValue = dbValue;
    }

    public static WorkType fromDbValue(String dbValue) {
        for (WorkType type : values()) {
            if (type.dbValue.equalsIgnoreCase(dbValue)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown work type db value: " + dbValue);
    }
}
