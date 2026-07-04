package com.app.ehps.repair;

import com.app.ehps.alert.RiskAlert;
import com.app.ehps.machine.Machine;
import com.app.ehps.user.User;
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

import java.time.LocalDate;

/**
 * Maps to the {@code repairs} table — a completed repair record (see db schema.sql and
 * BEHAVIOR-BASELINE.md §12).
 */
@Entity
@Table(name = "repairs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Repair {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "repair_id")
    private Long repairId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "machine_id")
    private Machine machine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tech_id")
    private User technician;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alert_id")
    private RiskAlert alert;

    @Column(name = "repair_date")
    private LocalDate repairDate;

    @Column(name = "changes_done")
    private String changesDone;

    @Column(name = "observations")
    private String observations;
}
