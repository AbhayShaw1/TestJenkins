package com.app.ehps.alert;

import com.app.ehps.alert.dto.AlertResponse;
import com.app.ehps.alert.dto.AssignRepairRequest;
import com.app.ehps.alert.dto.AssignRepairResponse;
import com.app.ehps.alert.dto.EscalationResponse;
import com.app.ehps.common.constant.Role;
import com.app.ehps.common.security.SecurityUtils;
import com.app.ehps.machine.Machine;
import com.app.ehps.user.User;
import com.app.ehps.user.UserRepository;
import com.app.ehps.user.dto.TechnicianResponse;
import com.app.ehps.work.TechnicianWork;
import com.app.ehps.work.TechnicianWorkRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

/**
 * Fab-coordinator alert lifecycle operations (BEHAVIOR-BASELINE.md §10). Ports
 * {@code com.app.ehps_api.service.FabAlertService} faithfully, but reads the expected speciality
 * from {@link com.app.ehps.machine.MachineType#getSpeciality()} (data-driven) instead of the
 * legacy hardcoded type-id-to-speciality map.
 */
@Service
@Transactional
public class FabAlertService {

    private final RiskAlertRepository riskAlertRepository;
    private final UserRepository userRepository;
    private final TechnicianWorkRepository technicianWorkRepository;

    public FabAlertService(RiskAlertRepository riskAlertRepository,
                            UserRepository userRepository,
                            TechnicianWorkRepository technicianWorkRepository) {
        this.riskAlertRepository = riskAlertRepository;
        this.userRepository = userRepository;
        this.technicianWorkRepository = technicianWorkRepository;
    }

    public List<AlertResponse> getPendingAlerts() {
        User fabCoordinator = currentUser();

        List<RiskAlert> alerts = riskAlertRepository.findByFabUser_EmpIdAndStatusOrderByRaisedOnDesc(
                fabCoordinator.getEmpId(), "pending");

        return mapAlerts(alerts);
    }

    public EscalationResponse sendToManager(Long alertId) {
        User fabCoordinator = currentUser();

        validateAlertId(alertId);

        RiskAlert alert = riskAlertRepository.findByAlertIdAndFabUser_EmpIdAndStatus(
                        alertId, fabCoordinator.getEmpId(), "pending")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pending alert not found"));

        alert.setStatus("sent_to_manager");
        RiskAlert updatedAlert = riskAlertRepository.save(alert);

        return new EscalationResponse(updatedAlert.getAlertId(), updatedAlert.getStatus());
    }

    public List<AlertResponse> getApprovedUnassignedAlerts() {
        User fabCoordinator = currentUser();

        List<RiskAlert> alerts = riskAlertRepository
                .findByFabUser_EmpIdAndStatusAndAssignedTechnicianIsNullOrderByRaisedOnDesc(
                        fabCoordinator.getEmpId(), "approved");

        return mapAlerts(alerts);
    }

    public List<TechnicianResponse> getMatchingTechnicians(Long alertId) {
        User fabCoordinator = currentUser();

        validateAlertId(alertId);

        RiskAlert alert = riskAlertRepository
                .findByAlertIdAndFabUser_EmpIdAndStatusAndAssignedTechnicianIsNull(
                        alertId, fabCoordinator.getEmpId(), "approved")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Approved unassigned alert not found"));

        Machine machine = alert.getMachine();

        if (machine == null || machine.getMachineType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Machine details not found for alert");
        }

        String speciality = machine.getMachineType().getSpeciality();

        List<User> technicians = userRepository.findByRoleAndSpecialityIgnoreCase(Role.TECHNICIAN, speciality);

        List<TechnicianResponse> responseList = new ArrayList<>();

        for (User technician : technicians) {
            responseList.add(new TechnicianResponse(
                    technician.getEmpId(),
                    technician.getName(),
                    technician.getEmail(),
                    technician.getPhone(),
                    technician.getRole().getDbValue(),
                    technician.getSpeciality()));
        }

        return responseList;
    }

    public AssignRepairResponse assignRepairTechnician(Long alertId, AssignRepairRequest request) {
        User fabCoordinator = currentUser();

        validateAlertId(alertId);

        RiskAlert alert = riskAlertRepository
                .findByAlertIdAndFabUser_EmpIdAndStatusAndAssignedTechnicianIsNull(
                        alertId, fabCoordinator.getEmpId(), "approved")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Approved unassigned alert not found"));

        Machine machine = alert.getMachine();

        if (machine == null || machine.getMachineType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Machine details not found for alert");
        }

        User technician = userRepository.findById(request.getTechnicianId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Technician not found"));

        if (technician.getRole() != Role.TECHNICIAN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not technician");
        }

        String expectedSpeciality = machine.getMachineType().getSpeciality();

        if (technician.getSpeciality() == null || !technician.getSpeciality().equalsIgnoreCase(expectedSpeciality)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Technician speciality mismatch");
        }

        boolean alreadyExists = technicianWorkRepository
                .existsByMachine_MachineIdAndTechnician_EmpIdAndWorkTypeIgnoreCaseAndWorkDate(
                        machine.getMachineId(), technician.getEmpId(), "repair", request.getRepairDate());

        if (alreadyExists) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Repair assignment already exists");
        }

        alert.setAssignedTechnician(technician);
        riskAlertRepository.save(alert);

        TechnicianWork work = new TechnicianWork();
        work.setMachine(machine);
        work.setTechnician(technician);
        work.setFabUser(fabCoordinator);
        work.setWorkType("repair");
        work.setWorkDate(request.getRepairDate());
        work.setCompleted(false);

        TechnicianWork savedWork = technicianWorkRepository.save(work);

        return new AssignRepairResponse(
                alert.getAlertId(),
                technician.getEmpId(),
                machine.getMachineId(),
                savedWork.getWorkType(),
                savedWork.getWorkDate());
    }

    private void validateAlertId(Long alertId) {
        if (alertId == null || alertId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid alert id");
        }
    }

    private List<AlertResponse> mapAlerts(List<RiskAlert> alerts) {
        List<AlertResponse> responseList = new ArrayList<>();

        for (RiskAlert alert : alerts) {
            responseList.add(mapAlert(alert));
        }

        return responseList;
    }

    private AlertResponse mapAlert(RiskAlert alert) {
        Machine machine = alert.getMachine();

        return new AlertResponse(
                alert.getAlertId(),
                machine != null ? machine.getMachineId() : null,
                machine != null ? machine.getMachineCode() : null,
                alert.getProblemMeasure(),
                alert.getSeverity(),
                alert.getStatus(),
                alert.getRaisedOn(),
                alert.getFabUser() != null ? alert.getFabUser().getEmpId() : null,
                alert.getApprovedBy() != null ? alert.getApprovedBy().getEmpId() : null,
                alert.getAssignedTechnician() != null ? alert.getAssignedTechnician().getEmpId() : null);
    }

    private User currentUser() {
        return userRepository.findByEmail(SecurityUtils.currentUserEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }
}
