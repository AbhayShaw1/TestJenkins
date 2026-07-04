package com.app.ehps.checkup;

import com.app.ehps.alert.RiskAlertRepository;
import com.app.ehps.checkup.dto.AssignCheckupRequest;
import com.app.ehps.checkup.dto.CheckupAssignmentResponse;
import com.app.ehps.common.constant.Role;
import com.app.ehps.machine.Machine;
import com.app.ehps.machine.MachineRepository;
import com.app.ehps.machine.MachineType;
import com.app.ehps.user.User;
import com.app.ehps.user.UserRepository;
import com.app.ehps.work.TechnicianWork;
import com.app.ehps.work.TechnicianWorkRepository;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FabCheckupAssignmentServiceTest {

    private static final String FAB_EMAIL = "fab@ehps.com";
    private static final Long FAB_EMP_ID = 10000L;
    private static final Long MACHINE_ID = 1000L;
    private static final Long TECHNICIAN_ID = 10001L;
    private static final LocalDate WORK_DATE = LocalDate.of(2026, 7, 4);

    @Mock
    private MachineRepository machineRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TechnicianWorkRepository technicianWorkRepository;

    @Mock
    private RiskAlertRepository riskAlertRepository;

    private FabCheckupAssignmentService service() {
        return new FabCheckupAssignmentService(machineRepository, userRepository, technicianWorkRepository, riskAlertRepository);
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

    private Machine machine() {
        Machine machine = new Machine();
        machine.setMachineId(MACHINE_ID);
        machine.setMachineCode("LH-500");
        machine.setMachineType(lithographyType());
        machine.setFabUser(fabUser());
        return machine;
    }

    private User technician(String speciality) {
        User technician = new User();
        technician.setEmpId(TECHNICIAN_ID);
        technician.setName("Tech One");
        technician.setEmail("tech1@ehps.com");
        technician.setPassword("ENCODED_HASH");
        technician.setRole(Role.TECHNICIAN);
        technician.setSpeciality(speciality);
        return technician;
    }

    private AssignCheckupRequest request() {
        AssignCheckupRequest request = new AssignCheckupRequest();
        request.setMachineId(MACHINE_ID);
        request.setTechnicianId(TECHNICIAN_ID);
        request.setWorkDate(WORK_DATE);
        return request;
    }

    @Test
    void assignTechnicianForCheckup_success_savesCompletedFalseAndReturnsResponse() {
        when(userRepository.findByEmail(FAB_EMAIL)).thenReturn(Optional.of(fabUser()));
        when(machineRepository.findById(MACHINE_ID)).thenReturn(Optional.of(machine()));
        when(riskAlertRepository.existsByMachine_MachineIdAndStatusNotIn(MACHINE_ID, List.of("resolved", "rejected")))
                .thenReturn(false);
        when(userRepository.findById(TECHNICIAN_ID)).thenReturn(Optional.of(technician("lithography")));
        when(technicianWorkRepository.existsByMachine_MachineIdAndWorkTypeIgnoreCaseAndWorkDateAndCompletedFalse(
                MACHINE_ID, "checkup", WORK_DATE)).thenReturn(false);
        when(technicianWorkRepository.save(any(TechnicianWork.class))).thenAnswer(invocation -> {
            TechnicianWork work = invocation.getArgument(0);
            work.setWorkId(1L);
            return work;
        });

        CheckupAssignmentResponse response = service().assignTechnicianForCheckup(request());

        ArgumentCaptor<TechnicianWork> captor = ArgumentCaptor.forClass(TechnicianWork.class);
        verify(technicianWorkRepository).save(captor.capture());
        TechnicianWork saved = captor.getValue();

        assertThat(saved.getMachine().getMachineId()).isEqualTo(MACHINE_ID);
        assertThat(saved.getTechnician().getEmpId()).isEqualTo(TECHNICIAN_ID);
        assertThat(saved.getFabUser().getEmpId()).isEqualTo(FAB_EMP_ID);
        assertThat(saved.getWorkType()).isEqualTo("checkup");
        assertThat(saved.getWorkDate()).isEqualTo(WORK_DATE);
        assertThat(saved.getCompleted()).isFalse();

        assertThat(response.getWorkId()).isEqualTo(1L);
        assertThat(response.getMachineId()).isEqualTo(MACHINE_ID);
        assertThat(response.getTechnicianId()).isEqualTo(TECHNICIAN_ID);
        assertThat(response.getWorkType()).isEqualTo("checkup");
        assertThat(response.getWorkDate()).isEqualTo(WORK_DATE);
    }

    @Test
    void assignTechnicianForCheckup_machineNotFound_throws404() {
        when(userRepository.findByEmail(FAB_EMAIL)).thenReturn(Optional.of(fabUser()));
        when(machineRepository.findById(MACHINE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().assignTechnicianForCheckup(request()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(404);
                    assertThat(rse.getReason()).isEqualTo("Machine not found");
                });

        verify(technicianWorkRepository, never()).save(any());
    }

    @Test
    void assignTechnicianForCheckup_unresolvedAlert_throws400() {
        when(userRepository.findByEmail(FAB_EMAIL)).thenReturn(Optional.of(fabUser()));
        when(machineRepository.findById(MACHINE_ID)).thenReturn(Optional.of(machine()));
        when(riskAlertRepository.existsByMachine_MachineIdAndStatusNotIn(MACHINE_ID, List.of("resolved", "rejected")))
                .thenReturn(true);

        assertThatThrownBy(() -> service().assignTechnicianForCheckup(request()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(400);
                    assertThat(rse.getReason()).isEqualTo("Machine has unresolved risk alerts. Resolve them before assigning again");
                });

        verify(technicianWorkRepository, never()).save(any());
    }

    @Test
    void assignTechnicianForCheckup_technicianNotFound_throws404() {
        when(userRepository.findByEmail(FAB_EMAIL)).thenReturn(Optional.of(fabUser()));
        when(machineRepository.findById(MACHINE_ID)).thenReturn(Optional.of(machine()));
        when(riskAlertRepository.existsByMachine_MachineIdAndStatusNotIn(MACHINE_ID, List.of("resolved", "rejected")))
                .thenReturn(false);
        when(userRepository.findById(TECHNICIAN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().assignTechnicianForCheckup(request()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(404);
                    assertThat(rse.getReason()).isEqualTo("Technician not found");
                });

        verify(technicianWorkRepository, never()).save(any());
    }

    @Test
    void assignTechnicianForCheckup_userNotTechnician_throws400() {
        User nonTechnician = technician("lithography");
        nonTechnician.setRole(Role.MANAGER);

        when(userRepository.findByEmail(FAB_EMAIL)).thenReturn(Optional.of(fabUser()));
        when(machineRepository.findById(MACHINE_ID)).thenReturn(Optional.of(machine()));
        when(riskAlertRepository.existsByMachine_MachineIdAndStatusNotIn(MACHINE_ID, List.of("resolved", "rejected")))
                .thenReturn(false);
        when(userRepository.findById(TECHNICIAN_ID)).thenReturn(Optional.of(nonTechnician));

        assertThatThrownBy(() -> service().assignTechnicianForCheckup(request()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(400);
                    assertThat(rse.getReason()).isEqualTo("User is not a technician");
                });

        verify(technicianWorkRepository, never()).save(any());
    }

    @Test
    void assignTechnicianForCheckup_specialityMismatch_throws400() {
        when(userRepository.findByEmail(FAB_EMAIL)).thenReturn(Optional.of(fabUser()));
        when(machineRepository.findById(MACHINE_ID)).thenReturn(Optional.of(machine()));
        when(riskAlertRepository.existsByMachine_MachineIdAndStatusNotIn(MACHINE_ID, List.of("resolved", "rejected")))
                .thenReturn(false);
        when(userRepository.findById(TECHNICIAN_ID)).thenReturn(Optional.of(technician("etcher")));

        assertThatThrownBy(() -> service().assignTechnicianForCheckup(request()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(400);
                    assertThat(rse.getReason()).isEqualTo("Technician speciality does not match machine type");
                });

        verify(technicianWorkRepository, never()).save(any());
    }

    @Test
    void assignTechnicianForCheckup_duplicateActiveAssignment_throws409() {
        when(userRepository.findByEmail(FAB_EMAIL)).thenReturn(Optional.of(fabUser()));
        when(machineRepository.findById(MACHINE_ID)).thenReturn(Optional.of(machine()));
        when(riskAlertRepository.existsByMachine_MachineIdAndStatusNotIn(MACHINE_ID, List.of("resolved", "rejected")))
                .thenReturn(false);
        when(userRepository.findById(TECHNICIAN_ID)).thenReturn(Optional.of(technician("lithography")));
        when(technicianWorkRepository.existsByMachine_MachineIdAndWorkTypeIgnoreCaseAndWorkDateAndCompletedFalse(
                MACHINE_ID, "checkup", WORK_DATE)).thenReturn(true);

        assertThatThrownBy(() -> service().assignTechnicianForCheckup(request()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(409);
                    assertThat(rse.getReason()).isEqualTo("Machine already has an active checkup assignment");
                });

        verify(technicianWorkRepository, never()).save(any());
    }
}
