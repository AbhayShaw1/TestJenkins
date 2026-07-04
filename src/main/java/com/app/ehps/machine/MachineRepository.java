package com.app.ehps.machine;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MachineRepository extends JpaRepository<Machine, Long> {

    boolean existsByMachineCodeIgnoreCase(String machineCode);

    boolean existsByMachineCodeIgnoreCaseAndMachineIdNot(String machineCode, Long machineId);

    List<Machine> findByMachineType_TypeIdOrderByMachineId(Long typeId);

    List<Machine> findByFabUser_EmpIdOrderByMachineId(Long fabId);

    Optional<Machine> findByMachineIdAndFabUser_EmpId(Long machineId, Long fabId);
}
