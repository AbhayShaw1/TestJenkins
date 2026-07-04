package com.app.ehps.common.constant;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * The three user roles (BEHAVIOR-BASELINE.md §1). DB enum values are lowercase; Spring
 * authorities are ROLE_ + uppercase.
 *
 * <p>{@code @JsonCreator}/{@code @JsonValue} added (additive, non-breaking) so Jackson can bind
 * this enum directly from its lowercase DB value (e.g. {@code "manager"}) in request/response
 * JSON — needed for {@code auth.dto.RegisterRequest.role} — without any other change to the
 * enum's members or existing behavior.</p>
 */
@Getter
public enum Role {

    MANAGER("manager", "ROLE_MANAGER"),
    FAB_COORDINATOR("fab_coordinator", "ROLE_FAB_COORDINATOR"),
    TECHNICIAN("technician", "ROLE_TECHNICIAN");

    private final String dbValue;
    private final String authority;

    Role(String dbValue, String authority) {
        this.dbValue = dbValue;
        this.authority = authority;
    }

    @JsonCreator
    public static Role fromDbValue(String dbValue) {
        for (Role role : values()) {
            if (role.dbValue.equalsIgnoreCase(dbValue)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown role db value: " + dbValue);
    }

    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

    /**
     * Returns the authority without the ROLE_ prefix (e.g. "MANAGER"), for use with
     * {@code hasRole(...)} which adds the prefix implicitly.
     */
    public String springRole() {
        return authority.substring("ROLE_".length());
    }
}
