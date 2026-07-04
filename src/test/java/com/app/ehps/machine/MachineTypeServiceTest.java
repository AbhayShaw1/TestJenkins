package com.app.ehps.machine;

import com.app.ehps.common.constant.Role;
import com.app.ehps.machine.dto.MachineResponse;
import com.app.ehps.machine.dto.MachineTypeResponse;
import com.app.ehps.machine.dto.ParameterRuleResponse;
import com.app.ehps.user.User;
import com.app.ehps.user.UserRepository;
import com.app.ehps.user.dto.TechnicianResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MachineTypeServiceTest {

    @Mock
    private MachineTypeRepository machineTypeRepository;

    @Mock
    private MachineTypeParameterRepository machineTypeParameterRepository;

    @Mock
    private MachineRepository machineRepository;

    @Mock
    private UserRepository userRepository;

    private MachineTypeService service() {
        return new MachineTypeService(machineTypeRepository, machineTypeParameterRepository, machineRepository, userRepository);
    }

    private MachineType machineType(Long typeId, String typeName, String codePrefix, String speciality, Integer paramCount) {
        return new MachineType(typeId, typeName, codePrefix, speciality, paramCount);
    }

    // ---- getAllTypes ----

    @Test
    void getAllTypes_returnsAllSixMappedToTypeIdAndTypeName() {
        List<MachineType> types = List.of(
                machineType(1L, "Lithography", "LH", "lithography", 5),
                machineType(2L, "Etcher", "EH", "etcher", 5),
                machineType(3L, "CVD", "CVD", "cvd", 4),
                machineType(4L, "Ion Implanter", "ION", "ion_implanter", 4),
                machineType(5L, "CMP", "CMP", "cmp", 4),
                machineType(6L, "Inspection", "INS", "inspection", 4)
        );
        when(machineTypeRepository.findAll()).thenReturn(types);

        List<MachineTypeResponse> response = service().getAllTypes();

        assertThat(response).hasSize(6);
        assertThat(response.get(0).getTypeId()).isEqualTo(1L);
        assertThat(response.get(0).getTypeName()).isEqualTo("Lithography");
    }

    // ---- getParameters ----

    @Test
    void getParameters_invalidId_throws400() {
        assertThatThrownBy(() -> service().getParameters(null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(400);
                    assertThat(rse.getReason()).isEqualTo("Invalid machine type id");
                });

        assertThatThrownBy(() -> service().getParameters(0L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));

        assertThatThrownBy(() -> service().getParameters(-1L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void getParameters_empty_throws400() {
        when(machineTypeParameterRepository.findByMachineType_TypeIdOrderByParamIndex(99L))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> service().getParameters(99L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(400);
                    assertThat(rse.getReason()).isEqualTo("No parameters found for machine type");
                });
    }

    @Test
    void getParameters_success_mapsAllFields() {
        MachineType type = machineType(1L, "Lithography", "LH", "lithography", 5);
        MachineTypeParameter param = new MachineTypeParameter(
                1L, type, 1, "Light Intensity", "mW/cm²", "[80,100]", "[60,80)", "<60"
        );
        when(machineTypeParameterRepository.findByMachineType_TypeIdOrderByParamIndex(1L))
                .thenReturn(List.of(param));

        List<ParameterRuleResponse> response = service().getParameters(1L);

        assertThat(response).hasSize(1);
        ParameterRuleResponse ruleResponse = response.get(0);
        assertThat(ruleResponse.getParamIndex()).isEqualTo(1);
        assertThat(ruleResponse.getParamName()).isEqualTo("Light Intensity");
        assertThat(ruleResponse.getUnit()).isEqualTo("mW/cm²");
        assertThat(ruleResponse.getGood()).isEqualTo("[80,100]");
        assertThat(ruleResponse.getWarning()).isEqualTo("[60,80)");
        assertThat(ruleResponse.getBad()).isEqualTo("<60");
    }

    // ---- getMachinesByType ----

    @Test
    void getMachinesByType_invalidId_throws400() {
        assertThatThrownBy(() -> service().getMachinesByType(null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));

        assertThatThrownBy(() -> service().getMachinesByType(0L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void getMachinesByType_typeNotFound_throws404() {
        when(machineTypeRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service().getMachinesByType(99L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(404);
                    assertThat(rse.getReason()).isEqualTo("Machine type not found");
                });
    }

    @Test
    void getMachinesByType_success_mapsFields() {
        MachineType type = machineType(1L, "Lithography", "LH", "lithography", 5);
        User fabUser = new User();
        fabUser.setEmpId(10000L);

        Machine machine = new Machine();
        machine.setMachineId(1000L);
        machine.setMachineCode("LH-001");
        machine.setMachineType(type);
        machine.setInstallDate(LocalDate.of(2024, 1, 1));
        machine.setFabUser(fabUser);

        when(machineTypeRepository.existsById(1L)).thenReturn(true);
        when(machineRepository.findByMachineType_TypeIdOrderByMachineId(1L)).thenReturn(List.of(machine));

        List<MachineResponse> response = service().getMachinesByType(1L);

        assertThat(response).hasSize(1);
        MachineResponse machineResponse = response.get(0);
        assertThat(machineResponse.getMachineId()).isEqualTo(1000L);
        assertThat(machineResponse.getMachineCode()).isEqualTo("LH-001");
        assertThat(machineResponse.getTypeId()).isEqualTo(1L);
        assertThat(machineResponse.getTypeName()).isEqualTo("Lithography");
        assertThat(machineResponse.getInstallDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(machineResponse.getFabCoordinatorId()).isEqualTo(10000L);
    }

    @Test
    void getMachinesByType_success_nullFabUser_isNullSafe() {
        MachineType type = machineType(1L, "Lithography", "LH", "lithography", 5);

        Machine machine = new Machine();
        machine.setMachineId(1000L);
        machine.setMachineCode("LH-001");
        machine.setMachineType(type);
        machine.setInstallDate(LocalDate.of(2024, 1, 1));
        machine.setFabUser(null);

        when(machineTypeRepository.existsById(1L)).thenReturn(true);
        when(machineRepository.findByMachineType_TypeIdOrderByMachineId(1L)).thenReturn(List.of(machine));

        List<MachineResponse> response = service().getMachinesByType(1L);

        assertThat(response.get(0).getFabCoordinatorId()).isNull();
    }

    // ---- getTechniciansByType ----

    @Test
    void getTechniciansByType_invalidId_throws400() {
        assertThatThrownBy(() -> service().getTechniciansByType(null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void getTechniciansByType_typeNotFound_throws404() {
        when(machineTypeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getTechniciansByType(99L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(404);
                    assertThat(rse.getReason()).isEqualTo("Machine type not found");
                });
    }

    @Test
    void getTechniciansByType_success_matchesSpeciality() {
        MachineType type = machineType(1L, "Lithography", "LH", "lithography", 5);
        when(machineTypeRepository.findById(1L)).thenReturn(Optional.of(type));

        User technician = new User();
        technician.setEmpId(10001L);
        technician.setName("Tech One");
        technician.setEmail("tech1@ehps.com");
        technician.setPhone("9876543210");
        technician.setRole(Role.TECHNICIAN);
        technician.setSpeciality("lithography");

        when(userRepository.findByRoleAndSpecialityIgnoreCase(Role.TECHNICIAN, "lithography"))
                .thenReturn(List.of(technician));

        List<TechnicianResponse> response = service().getTechniciansByType(1L);

        assertThat(response).hasSize(1);
        TechnicianResponse technicianResponse = response.get(0);
        assertThat(technicianResponse.getName()).isEqualTo("Tech One");
        assertThat(technicianResponse.getEmail()).isEqualTo("tech1@ehps.com");
        assertThat(technicianResponse.getPhone()).isEqualTo("9876543210");
        assertThat(technicianResponse.getRole()).isEqualTo("technician");
        assertThat(technicianResponse.getSpeciality()).isEqualTo("lithography");
    }
}
