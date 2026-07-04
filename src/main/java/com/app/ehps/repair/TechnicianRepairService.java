package com.app.ehps.repair;

import com.app.ehps.alert.RiskAlert;
import com.app.ehps.alert.RiskAlertRepository;
import com.app.ehps.checkup.dto.MachineDetailsResponse;
import com.app.ehps.common.security.SecurityUtils;
import com.app.ehps.history.MachineHistory;
import com.app.ehps.history.MachineHistoryRepository;
import com.app.ehps.machine.Machine;
import com.app.ehps.machine.MachineRepository;
import com.app.ehps.repair.dto.ApprovedRepairAlertResponse;
import com.app.ehps.repair.dto.CompleteRepairRequest;
import com.app.ehps.repair.dto.CompleteRepairResponse;
import com.app.ehps.user.User;
import com.app.ehps.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Technician-only repair business logic — verbatim port of legacy
 * {@code com.app.ehps_api.service.TechnicianRepairService} (BEHAVIOR-BASELINE.md §12).
 */
@Service
@Transactional
public class TechnicianRepairService {

    private static final String STATUS_APPROVED = "approved";
    private static final String STATUS_RESOLVED = "resolved";

    private final UserRepository userRepository;
    private final RiskAlertRepository riskAlertRepository;
    private final MachineRepository machineRepository;
    private final RepairRepository repairRepository;
    private final MachineHistoryRepository machineHistoryRepository;

    public TechnicianRepairService(UserRepository userRepository,
                                   RiskAlertRepository riskAlertRepository,
                                   MachineRepository machineRepository,
                                   RepairRepository repairRepository,
                                   MachineHistoryRepository machineHistoryRepository) {
        this.userRepository = userRepository;
        this.riskAlertRepository = riskAlertRepository;
        this.machineRepository = machineRepository;
        this.repairRepository = repairRepository;
        this.machineHistoryRepository = machineHistoryRepository;
    }

    public List<ApprovedRepairAlertResponse> getApprovedRepairAlerts() {
        User technician = getLoggedInTechnician();

        List<RiskAlert> alerts = riskAlertRepository
                .findByAssignedTechnician_EmpIdAndStatusOrderByRaisedOnDesc(technician.getEmpId(), STATUS_APPROVED);

        List<ApprovedRepairAlertResponse> responseList = new ArrayList<>();

        for (RiskAlert alert : alerts) {
            responseList.add(mapToApprovedRepairAlertResponse(alert));
        }

        return responseList;
    }

    public ApprovedRepairAlertResponse getApprovedRepairAlert(Long alertId) {
        validateAlertId(alertId);

        User technician = getLoggedInTechnician();

        RiskAlert alert = riskAlertRepository
                .findByAlertIdAndAssignedTechnician_EmpIdAndStatus(alertId, technician.getEmpId(), STATUS_APPROVED)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Alert is not assigned to this technician"
                ));

        return mapToApprovedRepairAlertResponse(alert);
    }

    public MachineDetailsResponse getMachineDetails(Long machineId) {
        validateMachineId(machineId);

        Machine machine = machineRepository.findById(machineId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Machine not found"));

        if (machine.getMachineType() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Machine type not found for selected machine"
            );
        }

        return new MachineDetailsResponse(
                machine.getMachineId(),
                machine.getMachineCode(),
                machine.getMachineType().getTypeId(),
                machine.getMachineType().getTypeName(),
                machine.getInstallDate(),
                machine.getFabUser() != null ? machine.getFabUser().getEmpId() : null
        );
    }

    public CompleteRepairResponse completeRepair(Long alertId, CompleteRepairRequest request) {
        validateAlertId(alertId);

        User technician = getLoggedInTechnician();

        RiskAlert alert = riskAlertRepository
                .findByAlertIdAndAssignedTechnician_EmpIdAndStatus(alertId, technician.getEmpId(), STATUS_APPROVED)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Alert is not assigned to this technician"
                ));

        Machine machine = alert.getMachine();

        String changesDone = request.getChangesDone() != null ? request.getChangesDone().trim() : null;
        String observations = request.getObservations() != null ? request.getObservations().trim() : null;

        Repair repair = new Repair();
        repair.setMachine(machine);
        repair.setTechnician(technician);
        repair.setAlert(alert);
        repair.setRepairDate(LocalDate.now());
        repair.setChangesDone(changesDone);
        repair.setObservations(observations);

        repairRepository.save(repair);

        MachineHistory history = new MachineHistory();
        history.setMachine(machine);
        history.setTechnician(technician);
        history.setAlert(alert);
        history.setHistoryDate(LocalDate.now());
        history.setIssue(alert.getProblemMeasure());
        history.setRepairAction(changesDone);
        history.setObservations(observations);

        machineHistoryRepository.save(history);

        alert.setStatus(STATUS_RESOLVED);
        RiskAlert updatedAlert = riskAlertRepository.save(alert);

        return new CompleteRepairResponse(
                updatedAlert.getAlertId(),
                machine != null ? machine.getMachineId() : null,
                machine != null ? machine.getMachineCode() : null,
                updatedAlert.getStatus(),
                true,
                true
        );
    }

    private ApprovedRepairAlertResponse mapToApprovedRepairAlertResponse(RiskAlert alert) {
        Machine machine = alert.getMachine();

        return new ApprovedRepairAlertResponse(
                alert.getAlertId(),
                machine != null ? machine.getMachineId() : null,
                machine != null ? machine.getMachineCode() : null,
                alert.getProblemMeasure(),
                alert.getSeverity(),
                alert.getStatus(),
                alert.getRaisedOn()
        );
    }

    private void validateAlertId(Long alertId) {
        if (alertId == null || alertId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid alert id");
        }
    }

    private void validateMachineId(Long machineId) {
        if (machineId == null || machineId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid machine id");
        }
    }

    private User getLoggedInTechnician() {
        return userRepository.findByEmail(SecurityUtils.currentUserEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }
}
