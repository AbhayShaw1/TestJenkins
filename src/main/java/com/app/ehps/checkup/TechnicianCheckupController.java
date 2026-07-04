package com.app.ehps.checkup;

import com.app.ehps.checkup.dto.AssignedWorkResponse;
import com.app.ehps.checkup.dto.MachineDetailsResponse;
import com.app.ehps.checkup.dto.PerformCheckupRequest;
import com.app.ehps.checkup.dto.PerformCheckupResult;
import com.app.ehps.common.response.ApiResponse;
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
 * Technician checkup endpoints (docs/API-CONTRACT.md "Technician — checkups").
 */
@RestController
@RequestMapping("/api/technician")
@PreAuthorize("hasRole('TECHNICIAN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Technician — Checkups", description = "Technician-facing checkup work: view assigned checkups and submit results. Requires role TECHNICIAN.")
public class TechnicianCheckupController {

    private final TechnicianCheckupService technicianCheckupService;

    public TechnicianCheckupController(TechnicianCheckupService technicianCheckupService) {
        this.technicianCheckupService = technicianCheckupService;
    }

    @Operation(
            summary = "List my assigned checkup work",
            description = "Returns checkups currently assigned to the calling technician. Requires role TECHNICIAN."
    )
    @GetMapping("/checkup-assignments")
    public ResponseEntity<ApiResponse<List<AssignedWorkResponse>>> getAssignedCheckupWorks() {
        List<AssignedWorkResponse> response = technicianCheckupService.getAssignedCheckupWorks();
        return ResponseEntity.ok(ApiResponse.of("Assigned checkups fetched successfully", response));
    }

    @Operation(
            summary = "Get machine details for a checkup",
            description = "Returns machine details for a machine assigned to the calling technician for checkup. Requires role TECHNICIAN."
    )
    @GetMapping("/checkups/machines/{machineId}")
    public ResponseEntity<ApiResponse<MachineDetailsResponse>> getMachineDetails(
            @Parameter(description = "Machine ID") @PathVariable Long machineId) {
        MachineDetailsResponse response = technicianCheckupService.getMachineDetails(machineId);
        return ResponseEntity.ok(ApiResponse.of("Machine details fetched successfully", response));
    }

    @Operation(
            summary = "Submit checkup results",
            description = "Records the results of a performed checkup on the given machine, raising alerts if parameters are out of range. Requires role TECHNICIAN."
    )
    @PostMapping("/checkups/machines/{machineId}/results")
    public ResponseEntity<ApiResponse<PerformCheckupResult>> performCheckup(
            @Parameter(description = "Machine ID") @PathVariable Long machineId,
            @Valid @RequestBody PerformCheckupRequest request) {
        PerformCheckupResult response = technicianCheckupService.performCheckup(machineId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of("Checkup completed successfully", response));
    }
}
