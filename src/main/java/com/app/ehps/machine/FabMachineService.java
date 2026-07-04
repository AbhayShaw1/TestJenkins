package com.app.ehps.machine;

import com.app.ehps.common.security.SecurityUtils;
import com.app.ehps.machine.dto.AddMachineRequest;
import com.app.ehps.machine.dto.MachineResponse;
import com.app.ehps.machine.dto.UpdateMachineRequest;
import com.app.ehps.user.User;
import com.app.ehps.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

/**
 * Fab-coordinator-owned machine management (BEHAVIOR-BASELINE.md §7). All machine access is
 * scoped to the logged-in fab coordinator ({@code fabUser.empId}). Role is already enforced by
 * {@code @PreAuthorize} on the controller; this service re-resolves the logged-in user for
 * ownership scoping.
 */
@Service
@Transactional
public class FabMachineService {

    private final MachineRepository machineRepository;
    private final MachineTypeRepository machineTypeRepository;
    private final UserRepository userRepository;

    public FabMachineService(MachineRepository machineRepository,
                              MachineTypeRepository machineTypeRepository,
                              UserRepository userRepository) {
        this.machineRepository = machineRepository;
        this.machineTypeRepository = machineTypeRepository;
        this.userRepository = userRepository;
    }

    public MachineResponse addMachine(AddMachineRequest request) {
        User currentUser = currentUser();

        MachineType machineType = machineTypeRepository.findById(request.getTypeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Machine type not found"));

        String machineCode = request.getMachineCode().trim().toUpperCase(Locale.ROOT);
        validatePrefix(machineCode, machineType);

        if (machineRepository.existsByMachineCodeIgnoreCase(machineCode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Machine code already exists");
        }

        Machine machine = new Machine();
        machine.setMachineCode(machineCode);
        machine.setMachineType(machineType);
        machine.setInstallDate(request.getInstallDate());
        machine.setFabUser(currentUser);

        Machine savedMachine = machineRepository.save(machine);

        return mapToMachineResponse(savedMachine);
    }

    public List<MachineResponse> getMyMachines() {
        User currentUser = currentUser();

        return machineRepository.findByFabUser_EmpIdOrderByMachineId(currentUser.getEmpId())
                .stream()
                .map(this::mapToMachineResponse)
                .toList();
    }

    public MachineResponse getMachineById(Long machineId) {
        User currentUser = currentUser();
        validateMachineId(machineId);

        Machine machine = machineRepository.findByMachineIdAndFabUser_EmpId(machineId, currentUser.getEmpId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Machine not found for this fab coordinator"));

        return mapToMachineResponse(machine);
    }

    public MachineResponse updateMachine(Long machineId, UpdateMachineRequest request) {
        User currentUser = currentUser();
        validateMachineId(machineId);

        Machine machine = machineRepository.findByMachineIdAndFabUser_EmpId(machineId, currentUser.getEmpId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Machine not found for this fab coordinator"));

        MachineType machineType = machineTypeRepository.findById(request.getTypeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Machine type not found"));

        String machineCode = request.getMachineCode().trim().toUpperCase(Locale.ROOT);
        validatePrefix(machineCode, machineType);

        if (machineRepository.existsByMachineCodeIgnoreCaseAndMachineIdNot(machineCode, machineId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Machine code already exists");
        }

        machine.setMachineCode(machineCode);
        machine.setMachineType(machineType);
        machine.setInstallDate(request.getInstallDate());

        Machine savedMachine = machineRepository.save(machine);

        return mapToMachineResponse(savedMachine);
    }

    private void validateMachineId(Long machineId) {
        if (machineId == null || machineId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid machine id");
        }
    }

    private void validatePrefix(String machineCode, MachineType machineType) {
        if (!machineCode.startsWith(machineType.getCodePrefix())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Machine code must start with " + machineType.getCodePrefix() + " for selected machine type");
        }
    }

    private User currentUser() {
        return userRepository.findByEmail(SecurityUtils.currentUserEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private MachineResponse mapToMachineResponse(Machine machine) {
        MachineType machineType = machine.getMachineType();
        User fabUser = machine.getFabUser();

        return new MachineResponse(
                machine.getMachineId(),
                machine.getMachineCode(),
                machineType == null ? null : machineType.getTypeId(),
                machineType == null ? null : machineType.getTypeName(),
                machine.getInstallDate(),
                fabUser == null ? null : fabUser.getEmpId()
        );
    }
}
