package com.app.ehps.repair;

import com.app.ehps.EhpsApplication;
import com.app.ehps.alert.RiskAlert;
import com.app.ehps.alert.RiskAlertRepository;
import com.app.ehps.history.MachineHistoryRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack coverage of the technician repair flow (docs/API-CONTRACT.md "Technician —
 * repairs"; BEHAVIOR-BASELINE.md §12). Registers a fab coordinator + technician via the real HTTP
 * auth/registration flow, creates a machine via the fab endpoint, then seeds an "approved"
 * risk alert assigned to the technician directly via the repository (the fab
 * escalate/approve/assign-repair flow is a separate, not-yet-built slice) before driving the
 * technician repair endpoints end-to-end through MockMvc.
 */
@SpringBootTest(classes = EhpsApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("itest")
@Transactional
class TechnicianRepairIntegrationTest {

    private static final String PASSWORD = "Password@123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MachineRepository machineRepository;

    @Autowired
    private RiskAlertRepository riskAlertRepository;

    @Autowired
    private RepairRepository repairRepository;

    @Autowired
    private MachineHistoryRepository machineHistoryRepository;

    private String registerAndLogin(String name, String email, String password, String role) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name,
                                "email", email,
                                "phone", "9876543210",
                                "password", password,
                                "role", role
                        ))))
                .andExpect(status().isCreated());

        return loginOnly(email, password);
    }

    private String loginOnly(String email, String password) throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        return json.at("/data/token").asText();
    }

    private void addTechnicianViaFab(String fabToken, String name, String email, String password, String speciality) throws Exception {
        mockMvc.perform(post("/api/fab/technicians")
                        .header("Authorization", "Bearer " + fabToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name,
                                "email", email,
                                "phone", "9876543211",
                                "password", password,
                                "speciality", speciality
                        ))))
                .andExpect(status().isCreated());
    }

    private Long addMachineViaFab(String fabToken, String machineCode, long typeId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/fab/machines")
                        .header("Authorization", "Bearer " + fabToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "machineCode", machineCode,
                                "typeId", typeId,
                                "installDate", "2024-01-01"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.at("/data/machineId").asLong();
    }

    /**
     * Directly creates an "approved" RiskAlert assigned to the given technician, standing in for
     * the not-yet-built fab escalate/manager-approve/assign-repair flow (BEHAVIOR-BASELINE.md
     * §10).
     */
    private Long seedApprovedAlertAssignedToTechnician(Long machineId, String technicianEmail, String fabEmail) {
        User technician = userRepository.findByEmail(technicianEmail).orElseThrow();
        User fab = userRepository.findByEmail(fabEmail).orElseThrow();
        Machine machine = machineRepository.findById(machineId).orElseThrow();

        RiskAlert alert = new RiskAlert();
        alert.setMachine(machine);
        alert.setProblemMeasure("Light Intensity: 40.0 mW/cm² (bad)");
        alert.setSeverity("high");
        alert.setStatus("approved");
        alert.setRaisedOn(LocalDate.now());
        alert.setFabUser(fab);
        alert.setApprovedBy(fab);
        alert.setAssignedTechnician(technician);

        RiskAlert saved = riskAlertRepository.save(alert);
        return saved.getAlertId();
    }

    @Test
    void fullRepairFlow_listFetchAndComplete() throws Exception {
        String fabEmail = "fab.repair@ehps.com";
        String techEmail = "tech.repair@ehps.com";

        String fabToken = registerAndLogin("Fab Coordinator", fabEmail, PASSWORD, "fab_coordinator");
        addTechnicianViaFab(fabToken, "Repair Tech", techEmail, PASSWORD, "lithography");
        Long machineId = addMachineViaFab(fabToken, "LH-800", 1L);

        Long alertId = seedApprovedAlertAssignedToTechnician(machineId, techEmail, fabEmail);

        String techToken = loginOnly(techEmail, PASSWORD);

        // GET /alerts contains the seeded approved alert.
        mockMvc.perform(get("/api/technician/repairs/alerts")
                        .header("Authorization", "Bearer " + techToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Approved repair alerts fetched successfully"))
                .andExpect(jsonPath("$.data[?(@.alertId == " + alertId + ")]").exists());

        // GET /alerts/{alertId}
        mockMvc.perform(get("/api/technician/repairs/alerts/" + alertId)
                        .header("Authorization", "Bearer " + techToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Repair alert fetched successfully"))
                .andExpect(jsonPath("$.data.alertId").value(alertId))
                .andExpect(jsonPath("$.data.machineCode").value("LH-800"));

        // GET /machines/{machineId}
        mockMvc.perform(get("/api/technician/repairs/machines/" + machineId)
                        .header("Authorization", "Bearer " + techToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Machine details fetched successfully"))
                .andExpect(jsonPath("$.data.machineCode").value("LH-800"));

        // POST /alerts/{alertId}/completion -> 201
        mockMvc.perform(post("/api/technician/repairs/alerts/" + alertId + "/completion")
                        .header("Authorization", "Bearer " + techToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "changesDone", "Replaced lens assembly",
                                "observations", "Machine running within spec"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Repair completed successfully"))
                .andExpect(jsonPath("$.data.alertId").value(alertId))
                .andExpect(jsonPath("$.data.machineId").value(machineId))
                .andExpect(jsonPath("$.data.machineCode").value("LH-800"))
                .andExpect(jsonPath("$.data.alertStatus").value("resolved"))
                .andExpect(jsonPath("$.data.repairRecorded").value(true))
                .andExpect(jsonPath("$.data.historyRecorded").value(true));

        // Verify persistence: a Repair + MachineHistory exist and the alert is resolved.
        assertThat(repairRepository.findAll())
                .anyMatch(r -> r.getMachine().getMachineId().equals(machineId)
                        && "Replaced lens assembly".equals(r.getChangesDone())
                        && "Machine running within spec".equals(r.getObservations()));

        assertThat(machineHistoryRepository.findAll())
                .anyMatch(h -> h.getMachine().getMachineId().equals(machineId)
                        && "Replaced lens assembly".equals(h.getRepairAction())
                        && "Machine running within spec".equals(h.getObservations()));

        Optional<RiskAlert> resolvedAlert = riskAlertRepository.findById(alertId);
        assertThat(resolvedAlert).isPresent();
        assertThat(resolvedAlert.get().getStatus()).isEqualTo("resolved");
    }

    @Test
    void getApprovedRepairAlert_notAssignedToTechnician_returns403() throws Exception {
        String fabEmail = "fab.repair2@ehps.com";
        String techEmail = "tech.repair2@ehps.com";
        String otherTechEmail = "tech.repair2.other@ehps.com";

        String fabToken = registerAndLogin("Fab Coordinator", fabEmail, PASSWORD, "fab_coordinator");
        addTechnicianViaFab(fabToken, "Repair Tech", techEmail, PASSWORD, "lithography");
        addTechnicianViaFab(fabToken, "Other Tech", otherTechEmail, PASSWORD, "lithography");
        Long machineId = addMachineViaFab(fabToken, "LH-801", 1L);

        Long alertId = seedApprovedAlertAssignedToTechnician(machineId, techEmail, fabEmail);

        String otherTechToken = loginOnly(otherTechEmail, PASSWORD);

        mockMvc.perform(get("/api/technician/repairs/alerts/" + alertId)
                        .header("Authorization", "Bearer " + otherTechToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Alert is not assigned to this technician"));
    }
}
