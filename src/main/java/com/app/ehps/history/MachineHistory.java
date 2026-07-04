package com.app.ehps.history;

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
 * Maps to the {@code machine_history} table — equipment history/audit trail entries (see db
 * schema.sql and BEHAVIOR-BASELINE.md §12, §14).
 */
@Entity
@Table(name = "machine_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MachineHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "machine_id")
    private Machine machine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tech_id")
    private User technician;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alert_id")
    private RiskAlert alert;

    @Column(name = "history_date")
    private LocalDate historyDate;

    @Column(name = "issue")
    private String issue;

    @Column(name = "repair_action")
    private String repairAction;

    @Column(name = "observations")
    private String observations;
}
