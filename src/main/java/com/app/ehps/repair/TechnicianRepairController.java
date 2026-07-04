package com.app.ehps.repair;

import com.app.ehps.checkup.dto.MachineDetailsResponse;
import com.app.ehps.common.response.ApiResponse;
import com.app.ehps.repair.dto.ApprovedRepairAlertResponse;
import com.app.ehps.repair.dto.CompleteRepairRequest;
import com.app.ehps.repair.dto.CompleteRepairResponse;
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
 * Technician repair endpoints (docs/API-CONTRACT.md "Technician — repairs").
 */
@RestController
@RequestMapping("/api/technician/repairs")
@PreAuthorize("hasRole('TECHNICIAN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Technician — Repairs", description = "Technician-facing repair work: view assigned repair alerts and mark repairs complete. Requires role TECHNICIAN.")
public class TechnicianRepairController {

    private final TechnicianRepairService technicianRepairService;

    public TechnicianRepairController(TechnicianRepairService technicianRepairService) {
        this.technicianRepairService = technicianRepairService;
    }

    @Operation(
            summary = "List my approved repair alerts",
            description = "Returns approved alerts assigned to the calling technician for repair. Requires role TECHNICIAN."
    )
    @GetMapping("/alerts")
    public ResponseEntity<ApiResponse<List<ApprovedRepairAlertResponse>>> getApprovedRepairAlerts() {
        List<ApprovedRepairAlertResponse> response = technicianRepairService.getApprovedRepairAlerts();
        return ResponseEntity.ok(ApiResponse.of("Approved repair alerts fetched successfully", response));
    }

    @Operation(
            summary = "Get a single approved repair alert",
            description = "Returns a single approved repair alert assigned to the calling technician. Requires role TECHNICIAN."
    )
    @GetMapping("/alerts/{alertId}")
    public ResponseEntity<ApiResponse<ApprovedRepairAlertResponse>> getApprovedRepairAlert(
            @Parameter(description = "Alert ID") @PathVariable Long alertId) {
        ApprovedRepairAlertResponse response = technicianRepairService.getApprovedRepairAlert(alertId);
        return ResponseEntity.ok(ApiResponse.of("Repair alert fetched successfully", response));
    }

    @Operation(
            summary = "Get machine details for a repair",
            description = "Returns machine details for a machine assigned to the calling technician for repair. Requires role TECHNICIAN."
    )
    @GetMapping("/machines/{machineId}")
    public ResponseEntity<ApiResponse<MachineDetailsResponse>> getMachineDetails(
            @Parameter(description = "Machine ID") @PathVariable Long machineId) {
        MachineDetailsResponse response = technicianRepairService.getMachineDetails(machineId);
        return ResponseEntity.ok(ApiResponse.of("Machine details fetched successfully", response));
    }

    @Operation(
            summary = "Complete a repair",
            description = "Marks the repair for the given alert as complete, recording the repair outcome. Requires role TECHNICIAN."
    )
    @PostMapping("/alerts/{alertId}/completion")
    public ResponseEntity<ApiResponse<CompleteRepairResponse>> completeRepair(
            @Parameter(description = "Alert ID") @PathVariable Long alertId,
            @Valid @RequestBody CompleteRepairRequest request) {
        CompleteRepairResponse response = technicianRepairService.completeRepair(alertId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of("Repair completed successfully", response));
    }
}
