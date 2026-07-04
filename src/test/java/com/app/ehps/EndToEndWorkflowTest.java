package com.app.ehps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ultimate behavior-parity check for the rebuild: drives the ENTIRE EHPS maintenance workflow
 * over real HTTP (MockMvc, full security chain, H2 "itest" profile) from registration through
 * checkup -> risk alert -> escalation -> manager approval -> repair assignment -> repair
 * completion -> equipment dashboard history.
 *
 * <p>See docs/BEHAVIOR-BASELINE.md (full workflow, esp. §§2-3, 9-14) and
 * docs/API-CONTRACT.md (endpoint mapping) for the exact contracts asserted below. Endpoint
 * shapes/messages are cross-checked against:
 * <ul>
 *   <li>{@code checkup/TechnicianCheckupIntegrationTest}</li>
 *   <li>{@code alert/FabAlertIntegrationTest}</li>
 *   <li>{@code repair/TechnicianRepairIntegrationTest}</li>
 *   <li>{@code checkup/FabCheckupAssignmentIntegrationTest}</li>
 *   <li>{@code alert/ManagerAlertIntegrationTest}</li>
 *   <li>{@code dashboard/EquipmentDashboardIntegrationTest}</li>
 * </ul>
 */
@SpringBootTest(classes = EhpsApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("itest")
@Transactional
class EndToEndWorkflowTest {

    private static final String PASSWORD = "Password@123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void fullMaintenanceWorkflow_registrationThroughDashboardHistory() throws Exception {
        String managerEmail = "e2e.manager@ehps.com";
        String fabEmail = "e2e.fab@ehps.com";
        String techEmail = "e2e.tech@ehps.com";
        String today = LocalDate.now().toString();

        // ---------------------------------------------------------------
        // Step 1: Register MANAGER + FAB_COORDINATOR, log both in.
        // ---------------------------------------------------------------
        registerUser("Manager One", managerEmail, "manager");
        registerUser("Fab One", fabEmail, "fab_coordinator");

        String managerToken = login(managerEmail);
        String fabToken = login(fabEmail);

        // ---------------------------------------------------------------
        // Step 2: Fab creates a technician (speciality lithography); technician logs in.
        // ---------------------------------------------------------------
        MvcResult techCreateResult = mockMvc.perform(post("/api/fab/technicians")
                        .header("Authorization", "Bearer " + fabToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Technician One",
                                "email", techEmail,
                                "phone", "9876543212",
                                "password", PASSWORD,
                                "speciality", "lithography"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.technicianId").exists())
                .andExpect(jsonPath("$.data.speciality").value("lithography"))
                .andReturn();

        long technicianId = dataNode(techCreateResult).get("technicianId").asLong();

        String techToken = login(techEmail);

        // ---------------------------------------------------------------
        // Step 3: Fab creates a Lithography machine (typeId 1, code LH-900).
        // ---------------------------------------------------------------
        MvcResult machineCreateResult = mockMvc.perform(post("/api/fab/machines")
                        .header("Authorization", "Bearer " + fabToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "machineCode", "LH-900",
                                "typeId", 1,
                                "installDate", "2024-01-01"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.machineId").exists())
                .andExpect(jsonPath("$.data.machineCode").value("LH-900"))
                .andReturn();

        long machineId = dataNode(machineCreateResult).get("machineId").asLong();

        // ---------------------------------------------------------------
        // Step 4: Fab assigns the technician a checkup for the machine (today).
        // ---------------------------------------------------------------
        mockMvc.perform(post("/api/fab/checkup-assignments")
                        .header("Authorization", "Bearer " + fabToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "machineId", machineId,
                                "technicianId", technicianId,
                                "workDate", today
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Technician assigned successfully for checkup"))
                .andExpect(jsonPath("$.data.machineId").value(machineId))
                .andExpect(jsonPath("$.data.technicianId").value(technicianId))
                .andExpect(jsonPath("$.data.workType").value("checkup"))
                .andExpect(jsonPath("$.data.workDate").value(today));

        // ---------------------------------------------------------------
        // Step 5: Technician sees the assignment, then performs the checkup with values that
        // produce a BAD reading (P1 Light Intensity 40 -> bad, <60) -> risk alert, severity high.
        // ---------------------------------------------------------------
        mockMvc.perform(get("/api/technician/checkup-assignments")
                        .header("Authorization", "Bearer " + techToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[?(@.machineId == " + machineId + ")]").exists());

        MvcResult checkupResult = mockMvc.perform(post("/api/technician/checkups/machines/" + machineId + "/results")
                        .header("Authorization", "Bearer " + techToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                // P1 Light Intensity 40 -> bad; rest good.
                                "values", List.of(40, 22, 1, 1, 5)
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.riskAlertCreated").value(true))
                .andExpect(jsonPath("$.data.severity").value("high"))
                .andExpect(jsonPath("$.data.riskAlertId").exists())
                .andReturn();

        long alertId = dataNode(checkupResult).get("riskAlertId").asLong();

        // ---------------------------------------------------------------
        // Step 6: Fab sees the pending alert and escalates it to the manager.
        // ---------------------------------------------------------------
        mockMvc.perform(get("/api/fab/alerts/pending")
                        .header("Authorization", "Bearer " + fabToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[?(@.alertId == " + alertId + ")]").exists())
                .andExpect(jsonPath("$.data[?(@.machineId == " + machineId + ")]").exists());

        mockMvc.perform(post("/api/fab/alerts/" + alertId + "/escalation")
                        .header("Authorization", "Bearer " + fabToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.alertId").value(alertId))
                .andExpect(jsonPath("$.data.updatedStatus").value("sent_to_manager"));

        // ---------------------------------------------------------------
        // Step 7: Manager sees the alert pending approval and approves it.
        // ---------------------------------------------------------------
        mockMvc.perform(get("/api/manager/alerts/pending-approval")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[?(@.alertId == " + alertId + ")]").exists());

        mockMvc.perform(post("/api/manager/alerts/" + alertId + "/approval")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Alert approved successfully"))
                .andExpect(jsonPath("$.data.alertId").value(alertId))
                .andExpect(jsonPath("$.data.updatedStatus").value("approved"));

        // ---------------------------------------------------------------
        // Step 8: Fab finds the candidate technician(s) and assigns the repair.
        // ---------------------------------------------------------------
        mockMvc.perform(get("/api/fab/alerts/" + alertId + "/candidate-technicians")
                        .header("Authorization", "Bearer " + fabToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[?(@.technicianId == " + technicianId + ")]").exists());

        mockMvc.perform(post("/api/fab/alerts/" + alertId + "/repair-assignment")
                        .header("Authorization", "Bearer " + fabToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "technicianId", technicianId,
                                "repairDate", today
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.alertId").value(alertId))
                .andExpect(jsonPath("$.data.technicianId").value(technicianId))
                .andExpect(jsonPath("$.data.machineId").value(machineId))
                .andExpect(jsonPath("$.data.workType").value("repair"))
                .andExpect(jsonPath("$.data.workDate").value(today));

        // ---------------------------------------------------------------
        // Step 9: Technician sees the approved alert in their repair queue and completes it.
        // ---------------------------------------------------------------
        mockMvc.perform(get("/api/technician/repairs/alerts")
                        .header("Authorization", "Bearer " + techToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[?(@.alertId == " + alertId + ")]").exists());

        mockMvc.perform(post("/api/technician/repairs/alerts/" + alertId + "/completion")
                        .header("Authorization", "Bearer " + techToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "changesDone", "Replaced illumination module",
                                "observations", "Light intensity restored to spec"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Repair completed successfully"))
                .andExpect(jsonPath("$.data.alertId").value(alertId))
                .andExpect(jsonPath("$.data.machineId").value(machineId))
                .andExpect(jsonPath("$.data.alertStatus").value("resolved"))
                .andExpect(jsonPath("$.data.repairRecorded").value(true))
                .andExpect(jsonPath("$.data.historyRecorded").value(true));

        // ---------------------------------------------------------------
        // Step 10: Manager views the equipment dashboard; the machine's history now includes an
        // entry with the recorded issue/repairAction/observations.
        // ---------------------------------------------------------------
        MvcResult dashboardResult = mockMvc.perform(get("/api/dashboard/equipment")
                        .param("fromDate", "2020-01-01")
                        .param("toDate", "2035-01-01")
                        .param("typeId", "0")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Equipment history fetched successfully"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[?(@.machineId == " + machineId + ")]").exists())
                .andReturn();

        JsonNode dashboardData = objectMapper.readTree(dashboardResult.getResponse().getContentAsString())
                .get("data");

        JsonNode machineHistoryEntry = null;
        for (JsonNode entry : dashboardData) {
            if (entry.get("machineId").asLong() == machineId) {
                machineHistoryEntry = entry;
                break;
            }
        }

        assertThat(machineHistoryEntry).isNotNull();
        assertThat(machineHistoryEntry.get("machineCode").asText()).isEqualTo("LH-900");
        assertThat(machineHistoryEntry.get("repairAction").asText()).isEqualTo("Replaced illumination module");
        assertThat(machineHistoryEntry.get("observations").asText()).isEqualTo("Light intensity restored to spec");
        assertThat(machineHistoryEntry.get("issue").asText()).isNotBlank();
        assertThat(machineHistoryEntry.get("alertId").asLong()).isEqualTo(alertId);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private void registerUser(String name, String email, String role) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name,
                                "email", email,
                                "phone", "9876543210",
                                "password", PASSWORD,
                                "role", role
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Registration successful"))
                .andExpect(jsonPath("$.data.email").value(email));
    }

    private String login(String email) throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", PASSWORD
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.data.token").exists())
                .andReturn();

        return dataNode(loginResult).get("token").asText();
    }

    private JsonNode dataNode(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }
}
