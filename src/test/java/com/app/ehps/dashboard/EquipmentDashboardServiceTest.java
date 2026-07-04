package com.app.ehps.dashboard;

import com.app.ehps.alert.RiskAlert;
import com.app.ehps.dashboard.dto.DashboardHistoryResponse;
import com.app.ehps.history.MachineHistory;
import com.app.ehps.history.MachineHistoryRepository;
import com.app.ehps.machine.Machine;
import com.app.ehps.machine.MachineType;
import com.app.ehps.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EquipmentDashboardServiceTest {

    private static final LocalDate FROM = LocalDate.of(2024, 1, 1);
    private static final LocalDate TO = LocalDate.of(2024, 1, 31);

    @Mock
    private MachineHistoryRepository machineHistoryRepository;

    private EquipmentDashboardService service() {
        return new EquipmentDashboardService(machineHistoryRepository);
    }

    private MachineHistory historyRow() {
        MachineType type = new MachineType();
        type.setTypeId(1L);
        type.setTypeName("Lithography");

        Machine machine = new Machine();
        machine.setMachineId(1000L);
        machine.setMachineCode("LH-100");
        machine.setMachineType(type);

        User technician = new User();
        technician.setEmpId(20002L);
        technician.setName("Technician One");

        RiskAlert alert = new RiskAlert();
        alert.setAlertId(5L);

        MachineHistory history = new MachineHistory();
        history.setHistoryId(1L);
        history.setMachine(machine);
        history.setTechnician(technician);
        history.setAlert(alert);
        history.setHistoryDate(LocalDate.of(2024, 1, 10));
        history.setIssue("Valve issue");
        history.setRepairAction("Replaced valve");
        history.setObservations("Stable now");
        return history;
    }

    // ---- validation ----

    @Test
    void getDashboardHistory_missingFromDate_throws400() {
        assertThatThrownBy(() -> service().getDashboardHistory(null, TO, 0L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(400);
                    assertThat(rse.getReason()).isEqualTo("From date is required");
                });
    }

    @Test
    void getDashboardHistory_missingToDate_throws400() {
        assertThatThrownBy(() -> service().getDashboardHistory(FROM, null, 0L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(400);
                    assertThat(rse.getReason()).isEqualTo("To date is required");
                });
    }

    @Test
    void getDashboardHistory_fromDateAfterToDate_throws400() {
        assertThatThrownBy(() -> service().getDashboardHistory(TO, FROM, 0L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(400);
                    assertThat(rse.getReason()).isEqualTo("From date cannot be after To date");
                });
    }

    @Test
    void getDashboardHistory_invalidTypeId_throws400() {
        assertThatThrownBy(() -> service().getDashboardHistory(FROM, TO, -1L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(400);
                    assertThat(rse.getReason()).isEqualTo("Invalid machine type id");
                });
    }

    @Test
    void getDashboardHistory_nullTypeId_throws400() {
        assertThatThrownBy(() -> service().getDashboardHistory(FROM, TO, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(400);
                    assertThat(rse.getReason()).isEqualTo("Invalid machine type id");
                });
    }

    // ---- typeId routing ----

    @Test
    void getDashboardHistory_typeIdZero_returnsAllTypesAndMapsFields() {
        when(machineHistoryRepository.findByHistoryDateBetweenOrderByHistoryDateDesc(FROM, TO))
                .thenReturn(List.of(historyRow()));

        List<DashboardHistoryResponse> result = service().getDashboardHistory(FROM, TO, 0L);

        verify(machineHistoryRepository).findByHistoryDateBetweenOrderByHistoryDateDesc(FROM, TO);
        verifyNoMoreInteractions(machineHistoryRepository);

        assertThat(result).hasSize(1);
        DashboardHistoryResponse response = result.get(0);
        assertThat(response.getHistoryId()).isEqualTo(1L);
        assertThat(response.getMachineId()).isEqualTo(1000L);
        assertThat(response.getMachineCode()).isEqualTo("LH-100");
        assertThat(response.getMachineType()).isEqualTo("Lithography");
        assertThat(response.getTechnicianId()).isEqualTo(20002L);
        assertThat(response.getTechnicianName()).isEqualTo("Technician One");
        assertThat(response.getAlertId()).isEqualTo(5L);
        assertThat(response.getHistoryDate()).isEqualTo(LocalDate.of(2024, 1, 10));
        assertThat(response.getIssue()).isEqualTo("Valve issue");
        assertThat(response.getRepairAction()).isEqualTo("Replaced valve");
        assertThat(response.getObservations()).isEqualTo("Stable now");
    }

    @Test
    void getDashboardHistory_typeIdPositive_filtersByMachineType() {
        when(machineHistoryRepository
                .findByHistoryDateBetweenAndMachine_MachineType_TypeIdOrderByHistoryDateDesc(FROM, TO, 1L))
                .thenReturn(List.of(historyRow()));

        List<DashboardHistoryResponse> result = service().getDashboardHistory(FROM, TO, 1L);

        verify(machineHistoryRepository)
                .findByHistoryDateBetweenAndMachine_MachineType_TypeIdOrderByHistoryDateDesc(FROM, TO, 1L);
        verifyNoMoreInteractions(machineHistoryRepository);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMachineType()).isEqualTo("Lithography");
    }

    @Test
    void getDashboardHistory_nullSafeOnMissingRelationships() {
        MachineHistory bareHistory = new MachineHistory();
        bareHistory.setHistoryId(2L);
        bareHistory.setHistoryDate(LocalDate.of(2024, 1, 15));

        when(machineHistoryRepository.findByHistoryDateBetweenOrderByHistoryDateDesc(any(), any()))
                .thenReturn(List.of(bareHistory));

        List<DashboardHistoryResponse> result = service().getDashboardHistory(FROM, TO, 0L);

        assertThat(result).hasSize(1);
        DashboardHistoryResponse response = result.get(0);
        assertThat(response.getMachineId()).isNull();
        assertThat(response.getMachineCode()).isNull();
        assertThat(response.getMachineType()).isNull();
        assertThat(response.getTechnicianId()).isNull();
        assertThat(response.getTechnicianName()).isNull();
        assertThat(response.getAlertId()).isNull();
    }
}
