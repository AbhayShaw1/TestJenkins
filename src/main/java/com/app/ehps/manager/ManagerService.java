package com.app.ehps.manager;

import com.app.ehps.common.constant.Role;
import com.app.ehps.machine.Machine;
import com.app.ehps.machine.MachineRepository;
import com.app.ehps.machine.dto.MachineResponse;
import com.app.ehps.user.User;
import com.app.ehps.user.UserRepository;
import com.app.ehps.user.dto.TechnicianResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Manager read models — manager sees ALL machines and technicians (BEHAVIOR-BASELINE.md §1).
 */
@Service
public class ManagerService {

    private final MachineRepository machineRepository;
    private final UserRepository userRepository;

    public ManagerService(MachineRepository machineRepository, UserRepository userRepository) {
        this.machineRepository = machineRepository;
        this.userRepository = userRepository;
    }

    public List<MachineResponse> getAllMachines() {
        return machineRepository.findAll().stream()
                .map(this::mapToMachineResponse)
                .collect(Collectors.toList());
    }

    public List<TechnicianResponse> getAllTechnicians() {
        return userRepository.findByRoleOrderByEmpId(Role.TECHNICIAN).stream()
                .map(this::mapToTechnicianResponse)
                .collect(Collectors.toList());
    }

    private MachineResponse mapToMachineResponse(Machine machine) {
        return new MachineResponse(
                machine.getMachineId(),
                machine.getMachineCode(),
                machine.getMachineType() == null ? null : machine.getMachineType().getTypeId(),
                machine.getMachineType() == null ? null : machine.getMachineType().getTypeName(),
                machine.getInstallDate(),
                machine.getFabUser() == null ? null : machine.getFabUser().getEmpId()
        );
    }

    private TechnicianResponse mapToTechnicianResponse(User user) {
        return new TechnicianResponse(
                user.getEmpId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole().getDbValue(),
                user.getSpeciality()
        );
    }
}
