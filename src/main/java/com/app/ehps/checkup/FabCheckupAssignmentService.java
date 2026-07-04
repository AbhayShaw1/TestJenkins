package com.app.ehps.checkup;

import com.app.ehps.alert.RiskAlertRepository;
import com.app.ehps.checkup.dto.AssignCheckupRequest;
import com.app.ehps.checkup.dto.CheckupAssignmentResponse;
import com.app.ehps.common.constant.Role;
import com.app.ehps.common.security.SecurityUtils;
import com.app.ehps.machine.Machine;
import com.app.ehps.machine.MachineRepository;
import com.app.ehps.user.User;
import com.app.ehps.user.UserRepository;
import com.app.ehps.work.TechnicianWork;
import com.app.ehps.work.TechnicianWorkRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Fab-coordinator checkup-assignment operations (BEHAVIOR-BASELINE.md §11). Ports
 * {@code com.app.ehps_api.service.FabCheckupAssignmentService#assignTechnicianForCheckup} faithfully,
 * but reads the expected speciality from {@link com.app.ehps.machine.MachineType#getSpeciality()}
 * (data-driven) instead of the legacy hardcoded type-id-to-speciality map.
 */
@Service
@Transactional
public class FabCheckupAssignmentService {

    private final MachineRepository machineRepository;
    private final UserRepository userRepository;
    private final TechnicianWorkRepository technicianWorkRepository;
    private final RiskAlertRepository riskAlertRepository;

    public FabCheckupAssignmentService(MachineRepository machineRepository,
                                        UserRepository userRepository,
                                        TechnicianWorkRepository technicianWorkRepository,
                                        RiskAlertRepository riskAlertRepository) {
        this.machineRepository = machineRepository;
        this.userRepository = userRepository;
        this.technicianWorkRepository = technicianWorkRepository;
        this.riskAlertRepository = riskAlertRepository;
    }

    public CheckupAssignmentResponse assignTechnicianForCheckup(AssignCheckupRequest request) {
        User fabCoordinator = currentUser();

        Machine machine = machineRepository.findById(request.getMachineId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Machine not found"));

        boolean hasActiveAlert = riskAlertRepository.existsByMachine_MachineIdAndStatusNotIn(
                machine.getMachineId(),
                List.of("resolved", "rejected"));

        if (hasActiveAlert) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Machine has unresolved risk alerts. Resolve them before assigning again");
        }

        User technician = userRepository.findById(request.getTechnicianId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Technician not found"));

        if (technician.getRole() != Role.TECHNICIAN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not a technician");
        }

        if (machine.getMachineType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Machine type not found for selected machine");
        }

        String expectedSpeciality = machine.getMachineType().getSpeciality();

        if (technician.getSpeciality() == null || !technician.getSpeciality().equalsIgnoreCase(expectedSpeciality)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Technician speciality does not match machine type");
        }

        boolean machineAlreadyAssigned = technicianWorkRepository
                .existsByMachine_MachineIdAndWorkTypeIgnoreCaseAndWorkDateAndCompletedFalse(
                        machine.getMachineId(), "checkup", request.getWorkDate());

        if (machineAlreadyAssigned) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Machine already has an active checkup assignment");
        }

        TechnicianWork technicianWork = new TechnicianWork();
        technicianWork.setMachine(machine);
        technicianWork.setTechnician(technician);
        technicianWork.setFabUser(fabCoordinator);
        technicianWork.setWorkType("checkup");
        technicianWork.setWorkDate(request.getWorkDate());
        technicianWork.setCompleted(false);

        TechnicianWork savedWork = technicianWorkRepository.save(technicianWork);

        return new CheckupAssignmentResponse(
                savedWork.getWorkId(),
                machine.getMachineId(),
                technician.getEmpId(),
                savedWork.getWorkType(),
                savedWork.getWorkDate());
    }

    private User currentUser() {
        return userRepository.findByEmail(SecurityUtils.currentUserEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }
}
