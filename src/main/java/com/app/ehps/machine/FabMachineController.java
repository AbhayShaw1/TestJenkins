package com.app.ehps.machine;

import com.app.ehps.common.response.ApiResponse;
import com.app.ehps.machine.dto.AddMachineRequest;
import com.app.ehps.machine.dto.MachineResponse;
import com.app.ehps.machine.dto.UpdateMachineRequest;
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
 * Fab-coordinator machine endpoints (docs/API-CONTRACT.md "Fab — machines").
 */
@RestController
@RequestMapping("/api/fab/machines")
@PreAuthorize("hasRole('FAB_COORDINATOR')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Fab — Machines", description = "Fab-coordinator management of their own registered machines. Requires role FAB_COORDINATOR.")
public class FabMachineController {

    private final FabMachineService fabMachineService;

    public FabMachineController(FabMachineService fabMachineService) {
        this.fabMachineService = fabMachineService;
    }

    @Operation(
            summary = "Register a new machine",
            description = "Adds a new machine owned by the calling fab coordinator. Requires role FAB_COORDINATOR."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<MachineResponse>> addMachine(@Valid @RequestBody AddMachineRequest request) {
        MachineResponse machineResponse = fabMachineService.addMachine(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of("Machine added successfully", machineResponse));
    }

    @Operation(
            summary = "List my machines",
            description = "Returns all machines owned by the calling fab coordinator. Requires role FAB_COORDINATOR."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<List<MachineResponse>>> getMyMachines() {
        List<MachineResponse> machines = fabMachineService.getMyMachines();
        return ResponseEntity.ok(ApiResponse.of("Machines fetched successfully", machines));
    }

    @Operation(
            summary = "Get a machine by ID",
            description = "Returns a single machine owned by the calling fab coordinator. Requires role FAB_COORDINATOR."
    )
    @GetMapping("/{machineId}")
    public ResponseEntity<ApiResponse<MachineResponse>> getMachineById(
            @Parameter(description = "Machine ID") @PathVariable Long machineId) {
        MachineResponse machineResponse = fabMachineService.getMachineById(machineId);
        return ResponseEntity.ok(ApiResponse.of("Machine fetched successfully", machineResponse));
    }

    @Operation(
            summary = "Update a machine",
            description = "Updates an existing machine owned by the calling fab coordinator. Requires role FAB_COORDINATOR."
    )
    @PutMapping("/{machineId}")
    public ResponseEntity<ApiResponse<MachineResponse>> updateMachine(
            @Parameter(description = "Machine ID") @PathVariable Long machineId,
            @Valid @RequestBody UpdateMachineRequest request) {
        MachineResponse machineResponse = fabMachineService.updateMachine(machineId, request);
        return ResponseEntity.ok(ApiResponse.of("Machine updated successfully", machineResponse));
    }
}
