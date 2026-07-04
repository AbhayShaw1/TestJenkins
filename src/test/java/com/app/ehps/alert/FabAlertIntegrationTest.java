package com.app.ehps.alert;

import com.app.ehps.EhpsApplication;
import com.app.ehps.machine.Machine;
import com.app.ehps.machine.MachineRepository;
import com.app.ehps.user.User;
import com.app.ehps.user.UserRepository;
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
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage for {@code /api/fab/alerts/**} (docs/API-CONTRACT.md "Fab — alerts";
 * BEHAVIOR-BASELINE.md §10).
 *
 * <p>The alert lifecycle normally starts from a checkup that auto-generates a risk alert. That
 * flow isn't exercised here, so a pending alert is inserted directly via {@link RiskAlertRepository}
 * (standing in for the checkup engine) after creating a machine through the real fab machines API.</p>
 */
@SpringBootTest(classes = EhpsApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("itest")
@Transactional
class FabAlertIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RiskAlertRepository riskAlertRepository;

    @Autowired
    private MachineRepository machineRepository;

    @Autowired
    private UserRepository userRepository;

    private String registerAndLoginFabCoordinator(String email) throws Exception {
        String registerJson = objectMapper.writeValueAsString(Map.of(
                "name", "Fab User",
                "email", email,
                "phone", "9876543210",
                "password", "Password@123",
                "role", "fab_coordinator"
        ));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "Password@123"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        return json.at("/data/token").asText();
    }

    private long createTechnician(String token, String email, String speciality) throws Exception {
        String requestJson = objectMapper.writeValueAsString(Map.of(
                "name", "Tech User",
                "email", email,
                "phone", "9876543211",
                "password", "Password@123",
                "speciality", speciality
        ));

        MvcResult result = mockMvc.perform(post("/api/fab/technicians")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.at("/data/technicianId").asLong();
    }

    private long createMachine(String token, String machineCode, long typeId) throws Exception {
        String requestJson = objectMapper.writeValueAsString(Map.of(
                "machineCode", machineCode,
                "typeId", typeId,
                "installDate", "2024-01-01"
        ));

        MvcResult result = mockMvc.perform(post("/api/fab/machines")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.at("/data/machineId").asLong();
    }

    /**
     * Inserts a pending {@link RiskAlert} directly, standing in for the checkup engine's
     * auto-generation (BEHAVIOR-BASELINE.md §9). Ownership (fabUser) is set to the logged-in fab
     * so the fab endpoints' ownership scoping matches.
     */
    private RiskAlert createPendingAlert(long machineId, String fabEmail) {
        Machine machine = machineRepository.findById(machineId).orElseThrow();
        User fabUser = userRepository.findByEmail(fabEmail).orElseThrow();

        RiskAlert alert = new RiskAlert();
        alert.setMachine(machine);
        alert.setProblemMeasure("Light Intensity:55.0(bad)");
        alert.setSeverity("high");
        alert.setStatus("pending");
        alert.setRaisedOn(LocalDate.of(2026, 7, 1));
        alert.setFabUser(fabUser);

        return riskAlertRepository.save(alert);
    }

    @Test
    void getPendingAlerts_returnsOwnPendingAlert() throws Exception {
        String token = registerAndLoginFabCoordinator("fab1@ehps.com");
        long machineId = createMachine(token, "LH-600", 1);
        RiskAlert alert = createPendingAlert(machineId, "fab1@ehps.com");

        mockMvc.perform(get("/api/fab/alerts/pending")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Pending alerts fetched successfully"))
                .andExpect(jsonPath("$.data[0].alertId").value(alert.getAlertId()))
                .andExpect(jsonPath("$.data[0].machineId").value(machineId))
                .andExpect(jsonPath("$.data[0].status").value("pending"));
    }

    @Test
    void sendToManager_success_returns200AndUpdatesStatus() throws Exception {
        String token = registerAndLoginFabCoordinator("fab2@ehps.com");
        long machineId = createMachine(token, "LH-601", 1);
        RiskAlert alert = createPendingAlert(machineId, "fab2@ehps.com");

        mockMvc.perform(post("/api/fab/alerts/" + alert.getAlertId() + "/escalation")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Alert sent to manager successfully"))
                .andExpect(jsonPath("$.data.alertId").value(alert.getAlertId()))
                .andExpect(jsonPath("$.data.updatedStatus").value("sent_to_manager"));
    }

    @Test
    void sendToManager_alreadySentToManager_returns404() throws Exception {
        String token = registerAndLoginFabCoordinator("fab3@ehps.com");
        long machineId = createMachine(token, "LH-602", 1);
        RiskAlert alert = createPendingAlert(machineId, "fab3@ehps.com");
        alert.setStatus("sent_to_manager");
        riskAlertRepository.save(alert);

        mockMvc.perform(post("/api/fab/alerts/" + alert.getAlertId() + "/escalation")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Pending alert not found"));
    }

    @Test
    void getApprovedUnassignedAlerts_returnsOwnApprovedUnassignedAlert() throws Exception {
        String token = registerAndLoginFabCoordinator("fab4@ehps.com");
        long machineId = createMachine(token, "LH-603", 1);
        RiskAlert alert = createPendingAlert(machineId, "fab4@ehps.com");
        alert.setStatus("approved");
        riskAlertRepository.save(alert);

        mockMvc.perform(get("/api/fab/alerts/approved-unassigned")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Approved unassigned alerts fetched successfully"))
                .andExpect(jsonPath("$.data[0].alertId").value(alert.getAlertId()))
                .andExpect(jsonPath("$.data[0].status").value("approved"));
    }

    @Test
    void getMatchingTechnicians_returnsSpecialityMatchedTechnicians() throws Exception {
        String token = registerAndLoginFabCoordinator("fab5@ehps.com");
        long technicianId = createTechnician(token, "matchtech1@ehps.com", "lithography");
        long machineId = createMachine(token, "LH-604", 1);
        RiskAlert alert = createPendingAlert(machineId, "fab5@ehps.com");
        alert.setStatus("approved");
        riskAlertRepository.save(alert);

        mockMvc.perform(get("/api/fab/alerts/" + alert.getAlertId() + "/candidate-technicians")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Matching technicians fetched successfully"))
                .andExpect(jsonPath("$.data[0].technicianId").value(technicianId))
                .andExpect(jsonPath("$.data[0].speciality").value("lithography"));
    }

    @Test
    void assignRepairTechnician_success_returns201AndAssignsWork() throws Exception {
        String token = registerAndLoginFabCoordinator("fab6@ehps.com");
        long technicianId = createTechnician(token, "repairtech1@ehps.com", "lithography");
        long machineId = createMachine(token, "LH-605", 1);
        RiskAlert alert = createPendingAlert(machineId, "fab6@ehps.com");
        alert.setStatus("approved");
        riskAlertRepository.save(alert);

        String requestJson = objectMapper.writeValueAsString(Map.of(
                "technicianId", technicianId,
                "repairDate", "2026-07-05"
        ));

        mockMvc.perform(post("/api/fab/alerts/" + alert.getAlertId() + "/repair-assignment")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Repair technician assigned successfully"))
                .andExpect(jsonPath("$.data.alertId").value(alert.getAlertId()))
                .andExpect(jsonPath("$.data.technicianId").value(technicianId))
                .andExpect(jsonPath("$.data.machineId").value(machineId))
                .andExpect(jsonPath("$.data.workType").value("repair"))
                .andExpect(jsonPath("$.data.workDate").value("2026-07-05"));
    }

    @Test
    void assignRepairTechnician_specialityMismatch_returns400() throws Exception {
        String token = registerAndLoginFabCoordinator("fab7@ehps.com");
        long technicianId = createTechnician(token, "repairtech2@ehps.com", "etcher");
        long machineId = createMachine(token, "LH-606", 1);
        RiskAlert alert = createPendingAlert(machineId, "fab7@ehps.com");
        alert.setStatus("approved");
        riskAlertRepository.save(alert);

        String requestJson = objectMapper.writeValueAsString(Map.of(
                "technicianId", technicianId,
                "repairDate", "2026-07-05"
        ));

        mockMvc.perform(post("/api/fab/alerts/" + alert.getAlertId() + "/repair-assignment")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Technician speciality mismatch"));
    }

    @Test
    void assignRepairTechnician_duplicateRepairWork_returns409() throws Exception {
        String token = registerAndLoginFabCoordinator("fab8@ehps.com");
        long technicianId = createTechnician(token, "repairtech3@ehps.com", "lithography");
        long machineId = createMachine(token, "LH-607", 1);

        RiskAlert firstAlert = createPendingAlert(machineId, "fab8@ehps.com");
        firstAlert.setStatus("approved");
        riskAlertRepository.save(firstAlert);

        String requestJson = objectMapper.writeValueAsString(Map.of(
                "technicianId", technicianId,
                "repairDate", "2026-07-05"
        ));

        mockMvc.perform(post("/api/fab/alerts/" + firstAlert.getAlertId() + "/repair-assignment")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated());

        RiskAlert secondAlert = createPendingAlert(machineId, "fab8@ehps.com");
        secondAlert.setStatus("approved");
        riskAlertRepository.save(secondAlert);

        mockMvc.perform(post("/api/fab/alerts/" + secondAlert.getAlertId() + "/repair-assignment")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Repair assignment already exists"));
    }
}
