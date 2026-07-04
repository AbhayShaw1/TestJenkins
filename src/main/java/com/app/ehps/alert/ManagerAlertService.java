package com.app.ehps.alert;

import com.app.ehps.alert.dto.AlertActionResponse;
import com.app.ehps.alert.dto.AlertResponse;
import com.app.ehps.common.security.SecurityUtils;
import com.app.ehps.user.User;
import com.app.ehps.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

/**
 * Manager alert lifecycle — reviewing and approving/rejecting risk alerts escalated by fab
 * coordinators (BEHAVIOR-BASELINE.md §10). Ported from legacy
 * {@code com.app.ehps_api.service.ManagerAlertService}, preserving exact statuses and messages.
 */
@Service
@Transactional
public class ManagerAlertService {

    private static final String STATUS_SENT_TO_MANAGER = "sent_to_manager";
    private static final String STATUS_APPROVED = "approved";
    private static final String STATUS_REJECTED = "rejected";

    private final RiskAlertRepository riskAlertRepository;
    private final UserRepository userRepository;

    public ManagerAlertService(RiskAlertRepository riskAlertRepository, UserRepository userRepository) {
        this.riskAlertRepository = riskAlertRepository;
        this.userRepository = userRepository;
    }

    public List<AlertResponse> getAllAlerts() {
        List<RiskAlert> alerts = riskAlertRepository.findByStatusNotInOrderByAlertIdDesc(
                List.of("pending", "resolved"));
        return mapAlerts(alerts);
    }

    public List<AlertResponse> getPendingManagerApprovalAlerts() {
        List<RiskAlert> alerts = riskAlertRepository.findByStatusOrderByRaisedOnDesc(STATUS_SENT_TO_MANAGER);
        return mapAlerts(alerts);
    }

    public AlertActionResponse approveAlert(Long alertId) {
        User manager = getLoggedInManager();

        validateAlertId(alertId);

        RiskAlert alert = riskAlertRepository
                .findByAlertIdAndStatus(alertId, STATUS_SENT_TO_MANAGER)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Alert waiting for manager approval not found"));

        alert.setStatus(STATUS_APPROVED);
        alert.setApprovedBy(manager);

        RiskAlert updatedAlert = riskAlertRepository.save(alert);

        return new AlertActionResponse(updatedAlert.getAlertId(), updatedAlert.getStatus(), manager.getEmpId());
    }

    public AlertActionResponse rejectAlert(Long alertId) {
        User manager = getLoggedInManager();

        validateAlertId(alertId);

        RiskAlert alert = riskAlertRepository
                .findByAlertIdAndStatus(alertId, STATUS_SENT_TO_MANAGER)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Alert waiting for manager approval not found"));

        alert.setStatus(STATUS_REJECTED);
        alert.setApprovedBy(manager);

        RiskAlert updatedAlert = riskAlertRepository.save(alert);

        return new AlertActionResponse(updatedAlert.getAlertId(), updatedAlert.getStatus(), manager.getEmpId());
    }

    private void validateAlertId(Long alertId) {
        if (alertId == null || alertId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid alert id");
        }
    }

    private List<AlertResponse> mapAlerts(List<RiskAlert> alerts) {
        List<AlertResponse> responseList = new ArrayList<>();

        for (RiskAlert alert : alerts) {
            responseList.add(new AlertResponse(
                    alert.getAlertId(),
                    alert.getMachine() != null ? alert.getMachine().getMachineId() : null,
                    alert.getMachine() != null ? alert.getMachine().getMachineCode() : null,
                    alert.getProblemMeasure(),
                    alert.getSeverity(),
                    alert.getStatus(),
                    alert.getRaisedOn(),
                    alert.getFabUser() != null ? alert.getFabUser().getEmpId() : null,
                    alert.getApprovedBy() != null ? alert.getApprovedBy().getEmpId() : null,
                    alert.getAssignedTechnician() != null ? alert.getAssignedTechnician().getEmpId() : null
            ));
        }

        return responseList;
    }

    private User getLoggedInManager() {
        String email = SecurityUtils.currentUserEmail();

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }
}
