package com.app.ehps.alert;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RiskAlertRepository extends JpaRepository<RiskAlert, Long> {

    boolean existsByMachine_MachineIdAndStatusNotIn(Long machineId, List<String> statuses);

    List<RiskAlert> findByFabUser_EmpIdAndStatusOrderByRaisedOnDesc(Long fabId, String status);

    Optional<RiskAlert> findByAlertIdAndFabUser_EmpIdAndStatus(Long alertId, Long fabId, String status);

    List<RiskAlert> findByFabUser_EmpIdAndStatusAndAssignedTechnicianIsNullOrderByRaisedOnDesc(Long fabId, String status);

    Optional<RiskAlert> findByAlertIdAndFabUser_EmpIdAndStatusAndAssignedTechnicianIsNull(Long alertId, Long fabId, String status);

    List<RiskAlert> findByStatusNotInOrderByAlertIdDesc(List<String> statuses);

    List<RiskAlert> findByStatusOrderByRaisedOnDesc(String status);

    Optional<RiskAlert> findByAlertIdAndStatus(Long alertId, String status);

    List<RiskAlert> findByAssignedTechnician_EmpIdAndStatusOrderByRaisedOnDesc(Long technicianId, String status);

    Optional<RiskAlert> findByAlertIdAndAssignedTechnician_EmpIdAndStatus(Long alertId, Long technicianId, String status);
}
