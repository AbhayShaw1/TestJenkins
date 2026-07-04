package com.app.ehps.machine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps to the {@code machine_types} reference table (6 fixed rows, see db schema.sql and
 * BEHAVIOR-BASELINE.md §6).
 */
@Entity
@Table(name = "machine_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MachineType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "type_id")
    private Long typeId;

    @Column(name = "type_name", nullable = false, unique = true)
    private String typeName;

    @Column(name = "code_prefix", nullable = false, unique = true)
    private String codePrefix;

    @Column(name = "speciality", nullable = false, unique = true)
    private String speciality;

    @Column(name = "param_count", nullable = false)
    private Integer paramCount;
}
