package com.app.ehps.alert;

import com.app.ehps.alert.dto.AlertResponse;
import com.app.ehps.alert.dto.AssignRepairRequest;
import com.app.ehps.alert.dto.AssignRepairResponse;
import com.app.ehps.alert.dto.EscalationResponse;
import com.app.ehps.common.response.ApiResponse;
import com.app.ehps.user.dto.TechnicianResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Fab-coordinator alert lifecycle endpoints (docs/API-CONTRACT.md "Fab — alerts").
 */
@RestController
@RequestMapping("/api/fab/alerts")
@PreAuthorize("hasRole('FAB_COORDINATOR')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Fab — Alerts", description = "Fab-coordinator alert lifecycle: review pending/approved alerts, escalate to manager, and assign repair technicians. Requires role FAB_COORDINATOR.")
public class FabAlertController {

    private final FabAlertService fabAlertService;

    public FabAlertController(FabAlertService fabAlertService) {
        this.fabAlertService = fabAlertService;
    }

    @Operation(
            summary = "List pending alerts",
            description = "Returns alerts awaiting fab-coordinator triage. Requires role FAB_COORDINATOR."
    )
    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<AlertResponse>>> getPendingAlerts() {
        List<AlertResponse> response = fabAlertService.getPendingAlerts();
        return ResponseEntity.ok(ApiResponse.of("Pending alerts fetched successfully", response));
    }

    @Operation(
            summary = "List approved, unassigned alerts",
            description = "Returns manager-approved alerts that do not yet have a repair technician assigned. Requires role FAB_COORDINATOR."
    )
    @GetMapping("/approved-unassigned")
    public ResponseEntity<ApiResponse<List<AlertResponse>>> getApprovedUnassignedAlerts() {
        List<AlertResponse> response = fabAlertService.getApprovedUnassignedAlerts();
        return ResponseEntity.ok(ApiResponse.of("Approved unassigned alerts fetched successfully", response));
    }

    @Operation(
            summary = "List candidate technicians for an alert",
            description = "Returns technicians qualified to repair the machine associated with the given alert. Requires role FAB_COORDINATOR."
    )
    @GetMapping("/{alertId}/candidate-technicians")
    public ResponseEntity<ApiResponse<List<TechnicianResponse>>> getMatchingTechnicians(
            @Parameter(description = "Alert ID") @PathVariable Long alertId) {
        List<TechnicianResponse> response = fabAlertService.getMatchingTechnicians(alertId);
        return ResponseEntity.ok(ApiResponse.of("Matching technicians fetched successfully", response));
    }

    @Operation(
            summary = "Escalate an alert to manager",
            description = "Sends a pending alert to the manager for approval. Requires role FAB_COORDINATOR."
    )
    @PostMapping("/{alertId}/escalation")
    public ResponseEntity<ApiResponse<EscalationResponse>> sendToManager(
            @Parameter(description = "Alert ID") @PathVariable Long alertId) {
        EscalationResponse response = fabAlertService.sendToManager(alertId);
        return ResponseEntity.ok(ApiResponse.of("Alert sent to manager successfully", response));
    }

    @Operation(
            summary = "Assign a repair technician to an alert",
            description = "Assigns a technician to repair the machine associated with an approved alert. Requires role FAB_COORDINATOR."
    )
    @PostMapping("/{alertId}/repair-assignment")
    public ResponseEntity<ApiResponse<AssignRepairResponse>> assignRepairTechnician(
            @Parameter(description = "Alert ID") @PathVariable Long alertId,
            @Valid @RequestBody AssignRepairRequest request) {
        AssignRepairResponse response = fabAlertService.assignRepairTechnician(alertId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of("Repair technician assigned successfully", response));
    }
}
