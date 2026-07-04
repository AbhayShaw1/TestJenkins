package com.app.ehps.alert;

import com.app.ehps.alert.dto.AlertActionResponse;
import com.app.ehps.alert.dto.AlertResponse;
import com.app.ehps.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Manager alert lifecycle endpoints (docs/API-CONTRACT.md "Manager — alerts";
 * BEHAVIOR-BASELINE.md §10).
 */
@RestController
@RequestMapping("/api/manager/alerts")
@PreAuthorize("hasRole('MANAGER')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Manager — Alerts", description = "Manager alert lifecycle: review escalated alerts and approve or reject them. Requires role MANAGER.")
public class ManagerAlertController {

    private final ManagerAlertService managerAlertService;

    public ManagerAlertController(ManagerAlertService managerAlertService) {
        this.managerAlertService = managerAlertService;
    }

    @Operation(
            summary = "List all alerts",
            description = "Returns every alert visible to managers. Requires role MANAGER."
    )
    @GetMapping("")
    public ResponseEntity<ApiResponse<List<AlertResponse>>> getAllAlerts() {
        List<AlertResponse> response = managerAlertService.getAllAlerts();
        return ResponseEntity.ok(ApiResponse.of("Alerts fetched successfully", response));
    }

    @Operation(
            summary = "List alerts pending manager approval",
            description = "Returns alerts escalated by a fab coordinator that are awaiting manager approval. Requires role MANAGER."
    )
    @GetMapping("/pending-approval")
    public ResponseEntity<ApiResponse<List<AlertResponse>>> getPendingManagerApprovalAlerts() {
        List<AlertResponse> response = managerAlertService.getPendingManagerApprovalAlerts();
        return ResponseEntity.ok(ApiResponse.of("Pending approval alerts fetched successfully", response));
    }

    @Operation(
            summary = "Approve an alert",
            description = "Approves an escalated alert, allowing it to proceed to repair assignment. Requires role MANAGER."
    )
    @PostMapping("/{alertId}/approval")
    public ResponseEntity<ApiResponse<AlertActionResponse>> approveAlert(
            @Parameter(description = "Alert ID") @PathVariable Long alertId) {
        AlertActionResponse response = managerAlertService.approveAlert(alertId);
        return ResponseEntity.ok(ApiResponse.of("Alert approved successfully", response));
    }

    @Operation(
            summary = "Reject an alert",
            description = "Rejects an escalated alert. Requires role MANAGER."
    )
    @PostMapping("/{alertId}/rejection")
    public ResponseEntity<ApiResponse<AlertActionResponse>> rejectAlert(
            @Parameter(description = "Alert ID") @PathVariable Long alertId) {
        AlertActionResponse response = managerAlertService.rejectAlert(alertId);
        return ResponseEntity.ok(ApiResponse.of("Alert rejected successfully", response));
    }
}
