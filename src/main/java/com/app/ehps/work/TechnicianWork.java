package com.app.ehps.work;

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
 * Maps to the {@code technician_work} table — assigned checkup/repair work items (see db
 * schema.sql and BEHAVIOR-BASELINE.md §10, §11, §12).
 */
@Entity
@Table(name = "technician_work")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TechnicianWork {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "work_id")
    private Long workId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "machine_id")
    private Machine machine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tech_id")
    private User technician;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fab_id")
    private User fabUser;

    @Column(name = "work_type", nullable = false)
    private String workType;

    @Column(name = "work_date")
    private LocalDate workDate;

    @Column(name = "completed", nullable = false)
    private Boolean completed;
}
