package com.app.ehps.alert;

import com.app.ehps.alert.dto.AlertResponse;
import com.app.ehps.alert.dto.AssignRepairRequest;
import com.app.ehps.alert.dto.AssignRepairResponse;
import com.app.ehps.alert.dto.EscalationResponse;
import com.app.ehps.common.constant.Role;
import com.app.ehps.machine.Machine;
import com.app.ehps.machine.MachineType;
import com.app.ehps.user.User;
import com.app.ehps.user.UserRepository;
import com.app.ehps.user.dto.TechnicianResponse;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FabAlertServiceTest {

    private static final String FAB_EMAIL = "fab@ehps.com";
    private static final Long FAB_EMP_ID = 10000L;
    private static final Long MACHINE_ID = 1000L;
    private static final Long ALERT_ID = 1L;
    private static final Long TECHNICIAN_ID = 10001L;
    private static final LocalDate REPAIR_DATE = LocalDate.of(2026, 7, 4);

    @Mock
    private RiskAlertRepository riskAlertRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TechnicianWorkRepository technicianWorkRepository;

    private FabAlertService service() {
        return new FabAlertService(riskAlertRepository, userRepository, technicianWorkRepository);
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

    private RiskAlert alert(String status, boolean assigned) {
        RiskAlert alert = new RiskAlert();
        alert.setAlertId(ALERT_ID);
        alert.setMachine(machine());
        alert.setProblemMeasure("value:80.0(bad)");
        alert.setSeverity("high");
        alert.setStatus(status);
        alert.setRaisedOn(LocalDate.of(2026, 7, 1));
        alert.setFabUser(fabUser());
        if (assigned) {
            alert.setAssignedTechnician(technician("lithography"));
        }
        return alert;
    }

    private AssignRepairRequest repairRequest() {
        AssignRepairRequest request = new AssignRepairRequest();
        request.setTechnicianId(TECHNICIAN_ID);
        request.setRepairDate(REPAIR_DATE);
        return request;
    }

    // ---- getPendingAlerts ----

    @Test
    void getPendingAlerts_returnsMappedList() {
        when(userRepository.findByEmail(FAB_EMAIL)).thenReturn(Optional.of(fabUser()));
        when(riskAlertRepository.findByFabUser_EmpIdAndStatusOrderByRaisedOnDesc(FAB_EMP_ID, "pending"))
                .thenReturn(List.of(alert("pending", false)));

        List<AlertResponse> responses = service().getPendingAlerts();

        assertThat(responses).hasSize(1);
        AlertResponse response = responses.get(0);
        assertThat(response.getAlertId()).isEqualTo(ALERT_ID);
        assertThat(response.getMachineId()).isEqualTo(MACHINE_ID);
        assertThat(response.getMachineCode()).isEqualTo("LH-500");
        assertThat(response.getStatus()).isEqualTo("pending");
        assertThat(response.getFabCoordinatorId()).isEqualTo(FAB_EMP_ID);
        assertThat(response.getApprovedById()).isNull();
        assertThat(response.getAssignedTechnicianId()).isNull();
    }

    @Test
    void getPendingAlerts_userNotFound_throws401() {
        when(userRepository.findByEmail(FAB_EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getPendingAlerts())
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(401);
                    assertThat(rse.getReason()).isEqualTo("User not found");
                });
    }

    // ---- sendToManager ----

    @Test
    void sendToManager_success_setsStatusSentToManager() {
        when(userRepository.findByEmail(FAB_EMAIL)).thenReturn(Optional.of(fabUser()));
        when(riskAlertRepository.findByAlertIdAndFabUser_EmpIdAndStatus(ALERT_ID, FAB_EMP_ID, "pending"))
                .thenReturn(Optional.of(alert("pending", false)));
        when(riskAlertRepository.save(any(RiskAlert.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EscalationResponse response = service().sendToManager(ALERT_ID);

        ArgumentCaptor<RiskAlert> captor = ArgumentCaptor.forClass(RiskAlert.class);
        verify(riskAlertRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("sent_to_manager");

        assertThat(response.getAlertId()).isEqualTo(ALERT_ID);
        assertThat(response.getUpdatedStatus()).isEqualTo("sent_to_manager");
    }

    @Test
    void sendToManager_invalidAlertId_throws400() {
        when(userRepository.findByEmail(FAB_EMAIL)).thenReturn(Optional.of(fabUser()));

        assertThatThrownBy(() -> service().sendToManager(0L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(400);
                    assertThat(rse.getReason()).isEqualTo("Invalid alert id");
                });

        verify(riskAlertRepository, never()).save(any());
    }

    @Test
    void sendToManager_notFound_throws404() {
        when(userRepository.findByEmail(FAB_EMAIL)).thenReturn(Optional.of(fabUser()));
        when(riskAlertRepository.findByAlertIdAndFabUser_EmpIdAndStatus(ALERT_ID, FAB_EMP_ID, "pending"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().sendToManager(ALERT_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(404);
                    assertThat(rse.getReason()).isEqualTo("Pending alert not found");
                });

        verify(riskAlertRepository, never()).save(any());
    }

    // ---- getApprovedUnassignedAlerts ----

    @Test
    void getApprovedUnassignedAlerts_returnsMappedList() {
        when(userRepository.findByEmail(FAB_EMAIL)).thenReturn(Optional.of(fabUser()));
        when(riskAlertRepository
                .findByFabUser_EmpIdAndStatusAndAssignedTechnicianIsNullOrderByRaisedOnDesc(FAB_EMP_ID, "approved"))
                .thenReturn(List.of(alert("approved", false)));

        List<AlertResponse> responses = service().getApprovedUnassignedAlerts();

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getStatus()).isEqualTo("approved");
    }

    // ---- getMatchingTechnicians ----

    @Test
    void getMatchingTechnicians_success_returnsSpecialityMatchedTechnicians() {
        when(userRepository.findByEmail(FAB_EMAIL)).thenReturn(Optional.of(fabUser()));
        when(riskAlertRepository.findByAlertIdAndFabUser_EmpIdAndStatusAndAssignedTechnicianIsNull(
                ALERT_ID, FAB_EMP_ID, "approved")).thenReturn(Optional.of(alert("approved", false)));
        when(userRepository.findByRoleAndSpecialityIgnoreCase(Role.TECHNICIAN, "lithography"))
                .thenReturn(List.of(technician("lithography")));

        List<TechnicianResponse> responses = service().getMatchingTechnicians(ALERT_ID);

        assertThat(responses).hasSize(1);
        TechnicianResponse response = responses.get(0);
        assertThat(response.getTechnicianId()).isEqualTo(TECHNICIAN_ID);
        assertThat(response.getSpeciality()).isEqualTo("lithography");
        assertThat(response.getRole()).isEqualTo("technician");
    }

    @Test
    void getMatchingTechnicians_invalidAlertId_throws400() {
        when(userRepository.findByEmail(FAB_EMAIL)).thenReturn(Optional.of(fabUser()));

        assertThatThrownBy(() -> service().getMatchingTechnicians(-1L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(400);
                    assertThat(rse.getReason()).isEqualTo("Invalid alert id");
                });
    }

    @Test
    void getMatchingTechnicians_notFound_throws404() {
        when(userRepository.findByEmail(FAB_EMAIL)).thenReturn(Optional.of(fabUser()));
        when(riskAlertRepository.findByAlertIdAndFabUser_EmpIdAndStatusAndAssignedTechnicianIsNull(
                ALERT_ID, FAB_EMP_ID, "approved")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getMatchingTechnicians(ALERT_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(404);
                    assertThat(rse.getReason()).isEqualTo("Approved unassigned alert not found");
                });
    }

    // ---- assignRepairTechnician ----

    @Test
    void assignRepairTechnician_success_savesCompletedFalseWorkAndAssignsAlert() {
        when(userRepository.findByEmail(FAB_EMAIL)).thenReturn(Optional.of(fabUser()));
        when(riskAlertRepository.findByAlertIdAndFabUser_EmpIdAndStatusAndAssignedTechnicianIsNull(
                ALERT_ID, FAB_EMP_ID, "approved")).thenReturn(Optional.of(alert("approved", false)));
        when(userRepository.findById(TECHNICIAN_ID)).thenReturn(Optional.of(technician("lithography")));
        when(technicianWorkRepository
                .existsByMachine_MachineIdAndTechnician_EmpIdAndWorkTypeIgnoreCaseAndWorkDate(
                        MACHINE_ID, TECHNICIAN_ID, "repair", REPAIR_DATE)).thenReturn(false);
        when(riskAlertRepository.save(any(RiskAlert.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(technicianWorkRepository.save(any(TechnicianWork.class))).thenAnswer(invocation -> {
            TechnicianWork work = invocation.getArgument(0);
            work.setWorkId(1L);
            return work;
        });

        AssignRepairResponse response = service().assignRepairTechnician(ALERT_ID, repairRequest());

        ArgumentCaptor<TechnicianWork> workCaptor = ArgumentCaptor.forClass(TechnicianWork.class);
        verify(technicianWorkRepository).save(workCaptor.capture());
        TechnicianWork savedWork = workCaptor.getValue();

        assertThat(savedWork.getMachine().getMachineId()).isEqualTo(MACHINE_ID);
        assertThat(savedWork.getTechnician().getEmpId()).isEqualTo(TECHNICIAN_ID);
        assertThat(savedWork.getFabUser().getEmpId()).isEqualTo(FAB_EMP_ID);
        assertThat(savedWork.getWorkType()).isEqualTo("repair");
        assertThat(savedWork.getWorkDate()).isEqualTo(REPAIR_DATE);
        assertThat(savedWork.getCompleted()).isFalse();

        ArgumentCaptor<RiskAlert> alertCaptor = ArgumentCaptor.forClass(RiskAlert.class);
        verify(riskAlertRepository).save(alertCaptor.capture());
        assertThat(alertCaptor.getValue().getAssignedTechnician().getEmpId()).isEqualTo(TECHNICIAN_ID);

        assertThat(response.getAlertId()).isEqualTo(ALERT_ID);
        assertThat(response.getTechnicianId()).isEqualTo(TECHNICIAN_ID);
        assertThat(response.getMachineId()).isEqualTo(MACHINE_ID);
        assertThat(response.getWorkType()).isEqualTo("repair");
        assertThat(response.getWorkDate()).isEqualTo(REPAIR_DATE);
    }

    @Test
    void assignRepairTechnician_invalidAlertId_throws400() {
        when(userRepository.findByEmail(FAB_EMAIL)).thenReturn(Optional.of(fabUser()));

        assertThatThrownBy(() -> service().assignRepairTechnician(0L, repairRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(400);
                    assertThat(rse.getReason()).isEqualTo("Invalid alert id");
                });

        verify(technicianWorkRepository, never()).save(any());
    }

    @Test
    void assignRepairTechnician_alertNotFound_throws404() {
        when(userRepository.findByEmail(FAB_EMAIL)).thenReturn(Optional.of(fabUser()));
        when(riskAlertRepository.findByAlertIdAndFabUser_EmpIdAndStatusAndAssignedTechnicianIsNull(
                ALERT_ID, FAB_EMP_ID, "approved")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().assignRepairTechnician(ALERT_ID, repairRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(404);
                    assertThat(rse.getReason()).isEqualTo("Approved unassigned alert not found");
                });

        verify(technicianWorkRepository, never()).save(any());
    }

    @Test
    void assignRepairTechnician_technicianNotFound_throws404() {
        when(userRepository.findByEmail(FAB_EMAIL)).thenReturn(Optional.of(fabUser()));
        when(riskAlertRepository.findByAlertIdAndFabUser_EmpIdAndStatusAndAssignedTechnicianIsNull(
                ALERT_ID, FAB_EMP_ID, "approved")).thenReturn(Optional.of(alert("approved", false)));
        when(userRepository.findById(TECHNICIAN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().assignRepairTechnician(ALERT_ID, repairRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(404);
                    assertThat(rse.getReason()).isEqualTo("Technician not found");
                });

        verify(technicianWorkRepository, never()).save(any());
    }

    @Test
    void assignRepairTechnician_userNotTechnician_throws400() {
        User nonTechnician = technician("lithography");
        nonTechnician.setRole(Role.MANAGER);

        when(userRepository.findByEmail(FAB_EMAIL)).thenReturn(Optional.of(fabUser()));
        when(riskAlertRepository.findByAlertIdAndFabUser_EmpIdAndStatusAndAssignedTechnicianIsNull(
                ALERT_ID, FAB_EMP_ID, "approved")).thenReturn(Optional.of(alert("approved", false)));
        when(userRepository.findById(TECHNICIAN_ID)).thenReturn(Optional.of(nonTechnician));

        assertThatThrownBy(() -> service().assignRepairTechnician(ALERT_ID, repairRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(400);
                    assertThat(rse.getReason()).isEqualTo("User is not technician");
                });

        verify(technicianWorkRepository, never()).save(any());
    }

    @Test
    void assignRepairTechnician_specialityMismatch_throws400() {
        when(userRepository.findByEmail(FAB_EMAIL)).thenReturn(Optional.of(fabUser()));
        when(riskAlertRepository.findByAlertIdAndFabUser_EmpIdAndStatusAndAssignedTechnicianIsNull(
                ALERT_ID, FAB_EMP_ID, "approved")).thenReturn(Optional.of(alert("approved", false)));
        when(userRepository.findById(TECHNICIAN_ID)).thenReturn(Optional.of(technician("etcher")));

        assertThatThrownBy(() -> service().assignRepairTechnician(ALERT_ID, repairRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(400);
                    assertThat(rse.getReason()).isEqualTo("Technician speciality mismatch");
                });

        verify(technicianWorkRepository, never()).save(any());
    }

    @Test
    void assignRepairTechnician_duplicateRepairWork_throws409() {
        when(userRepository.findByEmail(FAB_EMAIL)).thenReturn(Optional.of(fabUser()));
        when(riskAlertRepository.findByAlertIdAndFabUser_EmpIdAndStatusAndAssignedTechnicianIsNull(
                ALERT_ID, FAB_EMP_ID, "approved")).thenReturn(Optional.of(alert("approved", false)));
        when(userRepository.findById(TECHNICIAN_ID)).thenReturn(Optional.of(technician("lithography")));
        when(technicianWorkRepository
                .existsByMachine_MachineIdAndTechnician_EmpIdAndWorkTypeIgnoreCaseAndWorkDate(
                        MACHINE_ID, TECHNICIAN_ID, "repair", REPAIR_DATE)).thenReturn(true);

        assertThatThrownBy(() -> service().assignRepairTechnician(ALERT_ID, repairRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(409);
                    assertThat(rse.getReason()).isEqualTo("Repair assignment already exists");
                });

        verify(technicianWorkRepository, never()).save(any());
    }
}
