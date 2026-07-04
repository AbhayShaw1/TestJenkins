package com.app.ehps.machine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps to the {@code machine_type_parameters} table — per-type parameter metadata (name, unit,
 * good/warning/bad descriptions). See db schema.sql and BEHAVIOR-BASELINE.md §9.
 */
@Entity
@Table(name = "machine_type_parameters")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MachineTypeParameter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "param_def_id")
    private Long paramDefId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_id")
    private MachineType machineType;

    @Column(name = "param_index", nullable = false)
    private Integer paramIndex;

    @Column(name = "param_name", nullable = false)
    private String paramName;

    @Column(name = "unit")
    private String unit;

    @Column(name = "good_desc")
    private String goodDesc;

    @Column(name = "warning_desc")
    private String warningDesc;

    @Column(name = "bad_desc")
    private String badDesc;
}
