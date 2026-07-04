package com.app.ehps.manager;

import com.app.ehps.common.response.ApiResponse;
import com.app.ehps.machine.dto.MachineResponse;
import com.app.ehps.user.dto.TechnicianResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Manager read endpoints (docs/API-CONTRACT.md "Manager — machines & technicians").
 */
@RestController
@RequestMapping("/api/manager")
@PreAuthorize("hasRole('MANAGER')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Manager — Machines & Technicians", description = "Manager read access to all machines and technicians "
        + "across the fab (not scoped to a single owner). Requires role MANAGER.")
public class ManagerController {

    private final ManagerService managerService;

    public ManagerController(ManagerService managerService) {
        this.managerService = managerService;
    }

    @Operation(
            summary = "List all machines",
            description = "Returns every machine across all fab coordinators. Requires role MANAGER."
    )
    @GetMapping("/machines")
    public ResponseEntity<ApiResponse<List<MachineResponse>>> getAllMachines() {
        List<MachineResponse> response = managerService.getAllMachines();
        return ResponseEntity.ok(ApiResponse.of("Machines fetched successfully", response));
    }

    @Operation(
            summary = "List all technicians",
            description = "Returns every technician across the fab. Requires role MANAGER."
    )
    @GetMapping("/technicians")
    public ResponseEntity<ApiResponse<List<TechnicianResponse>>> getAllTechnicians() {
        List<TechnicianResponse> response = managerService.getAllTechnicians();
        return ResponseEntity.ok(ApiResponse.of("Technicians fetched successfully", response));
    }
}
