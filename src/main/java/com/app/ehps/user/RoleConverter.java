package com.app.ehps.user;

import com.app.ehps.common.constant.Role;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Persists {@link Role} using its lowercase DB value (e.g. "manager"), matching the
 * {@code users.role} CHECK constraint in the db schema.sql.
 */
@Converter
public class RoleConverter implements AttributeConverter<Role, String> {

    @Override
    public String convertToDatabaseColumn(Role attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public Role convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Role.fromDbValue(dbData);
    }
}
