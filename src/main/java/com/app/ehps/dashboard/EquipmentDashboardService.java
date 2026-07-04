package com.app.ehps.dashboard;

import com.app.ehps.dashboard.dto.DashboardHistoryResponse;
import com.app.ehps.history.MachineHistory;
import com.app.ehps.history.MachineHistoryRepository;
import com.app.ehps.machine.Machine;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Equipment dashboard read model for manager/fab coordinator (BEHAVIOR-BASELINE.md §14). Ported
 * from legacy {@code EquipmentDashboardService.getDashboardHistory} — exact validation/messages.
 */
@Service
@Transactional(readOnly = true)
public class EquipmentDashboardService {

    private final MachineHistoryRepository machineHistoryRepository;

    public EquipmentDashboardService(MachineHistoryRepository machineHistoryRepository) {
        this.machineHistoryRepository = machineHistoryRepository;
    }

    public List<DashboardHistoryResponse> getDashboardHistory(
            LocalDate fromDate,
            LocalDate toDate,
            Long typeId) {

        validateDashboardRequest(fromDate, toDate, typeId);

        List<MachineHistory> histories;

        if (typeId == 0) {
            histories = machineHistoryRepository
                    .findByHistoryDateBetweenOrderByHistoryDateDesc(fromDate, toDate);
        } else {
            histories = machineHistoryRepository
                    .findByHistoryDateBetweenAndMachine_MachineType_TypeIdOrderByHistoryDateDesc(
                            fromDate, toDate, typeId);
        }

        List<DashboardHistoryResponse> responseList = new ArrayList<>();

        for (MachineHistory history : histories) {
            responseList.add(mapToDashboardHistoryResponse(history));
        }

        return responseList;
    }

    private void validateDashboardRequest(LocalDate fromDate, LocalDate toDate, Long typeId) {
        if (fromDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "From date is required");
        }

        if (toDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "To date is required");
        }

        if (fromDate.isAfter(toDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "From date cannot be after To date");
        }

        if (typeId == null || typeId < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid machine type id");
        }
    }

    private DashboardHistoryResponse mapToDashboardHistoryResponse(MachineHistory history) {
        Machine machine = history.getMachine();

        return new DashboardHistoryResponse(
                history.getHistoryId(),

                machine != null ? machine.getMachineId() : null,

                machine != null ? machine.getMachineCode() : null,

                machine != null && machine.getMachineType() != null
                        ? machine.getMachineType().getTypeName()
                        : null,

                history.getTechnician() != null ? history.getTechnician().getEmpId() : null,

                history.getTechnician() != null ? history.getTechnician().getName() : null,

                history.getAlert() != null ? history.getAlert().getAlertId() : null,

                history.getHistoryDate(),

                history.getIssue(),

                history.getRepairAction(),

                history.getObservations()
        );
    }
}
