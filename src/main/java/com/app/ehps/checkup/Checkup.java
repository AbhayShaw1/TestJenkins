package com.app.ehps.checkup;

import com.app.ehps.machine.Machine;
import com.app.ehps.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps to the unified {@code checkups} table (see db schema.sql and BEHAVIOR-BASELINE.md §9).
 * Replaces the legacy per-machine-type checkup tables with one row per checkup plus a child
 * {@code checkup_readings} row per parameter.
 */
@Entity
@Table(name = "checkups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Checkup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "checkup_id")
    private Long checkupId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "machine_id")
    private Machine machine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tech_id")
    private User technician;

    @Column(name = "check_date", nullable = false)
    private LocalDate checkDate;

    @Column(name = "final_health", nullable = false)
    private Integer finalHealth;

    @OneToMany(mappedBy = "checkup", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CheckupReading> readings = new ArrayList<>();

    /**
     * Adds a reading to this checkup, keeping both sides of the relationship in sync.
     */
    public void addReading(CheckupReading reading) {
        readings.add(reading);
        reading.setCheckup(this);
    }
}
