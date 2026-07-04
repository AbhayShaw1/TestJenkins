package com.app.ehps.checkup;

import com.app.ehps.checkup.dto.AssignCheckupRequest;
import com.app.ehps.checkup.dto.CheckupAssignmentResponse;
import com.app.ehps.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fab-coordinator checkup-assignment endpoint (docs/API-CONTRACT.md "Fab — checkup assignment").
 */
@RestController
@RequestMapping("/api/fab/checkup-assignments")
@PreAuthorize("hasRole('FAB_COORDINATOR')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Fab — Checkup Assignments", description = "Fab-coordinator assignment of technicians to routine machine checkups. Requires role FAB_COORDINATOR.")
public class FabCheckupAssignmentController {

    private final FabCheckupAssignmentService fabCheckupAssignmentService;

    public FabCheckupAssignmentController(FabCheckupAssignmentService fabCheckupAssignmentService) {
        this.fabCheckupAssignmentService = fabCheckupAssignmentService;
    }

    @Operation(
            summary = "Assign a technician to a checkup",
            description = "Assigns a technician to perform a routine checkup on a machine. Requires role FAB_COORDINATOR."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<CheckupAssignmentResponse>> assignTechnicianForCheckup(
            @Valid @RequestBody AssignCheckupRequest request) {
        CheckupAssignmentResponse response = fabCheckupAssignmentService.assignTechnicianForCheckup(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of("Technician assigned successfully for checkup", response));
    }
}
