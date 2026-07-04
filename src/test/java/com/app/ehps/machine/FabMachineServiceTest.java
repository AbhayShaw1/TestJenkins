package com.app.ehps.machine;

import com.app.ehps.common.constant.Role;
import com.app.ehps.machine.dto.AddMachineRequest;
import com.app.ehps.machine.dto.MachineResponse;
import com.app.ehps.machine.dto.UpdateMachineRequest;
import com.app.ehps.user.User;
import com.app.ehps.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FabMachineServiceTest {

    private static final String FAB_EMAIL = "fab@ehps.com";
    private static final Long FAB_EMP_ID = 10000L;

    @Mock
    private MachineRepository machineRepository;

    @Mock
    private MachineTypeRepository machineTypeRepository;

    @Mock
    private UserRepository userRepository;

    private FabMachineService fabMachineService() {
        return new FabMachineService(machineRepository, machineTypeRepository, userRepository);
    }

    @BeforeEach
    void seedSecurityContext() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(FAB_EMAIL, null));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private User fabUser() {
        User user = new User();
        user.setEmpId(FAB_EMP_ID);
        user.setName("Fab One");
        user.setEmail(FAB_EMAIL);
        user.setPassword("ENCODED_HASH");
        user.setRole(Role.FAB_COORDINATOR);
        return user;
    }

    private MachineType lithographyType() {
        MachineType type = new MachineType();
        type.setTypeId(1L);
        type.setTypeName("Lithography");
        type.setCodePrefix("LH");
        type.setSpeciality("lithography");
        type.setParamCount(5);
        return type;
    }

    // ---- addMachine ----

    @Test
    void addMachine_success_savesUppercasedTrimmedCodeAndReturnsResponse() {
        AddMachineRequest request = new AddMachineRequest();
        request.setMachineCode("  lh-100  ");
        request.setTypeId(1L);
        request.setInstallDate(LocalDate.of(2024, 1, 1));

        when(userRepository.findByEmail(FAB_EMAIL)).thenReturn(Optional.of(fabUser()));
        when(machineTypeRepository.findById(1L)).thenReturn(Optional.of(lithographyType()));
        when(machineRepository.existsByMachineCodeIgnoreCase("LH-100")).thenReturn(false);
        when(machineRepository.save(any(Machine.class))).thenAnswer(invocation -> {
            Machine m = invocation.getArgument(0);
            m.setMachineId(1000L);
            return m;
        });

        MachineResponse response = fabMachineService().addMachine(request);

        ArgumentCaptor<Machine> captor = ArgumentCaptor.forClass(Machine.class);
        verify(machineRepository).save(captor.capture());
        Machine saved = captor.getValue();

        assertThat(saved.getMachineCode()).isEqualTo("LH-100");
        assertThat(saved.getMachineType().getTypeId()).isEqualTo(1L);
        assertThat(saved.getFabUser().getEmpId()).isEqualTo(FAB_EMP_ID);
        assertThat(saved.getInstallDate()).isEqualTo(LocalDate.of(2024, 1, 1));

        assertThat(response.getMachineId()).isEqualTo(1000L);
        assertThat(response.getMachineCode()).isEqualTo("LH-100");
        assertThat(response.getTypeId()).isEqualTo(1L);
        assertThat(response.getTypeName()).isEqualTo("Lithography");
        assertThat(response.getFabCoordinatorId()).isEqualTo(FAB_EMP_ID);
    }

    @Test
    void addMachine_typeNotFound_throws404() {
        AddMachineRequest request = new AddMachineRequest();
        request.setMachineCode("LH-100");
        request.setTypeId(99L);

        when(userRepository.findByEmail(FAB_EMAIL)).thenReturn(Optional.of(fabUser()));
        when(machineTypeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fabMachineService().addMachine(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(404);
                    assertThat(rse.getReason()).isEqualTo("Machine type not found");
                });
    }

    @Test
    void addMachine_badPrefix_throws400() {
        AddMachineRequest request = new AddMachineRequest();
        request.setMachineCode("XX-100");
        request.setTypeId(1L);

        when(userRepository.findByEmail(FAB_EMAIL)).thenReturn(Optional.of(fabUser()));
        when(machineTypeRepository.findById(1L)).thenReturn(Optional.of(lithographyType()));

        assertThatThrownBy(() -> fabMachineService().addMachine(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(400);
                    assertThat(rse.getReason()).isEqualTo("Machine code must start with LH for selected machine type");
                });
    }

    @Test
    void addMachine_duplicateCode_throws409() {
        AddMachineRequest request = new AddMachineRequest();
        request.setMachineCode("LH-100");
        request.setTypeId(1L);

        when(userRepository.findByEmail(FAB_EMAIL)).thenReturn(Optional.of(fabUser()));
        when(machineTypeRepository.findById(1L)).thenReturn(Optional.of(lithographyType()));
        when(machineRepository.existsByMachineCodeIgnoreCase("LH-100")).thenReturn(true);

        assertThatThrownBy(() -> fabMachineService().addMachine(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(409);
                    assertThat(rse.getReason()).isEqualTo("Machine code already exists");
                });
    }

    // ---- getMachineById ----

    @Test
    void getMachineById_invalidId_throws400() {
        when(userRepository.findByEmail(FAB_EMAIL)).thenReturn(Optional.of(fabUser()));

        assertThatThrownBy(() -> fabMachineService().getMachineById(0L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(400);
                    assertThat(rse.getReason()).isEqualTo("Invalid machine id");
                });
    }

    @Test
    void getMachineById_notFound_throws404() {
        when(userRepository.findByEmail(FAB_EMAIL)).thenReturn(Optional.of(fabUser()));
        when(machineRepository.findByMachineIdAndFabUser_EmpId(eq(1000L), eq(FAB_EMP_ID)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> fabMachineService().getMachineById(1000L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(404);
                    assertThat(rse.getReason()).isEqualTo("Machine not found for this fab coordinator");
                });
    }

    // ---- updateMachine ----

    @Test
    void updateMachine_success_updatesFieldsAndReturnsResponse() {
        UpdateMachineRequest request = new UpdateMachineRequest();
        request.setMachineCode("  lh-200  ");
        request.setTypeId(1L);
        request.setInstallDate(LocalDate.of(2025, 2, 2));

        Machine existing = new Machine();
        existing.setMachineId(1000L);
        existing.setMachineCode("LH-100");
        existing.setMachineType(lithographyType());
        existing.setFabUser(fabUser());

        when(userRepository.findByEmail(FAB_EMAIL)).thenReturn(Optional.of(fabUser()));
        when(machineRepository.findByMachineIdAndFabUser_EmpId(eq(1000L), eq(FAB_EMP_ID)))
                .thenReturn(Optional.of(existing));
        when(machineTypeRepository.findById(1L)).thenReturn(Optional.of(lithographyType()));
        when(machineRepository.existsByMachineCodeIgnoreCaseAndMachineIdNot("LH-200", 1000L)).thenReturn(false);
        when(machineRepository.save(any(Machine.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MachineResponse response = fabMachineService().updateMachine(1000L, request);

        assertThat(response.getMachineCode()).isEqualTo("LH-200");
        assertThat(response.getInstallDate()).isEqualTo(LocalDate.of(2025, 2, 2));
        assertThat(response.getMachineId()).isEqualTo(1000L);
    }

    @Test
    void updateMachine_duplicateCode_throws409() {
        UpdateMachineRequest request = new UpdateMachineRequest();
        request.setMachineCode("LH-200");
        request.setTypeId(1L);

        Machine existing = new Machine();
        existing.setMachineId(1000L);
        existing.setMachineCode("LH-100");
        existing.setMachineType(lithographyType());
        existing.setFabUser(fabUser());

        when(userRepository.findByEmail(FAB_EMAIL)).thenReturn(Optional.of(fabUser()));
        when(machineRepository.findByMachineIdAndFabUser_EmpId(eq(1000L), eq(FAB_EMP_ID)))
                .thenReturn(Optional.of(existing));
        when(machineTypeRepository.findById(1L)).thenReturn(Optional.of(lithographyType()));
        when(machineRepository.existsByMachineCodeIgnoreCaseAndMachineIdNot("LH-200", 1000L)).thenReturn(true);

        assertThatThrownBy(() -> fabMachineService().updateMachine(1000L, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(409);
                    assertThat(rse.getReason()).isEqualTo("Machine code already exists");
                });
    }
}
