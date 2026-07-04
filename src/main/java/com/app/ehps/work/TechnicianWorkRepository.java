package com.app.ehps.work;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TechnicianWorkRepository extends JpaRepository<TechnicianWork, Long> {

    List<TechnicianWork> findByTechnician_EmpIdAndWorkTypeIgnoreCaseAndCompletedFalse(Long technicianId, String workType);

    Optional<TechnicianWork> findFirstByTechnician_EmpIdAndMachine_MachineIdAndWorkTypeIgnoreCaseAndCompletedFalseOrderByWorkIdDesc(
            Long technicianId, Long machineId, String workType);

    boolean existsByTechnician_EmpIdAndMachine_MachineIdAndWorkTypeIgnoreCaseAndCompletedFalse(
            Long technicianId, Long machineId, String workType);

    boolean existsByMachine_MachineIdAndWorkTypeIgnoreCaseAndWorkDateAndCompletedFalse(
            Long machineId, String workType, LocalDate workDate);

    boolean existsByMachine_MachineIdAndTechnician_EmpIdAndWorkTypeIgnoreCaseAndWorkDate(
            Long machineId, Long technicianId, String workType, LocalDate workDate);
}
