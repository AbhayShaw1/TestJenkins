package com.app.ehps.repair;

import com.app.ehps.alert.RiskAlert;
import com.app.ehps.alert.RiskAlertRepository;
import com.app.ehps.checkup.dto.MachineDetailsResponse;
import com.app.ehps.common.constant.Role;
import com.app.ehps.history.MachineHistory;
import com.app.ehps.history.MachineHistoryRepository;
import com.app.ehps.machine.Machine;
import com.app.ehps.machine.MachineRepository;
import com.app.ehps.machine.MachineType;
import com.app.ehps.repair.dto.ApprovedRepairAlertResponse;
import com.app.ehps.repair.dto.CompleteRepairRequest;
import com.app.ehps.repair.dto.CompleteRepairResponse;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link TechnicianRepairService}, mirroring legacy
 * {@code TechnicianRepairService} behavior (BEHAVIOR-BASELINE.md §12).
 */
@ExtendWith(MockitoExtension.class)
class TechnicianRepairServiceTest {

    private static final String TECH_EMAIL = "tech1@ehps.com";
    private static final Long TECH_EMP_ID = 10001L;
    private static final Long MACHINE_ID = 1000L;
    private static final Long ALERT_ID = 500L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RiskAlertRepository riskAlertRepository;

    @Mock
    private MachineRepository machineRepository;

    @Mock
    private RepairRepository repairRepository;

    @Mock
    private MachineHistoryRepository machineHistoryRepository;

    private TechnicianRepairService service() {
        return new TechnicianRepairService(userRepository, riskAlertRepository, machineRepository,
                repairRepository, machineHistoryRepository);
    }

    @BeforeEach
    void seedSecurityContext() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(TECH_EMAIL, null));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private User technician() {
        User user = new User();
        user.setEmpId(TECH_EMP_ID);
        user.setName("Tech One");
        user.setEmail(TECH_EMAIL);
        user.setPassword("ENCODED_HASH");
        user.setRole(Role.TECHNICIAN);
        user.setSpeciality("lithography");
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
        machine.setMachineCode("LH-700");
        machine.setMachineType(lithographyType());
        machine.setInstallDate(LocalDate.of(2024, 1, 1));
        return machine;
    }

    private RiskAlert approvedAlert() {
        RiskAlert alert = new RiskAlert();
        alert.setAlertId(ALERT_ID);
        alert.setMachine(machine());
        alert.setProblemMeasure("Light Intensity: 40.0 mW/cm² (bad)");
        alert.setSeverity("high");
        alert.setStatus("approved");
        alert.setRaisedOn(LocalDate.now());
        alert.setAssignedTechnician(technician());
        return alert;
    }

    // ---- getApprovedRepairAlerts ----

    @Test
    void getApprovedRepairAlerts_mapsAlertsToResponses() {
        when(userRepository.findByEmail(TECH_EMAIL)).thenReturn(Optional.of(technician()));
        when(riskAlertRepository.findByAssignedTechnician_EmpIdAndStatusOrderByRaisedOnDesc(TECH_EMP_ID, "approved"))
                .thenReturn(List.of(approvedAlert()));

        List<ApprovedRepairAlertResponse> result = service().getApprovedRepairAlerts();

        assertThat(result).hasSize(1);
        ApprovedRepairAlertResponse response = result.get(0);
        assertThat(response.getAlertId()).isEqualTo(ALERT_ID);
        assertThat(response.getMachineId()).isEqualTo(MACHINE_ID);
        assertThat(response.getMachineCode()).isEqualTo("LH-700");
        assertThat(response.getSeverity()).isEqualTo("high");
        assertThat(response.getStatus()).isEqualTo("approved");
    }

    // ---- getApprovedRepairAlert ----

    @Test
    void getApprovedRepairAlert_invalidId_throws400() {
        assertThatThrownBy(() -> service().getApprovedRepairAlert(0L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(400);
                    assertThat(rse.getReason()).isEqualTo("Invalid alert id");
                });
    }

    @Test
    void getApprovedRepairAlert_notAssigned_throws403() {
        when(userRepository.findByEmail(TECH_EMAIL)).thenReturn(Optional.of(technician()));
        when(riskAlertRepository.findByAlertIdAndAssignedTechnician_EmpIdAndStatus(ALERT_ID, TECH_EMP_ID, "approved"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getApprovedRepairAlert(ALERT_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(403);
                    assertThat(rse.getReason()).isEqualTo("Alert is not assigned to this technician");
                });
    }

    @Test
    void getApprovedRepairAlert_success_returnsAlert() {
        when(userRepository.findByEmail(TECH_EMAIL)).thenReturn(Optional.of(technician()));
        when(riskAlertRepository.findByAlertIdAndAssignedTechnician_EmpIdAndStatus(ALERT_ID, TECH_EMP_ID, "approved"))
                .thenReturn(Optional.of(approvedAlert()));

        ApprovedRepairAlertResponse response = service().getApprovedRepairAlert(ALERT_ID);

        assertThat(response.getAlertId()).isEqualTo(ALERT_ID);
        assertThat(response.getMachineCode()).isEqualTo("LH-700");
    }

    // ---- getMachineDetails ----

    @Test
    void getMachineDetails_invalidId_throws400() {
        assertThatThrownBy(() -> service().getMachineDetails(-1L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(400);
                    assertThat(rse.getReason()).isEqualTo("Invalid machine id");
                });
    }

    @Test
    void getMachineDetails_notFound_throws404() {
        when(machineRepository.findById(MACHINE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getMachineDetails(MACHINE_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(404);
                    assertThat(rse.getReason()).isEqualTo("Machine not found");
                });
    }

    @Test
    void getMachineDetails_success_returnsMachineDetails() {
        when(machineRepository.findById(MACHINE_ID)).thenReturn(Optional.of(machine()));

        MachineDetailsResponse response = service().getMachineDetails(MACHINE_ID);

        assertThat(response.getMachineId()).isEqualTo(MACHINE_ID);
        assertThat(response.getMachineCode()).isEqualTo("LH-700");
        assertThat(response.getTypeId()).isEqualTo(1L);
        assertThat(response.getTypeName()).isEqualTo("Lithography");
    }

    // ---- completeRepair ----

    @Test
    void completeRepair_notAssigned_throws403() {
        when(userRepository.findByEmail(TECH_EMAIL)).thenReturn(Optional.of(technician()));
        when(riskAlertRepository.findByAlertIdAndAssignedTechnician_EmpIdAndStatus(ALERT_ID, TECH_EMP_ID, "approved"))
                .thenReturn(Optional.empty());

        CompleteRepairRequest request = new CompleteRepairRequest();
        request.setChangesDone("Replaced lens");
        request.setObservations("All good");

        assertThatThrownBy(() -> service().completeRepair(ALERT_ID, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(403);
                    assertThat(rse.getReason()).isEqualTo("Alert is not assigned to this technician");
                });
    }

    @Test
    void completeRepair_success_savesRepairAndHistoryAndResolvesAlert() {
        when(userRepository.findByEmail(TECH_EMAIL)).thenReturn(Optional.of(technician()));
        when(riskAlertRepository.findByAlertIdAndAssignedTechnician_EmpIdAndStatus(ALERT_ID, TECH_EMP_ID, "approved"))
                .thenReturn(Optional.of(approvedAlert()));
        when(riskAlertRepository.save(any(RiskAlert.class))).thenAnswer(inv -> inv.getArgument(0));

        CompleteRepairRequest request = new CompleteRepairRequest();
        request.setChangesDone("  Replaced lens  ");
        request.setObservations("  All good  ");

        CompleteRepairResponse response = service().completeRepair(ALERT_ID, request);

        assertThat(response.getAlertId()).isEqualTo(ALERT_ID);
        assertThat(response.getMachineId()).isEqualTo(MACHINE_ID);
        assertThat(response.getMachineCode()).isEqualTo("LH-700");
        assertThat(response.getAlertStatus()).isEqualTo("resolved");
        assertThat(response.isRepairRecorded()).isTrue();
        assertThat(response.isHistoryRecorded()).isTrue();

        ArgumentCaptor<Repair> repairCaptor = ArgumentCaptor.forClass(Repair.class);
        verify(repairRepository).save(repairCaptor.capture());
        Repair savedRepair = repairCaptor.getValue();
        assertThat(savedRepair.getChangesDone()).isEqualTo("Replaced lens");
        assertThat(savedRepair.getObservations()).isEqualTo("All good");
        assertThat(savedRepair.getMachine().getMachineId()).isEqualTo(MACHINE_ID);
        assertThat(savedRepair.getTechnician().getEmpId()).isEqualTo(TECH_EMP_ID);
        assertThat(savedRepair.getRepairDate()).isEqualTo(LocalDate.now());

        ArgumentCaptor<MachineHistory> historyCaptor = ArgumentCaptor.forClass(MachineHistory.class);
        verify(machineHistoryRepository).save(historyCaptor.capture());
        MachineHistory savedHistory = historyCaptor.getValue();
        assertThat(savedHistory.getIssue()).isEqualTo("Light Intensity: 40.0 mW/cm² (bad)");
        assertThat(savedHistory.getRepairAction()).isEqualTo("Replaced lens");
        assertThat(savedHistory.getObservations()).isEqualTo("All good");
        assertThat(savedHistory.getHistoryDate()).isEqualTo(LocalDate.now());

        ArgumentCaptor<RiskAlert> alertCaptor = ArgumentCaptor.forClass(RiskAlert.class);
        verify(riskAlertRepository).save(alertCaptor.capture());
        assertThat(alertCaptor.getValue().getStatus()).isEqualTo("resolved");
    }
}
