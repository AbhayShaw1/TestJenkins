package com.app.ehps.machine;

import com.app.ehps.common.response.ApiResponse;
import com.app.ehps.machine.dto.MachineResponse;
import com.app.ehps.machine.dto.MachineTypeResponse;
import com.app.ehps.machine.dto.ParameterRuleResponse;
import com.app.ehps.user.dto.TechnicianResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Shared machine-type reference data + candidate lists (docs/API-CONTRACT.md "Machine types"),
 * consolidated from 4 legacy endpoints. Authentication for {@code /api/machine-types/**} is
 * already enforced by {@code SecurityConfig}; method-level {@code @PreAuthorize} narrows the
 * two role-scoped sub-lists per the contract.
 */
@RestController
@RequestMapping("/api/machine-types")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Machine Types", description = "Shared machine-type reference data, available to any authenticated role. "
        + "Some sub-lists are further scoped by role.")
public class MachineTypeController {

    private final MachineTypeService machineTypeService;

    public MachineTypeController(MachineTypeService machineTypeService) {
        this.machineTypeService = machineTypeService;
    }

    @Operation(
            summary = "List all machine types",
            description = "Returns all machine types. Available to any authenticated role."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<List<MachineTypeResponse>>> getAllTypes() {
        List<MachineTypeResponse> response = machineTypeService.getAllTypes();
        return ResponseEntity.ok(ApiResponse.of("Machine types fetched successfully", response));
    }

    @Operation(
            summary = "Get parameter rules for a machine type",
            description = "Returns the checkup parameter rules configured for the given machine type. "
                    + "Available to any authenticated role."
    )
    @GetMapping("/{typeId}/parameters")
    public ResponseEntity<ApiResponse<List<ParameterRuleResponse>>> getParameters(
            @Parameter(description = "Machine type ID") @PathVariable Long typeId) {
        List<ParameterRuleResponse> response = machineTypeService.getParameters(typeId);
        return ResponseEntity.ok(ApiResponse.of("Parameters fetched successfully", response));
    }

    @Operation(
            summary = "List machines of a given type",
            description = "Returns machines of the given type. Requires role FAB_COORDINATOR or MANAGER."
    )
    @GetMapping("/{typeId}/machines")
    @PreAuthorize("hasAnyRole('FAB_COORDINATOR','MANAGER')")
    public ResponseEntity<ApiResponse<List<MachineResponse>>> getMachinesByType(
            @Parameter(description = "Machine type ID") @PathVariable Long typeId) {
        List<MachineResponse> response = machineTypeService.getMachinesByType(typeId);
        return ResponseEntity.ok(ApiResponse.of("Machines fetched successfully", response));
    }

    @Operation(
            summary = "List technicians qualified for a machine type",
            description = "Returns technicians qualified to service the given machine type. Requires role FAB_COORDINATOR."
    )
    @GetMapping("/{typeId}/technicians")
    @PreAuthorize("hasRole('FAB_COORDINATOR')")
    public ResponseEntity<ApiResponse<List<TechnicianResponse>>> getTechniciansByType(
            @Parameter(description = "Machine type ID") @PathVariable Long typeId) {
        List<TechnicianResponse> response = machineTypeService.getTechniciansByType(typeId);
        return ResponseEntity.ok(ApiResponse.of("Technicians fetched successfully", response));
    }
}
