package com.app.ehps.dashboard;

import com.app.ehps.common.response.ApiResponse;
import com.app.ehps.dashboard.dto.DashboardHistoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Equipment dashboard endpoints for manager/fab coordinator (docs/API-CONTRACT.md "Equipment
 * dashboard").
 */
@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize("hasAnyRole('MANAGER','FAB_COORDINATOR')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Dashboard", description = "Equipment health/performance history for reporting. Requires role MANAGER or FAB_COORDINATOR.")
public class EquipmentDashboardController {

    private final EquipmentDashboardService equipmentDashboardService;

    public EquipmentDashboardController(EquipmentDashboardService equipmentDashboardService) {
        this.equipmentDashboardService = equipmentDashboardService;
    }

    @Operation(
            summary = "Get equipment history",
            description = "Returns equipment checkup/repair history within an optional date range and machine type filter. "
                    + "Requires role MANAGER or FAB_COORDINATOR."
    )
    @GetMapping("/equipment")
    public ResponseEntity<ApiResponse<List<DashboardHistoryResponse>>> getDashboardHistory(
            @Parameter(description = "Start date (inclusive), ISO-8601 (yyyy-MM-dd)")
            // required=false: let the service perform the null check so a missing param
            // surfaces as the legacy "From/To date is required" 400 (via ResponseStatusException)
            // rather than Spring's built-in MissingServletRequestParameterException handling.
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,

            @Parameter(description = "End date (inclusive), ISO-8601 (yyyy-MM-dd)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate,

            @Parameter(description = "Machine type ID filter; 0 means all types")
            @RequestParam(defaultValue = "0")
            Long typeId) {

        List<DashboardHistoryResponse> response =
                equipmentDashboardService.getDashboardHistory(fromDate, toDate, typeId);

        return ResponseEntity.ok(ApiResponse.of("Equipment history fetched successfully", response));
    }
}
