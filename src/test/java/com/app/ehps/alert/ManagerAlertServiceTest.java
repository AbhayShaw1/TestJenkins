package com.app.ehps.alert;

import com.app.ehps.alert.dto.AlertActionResponse;
import com.app.ehps.alert.dto.AlertResponse;
import com.app.ehps.common.constant.Role;
import com.app.ehps.machine.Machine;
import com.app.ehps.machine.MachineType;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManagerAlertServiceTest {

    private static final String MANAGER_EMAIL = "manager@ehps.com";
    private static final Long MANAGER_EMP_ID = 10000L;
    private static final Long ALERT_ID = 1L;
    private static final Long MACHINE_ID = 1000L;

    @Mock
    private RiskAlertRepository riskAlertRepository;

    @Mock
    private UserRepository userRepository;

    private ManagerAlertService service() {
        return new ManagerAlertService(riskAlertRepository, userRepository);
    }

    @BeforeEach
    void seedSecurityContext() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(MANAGER_EMAIL, null));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private User manager() {
        User user = new User();
        user.setEmpId(MANAGER_EMP_ID);
        user.setName("Manager One");
        user.setEmail(MANAGER_EMAIL);
        user.setPassword("ENCODED_HASH");
        user.setRole(Role.MANAGER);
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
        return machine;
    }

    private RiskAlert alert(String status) {
        RiskAlert alert = new RiskAlert();
        alert.setAlertId(ALERT_ID);
        alert.setMachine(machine());
        alert.setProblemMeasure("Light Intensity: bad");
        alert.setSeverity("high");
        alert.setStatus(status);
        alert.setRaisedOn(LocalDate.of(2026, 7, 4));
        return alert;
    }

    @Test
    void getAllAlerts_returnsMappedAlerts_excludingPendingAndResolved() {
        when(riskAlertRepository.findByStatusNotInOrderByAlertIdDesc(List.of("pending", "resolved")))
                .thenReturn(List.of(alert("sent_to_manager")));

        List<AlertResponse> result = service().getAllAlerts();

        assertThat(result).hasSize(1);
        AlertResponse response = result.get(0);
        assertThat(response.getAlertId()).isEqualTo(ALERT_ID);
        assertThat(response.getMachineId()).isEqualTo(MACHINE_ID);
        assertThat(response.getMachineCode()).isEqualTo("LH-500");
        assertThat(response.getStatus()).isEqualTo("sent_to_manager");
    }

    @Test
    void getPendingManagerApprovalAlerts_returnsMappedAlerts() {
        when(riskAlertRepository.findByStatusOrderByRaisedOnDesc("sent_to_manager"))
                .thenReturn(List.of(alert("sent_to_manager")));

        List<AlertResponse> result = service().getPendingManagerApprovalAlerts();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("sent_to_manager");
    }

    @Test
    void approveAlert_success_setsApprovedStatusAndApprovedBy() {
        when(userRepository.findByEmail(MANAGER_EMAIL)).thenReturn(Optional.of(manager()));
        when(riskAlertRepository.findByAlertIdAndStatus(ALERT_ID, "sent_to_manager"))
                .thenReturn(Optional.of(alert("sent_to_manager")));
        when(riskAlertRepository.save(any(RiskAlert.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AlertActionResponse response = service().approveAlert(ALERT_ID);

        ArgumentCaptor<RiskAlert> captor = ArgumentCaptor.forClass(RiskAlert.class);
        verify(riskAlertRepository).save(captor.capture());
        RiskAlert saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo("approved");
        assertThat(saved.getApprovedBy().getEmpId()).isEqualTo(MANAGER_EMP_ID);

        assertThat(response.getAlertId()).isEqualTo(ALERT_ID);
        assertThat(response.getUpdatedStatus()).isEqualTo("approved");
        assertThat(response.getManagerId()).isEqualTo(MANAGER_EMP_ID);
    }

    @Test
    void approveAlert_notFound_throws404() {
        when(userRepository.findByEmail(MANAGER_EMAIL)).thenReturn(Optional.of(manager()));
        when(riskAlertRepository.findByAlertIdAndStatus(ALERT_ID, "sent_to_manager"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().approveAlert(ALERT_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(404);
                    assertThat(rse.getReason()).isEqualTo("Alert waiting for manager approval not found");
                });

        verify(riskAlertRepository, never()).save(any());
    }

    @Test
    void approveAlert_invalidId_throws400() {
        when(userRepository.findByEmail(MANAGER_EMAIL)).thenReturn(Optional.of(manager()));
        assertThatThrownBy(() -> service().approveAlert(0L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(400);
                    assertThat(rse.getReason()).isEqualTo("Invalid alert id");
                });

        verify(riskAlertRepository, never()).save(any());
    }

    @Test
    void rejectAlert_success_setsRejectedStatusAndApprovedBy() {
        when(userRepository.findByEmail(MANAGER_EMAIL)).thenReturn(Optional.of(manager()));
        when(riskAlertRepository.findByAlertIdAndStatus(ALERT_ID, "sent_to_manager"))
                .thenReturn(Optional.of(alert("sent_to_manager")));
        when(riskAlertRepository.save(any(RiskAlert.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AlertActionResponse response = service().rejectAlert(ALERT_ID);

        ArgumentCaptor<RiskAlert> captor = ArgumentCaptor.forClass(RiskAlert.class);
        verify(riskAlertRepository).save(captor.capture());
        RiskAlert saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo("rejected");
        assertThat(saved.getApprovedBy().getEmpId()).isEqualTo(MANAGER_EMP_ID);

        assertThat(response.getAlertId()).isEqualTo(ALERT_ID);
        assertThat(response.getUpdatedStatus()).isEqualTo("rejected");
        assertThat(response.getManagerId()).isEqualTo(MANAGER_EMP_ID);
    }

    @Test
    void rejectAlert_invalidId_throws400() {
        when(userRepository.findByEmail(MANAGER_EMAIL)).thenReturn(Optional.of(manager()));
        assertThatThrownBy(() -> service().rejectAlert(-1L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(400);
                    assertThat(rse.getReason()).isEqualTo("Invalid alert id");
                });

        verify(riskAlertRepository, never()).save(any());
    }
}
