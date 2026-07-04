package com.app.ehps.alert;

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
 * Maps to the {@code risk_alerts} table — the alert state machine (see db schema.sql and
 * BEHAVIOR-BASELINE.md §9, §10).
 */
@Entity
@Table(name = "risk_alerts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RiskAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "alert_id")
    private Long alertId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "machine_id")
    private Machine machine;

    @Column(name = "problem_measure")
    private String problemMeasure;

    @Column(name = "severity")
    private String severity;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "raised_on")
    private LocalDate raisedOn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fab_id")
    private User fabUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_tech_id")
    private User assignedTechnician;
}
