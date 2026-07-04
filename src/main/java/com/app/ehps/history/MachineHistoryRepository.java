package com.app.ehps.history;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface MachineHistoryRepository extends JpaRepository<MachineHistory, Long> {

    List<MachineHistory> findByHistoryDateBetweenOrderByHistoryDateDesc(LocalDate from, LocalDate to);

    List<MachineHistory> findByHistoryDateBetweenAndMachine_MachineType_TypeIdOrderByHistoryDateDesc(
            LocalDate from, LocalDate to, Long typeId);
}
