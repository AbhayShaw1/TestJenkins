package com.app.ehps.user;

import com.app.ehps.common.response.ApiResponse;
import com.app.ehps.user.dto.AddTechnicianRequest;
import com.app.ehps.user.dto.TechnicianResponse;
import com.app.ehps.user.dto.UpdateTechnicianRequest;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Fab coordinator technician management endpoints (docs/API-CONTRACT.md "Fab — technicians").
 */
@RestController
@RequestMapping("/api/fab/technicians")
@PreAuthorize("hasRole('FAB_COORDINATOR')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Fab — Technicians", description = "Fab-coordinator management of technicians. Requires role FAB_COORDINATOR.")
public class FabTechnicianController {

    private final FabTechnicianService fabTechnicianService;

    public FabTechnicianController(FabTechnicianService fabTechnicianService) {
        this.fabTechnicianService = fabTechnicianService;
    }

    @Operation(
            summary = "Register a new technician",
            description = "Creates a new technician account managed by the calling fab coordinator. Requires role FAB_COORDINATOR."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<TechnicianResponse>> addTechnician(@Valid @RequestBody AddTechnicianRequest request) {
        TechnicianResponse response = fabTechnicianService.addTechnician(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of("Technician added successfully", response));
    }

    @Operation(
            summary = "List all technicians",
            description = "Returns all technicians managed by the calling fab coordinator. Requires role FAB_COORDINATOR."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<List<TechnicianResponse>>> getAllTechnicians() {
        List<TechnicianResponse> response = fabTechnicianService.getAllTechnicians();
        return ResponseEntity.ok(ApiResponse.of("Technicians fetched successfully", response));
    }

    @Operation(
            summary = "Get a technician by ID",
            description = "Returns a single technician managed by the calling fab coordinator. Requires role FAB_COORDINATOR."
    )
    @GetMapping("/{technicianId}")
    public ResponseEntity<ApiResponse<TechnicianResponse>> getTechnicianById(
            @Parameter(description = "Technician ID") @PathVariable Long technicianId) {
        TechnicianResponse response = fabTechnicianService.getTechnicianById(technicianId);
        return ResponseEntity.ok(ApiResponse.of("Technician fetched successfully", response));
    }

    @Operation(
            summary = "Update a technician",
            description = "Updates an existing technician managed by the calling fab coordinator. Requires role FAB_COORDINATOR."
    )
    @PutMapping("/{technicianId}")
    public ResponseEntity<ApiResponse<TechnicianResponse>> updateTechnician(
            @Parameter(description = "Technician ID") @PathVariable Long technicianId,
            @Valid @RequestBody UpdateTechnicianRequest request) {
        TechnicianResponse response = fabTechnicianService.updateTechnician(technicianId, request);
        return ResponseEntity.ok(ApiResponse.of("Technician updated successfully", response));
    }
}
