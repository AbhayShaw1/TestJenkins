package com.app.ehps.checkup;

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
 * Maps to the {@code checkup_readings} table — one row per parameter reading for a checkup
 * (see db schema.sql and BEHAVIOR-BASELINE.md §9).
 */
@Entity
@Table(name = "checkup_readings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CheckupReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reading_id")
    private Long readingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checkup_id")
    private Checkup checkup;

    @Column(name = "param_index", nullable = false)
    private Integer paramIndex;

    @Column(name = "reading_value", nullable = false)
    private Float readingValue;

    @Column(name = "status", nullable = false)
    private String status;
}
