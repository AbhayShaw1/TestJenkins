package com.app.ehps.machine;

import com.app.ehps.common.constant.Role;
import com.app.ehps.machine.dto.MachineResponse;
import com.app.ehps.machine.dto.MachineTypeResponse;
import com.app.ehps.machine.dto.ParameterRuleResponse;
import com.app.ehps.user.User;
import com.app.ehps.user.UserRepository;
import com.app.ehps.user.dto.TechnicianResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Shared machine-type reference data + candidate lists, consolidated from 4 legacy endpoints
 * (docs/API-CONTRACT.md "Machine types"; BEHAVIOR-BASELINE.md §6, §9, §11).
 */
@Service
@Transactional(readOnly = true)
public class MachineTypeService {

    private final MachineTypeRepository machineTypeRepository;
    private final MachineTypeParameterRepository machineTypeParameterRepository;
    private final MachineRepository machineRepository;
    private final UserRepository userRepository;

    public MachineTypeService(MachineTypeRepository machineTypeRepository,
                               MachineTypeParameterRepository machineTypeParameterRepository,
                               MachineRepository machineRepository,
                               UserRepository userRepository) {
        this.machineTypeRepository = machineTypeRepository;
        this.machineTypeParameterRepository = machineTypeParameterRepository;
        this.machineRepository = machineRepository;
        this.userRepository = userRepository;
    }

    public List<MachineTypeResponse> getAllTypes() {
        return machineTypeRepository.findAll()
                .stream()
                .map(type -> new MachineTypeResponse(type.getTypeId(), type.getTypeName()))
                .toList();
    }

    public List<ParameterRuleResponse> getParameters(Long typeId) {
        validateTypeId(typeId);

        List<MachineTypeParameter> parameters =
                machineTypeParameterRepository.findByMachineType_TypeIdOrderByParamIndex(typeId);

        if (parameters.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No parameters found for machine type");
        }

        return parameters.stream()
                .map(param -> new ParameterRuleResponse(
                        param.getParamIndex(),
                        param.getParamName(),
                        param.getUnit(),
                        param.getGoodDesc(),
                        param.getWarningDesc(),
                        param.getBadDesc()
                ))
                .toList();
    }

    public List<MachineResponse> getMachinesByType(Long typeId) {
        validateTypeId(typeId);

        if (!machineTypeRepository.existsById(typeId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Machine type not found");
        }

        return machineRepository.findByMachineType_TypeIdOrderByMachineId(typeId)
                .stream()
                .map(this::mapToMachineResponse)
                .toList();
    }

    public List<TechnicianResponse> getTechniciansByType(Long typeId) {
        validateTypeId(typeId);

        MachineType machineType = machineTypeRepository.findById(typeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Machine type not found"));

        return userRepository.findByRoleAndSpecialityIgnoreCase(Role.TECHNICIAN, machineType.getSpeciality())
                .stream()
                .map(this::mapToTechnicianResponse)
                .toList();
    }

    private void validateTypeId(Long typeId) {
        if (typeId == null || typeId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid machine type id");
        }
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
