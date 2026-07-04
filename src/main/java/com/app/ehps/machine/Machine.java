package com.app.ehps.machine;

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
 * Maps to the {@code machines} table (see db schema.sql and BEHAVIOR-BASELINE.md §7).
 * The owning fab coordinator is exposed as {@code fabUser} (repo queries use fabUser.empId).
 */
@Entity
@Table(name = "machines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Machine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "machine_id")
    private Long machineId;

    @Column(name = "machine_code", nullable = false, unique = true)
    private String machineCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_id")
    private MachineType machineType;

    @Column(name = "install_date")
    private LocalDate installDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fab_id")
    private User fabUser;
}
