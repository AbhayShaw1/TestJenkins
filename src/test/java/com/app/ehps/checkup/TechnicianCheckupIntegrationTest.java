package com.app.ehps.checkup;

import com.app.ehps.EhpsApplication;
import com.app.ehps.machine.Machine;
import com.app.ehps.machine.MachineRepository;
import com.app.ehps.user.User;
import com.app.ehps.user.UserRepository;
import com.app.ehps.work.TechnicianWork;
import com.app.ehps.work.TechnicianWorkRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack coverage of the technician checkup flow (docs/API-CONTRACT.md "Technician —
 * checkups"; BEHAVIOR-BASELINE.md §9). Registers a fab coordinator + technician via the real HTTP
 * auth/registration flow, then assigns checkup work directly via repositories (the
 * fab-checkup-assignment HTTP endpoint is a separate, not-yet-built slice) before driving the
 * technician endpoints end-to-end through MockMvc.
 */
@SpringBootTest(classes = EhpsApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("itest")
@Transactional
class TechnicianCheckupIntegrationTest {

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
    private TechnicianWorkRepository technicianWorkRepository;

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

    private String addTechnicianViaFab(String fabToken, String name, String email, String password, String speciality) throws Exception {
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
        return email;
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
     * Directly creates an active (completed=false) "checkup" TechnicianWork assignment, standing
     * in for the not-yet-built {@code POST /api/fab/checkup-assignments} endpoint
     * (BEHAVIOR-BASELINE.md §11).
     */
    private void assignCheckup(Long machineId, String technicianEmail, String fabEmail) {
        User technician = userRepository.findByEmail(technicianEmail).orElseThrow();
        User fab = userRepository.findByEmail(fabEmail).orElseThrow();
        Machine machine = machineRepository.findById(machineId).orElseThrow();

        TechnicianWork work = new TechnicianWork();
        work.setMachine(machine);
        work.setTechnician(technician);
        work.setFabUser(fab);
        work.setWorkType("checkup");
        work.setWorkDate(LocalDate.now());
        work.setCompleted(false);

        technicianWorkRepository.save(work);
    }

    @Test
    void fullCheckupFlow_goodValues_thenBadValues() throws Exception {
        String fabEmail = "fab.checkup@ehps.com";
        String techEmail = "tech.checkup@ehps.com";

        String fabToken = registerAndLogin("Fab Coordinator", fabEmail, PASSWORD, "fab_coordinator");

        addTechnicianViaFab(fabToken, "Checkup Tech", techEmail, PASSWORD, "lithography");

        Long machineId = addMachineViaFab(fabToken, "LH-700", 1L);

        assignCheckup(machineId, techEmail, fabEmail);

        // Technician was created via POST /api/fab/technicians (not /api/auth/register), so log
        // in directly with the known email + password.
        String techToken = loginOnly(techEmail, PASSWORD);

        // Assigned checkups contains the assignment.
        mockMvc.perform(get("/api/technician/checkup-assignments")
                        .header("Authorization", "Bearer " + techToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Assigned checkups fetched successfully"))
                .andExpect(jsonPath("$.data[?(@.machineId == " + machineId + ")]").exists());

        // Machine details fetched successfully.
        mockMvc.perform(get("/api/technician/checkups/machines/" + machineId)
                        .header("Authorization", "Bearer " + techToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Machine details fetched successfully"))
                .andExpect(jsonPath("$.data.machineCode").value("LH-700"));

        // Perform checkup with 5 good lithography values -> 201, finalHealth 100, no alert.
        mockMvc.perform(post("/api/technician/checkups/machines/" + machineId + "/results")
                        .header("Authorization", "Bearer " + techToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "values", List.of(90, 22, 1, 1, 5)
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Checkup completed successfully"))
                .andExpect(jsonPath("$.data.finalHealth").value(100))
                .andExpect(jsonPath("$.data.riskAlertCreated").value(false))
                .andExpect(jsonPath("$.data.severity").value(Matchers.nullValue()));

        // New assignment for the same machine, then submit bad values -> alert created, severity high.
        assignCheckup(machineId, techEmail, fabEmail);

        mockMvc.perform(post("/api/technician/checkups/machines/" + machineId + "/results")
                        .header("Authorization", "Bearer " + techToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                // P1 = 40 -> bad (light intensity < 60); rest good.
                                "values", List.of(40, 22, 1, 1, 5)
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.riskAlertCreated").value(true))
                .andExpect(jsonPath("$.data.severity").value("high"))
                .andExpect(jsonPath("$.data.riskAlertId").exists());
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

    @Test
    void performCheckup_notAssigned_returns403() throws Exception {
        String fabEmail = "fab.notassigned@ehps.com";
        String techEmail = "tech.notassigned@ehps.com";

        String fabToken = registerAndLogin("Fab Coordinator", fabEmail, PASSWORD, "fab_coordinator");
        addTechnicianViaFab(fabToken, "Unassigned Tech", techEmail, PASSWORD, "lithography");
        Long machineId = addMachineViaFab(fabToken, "LH-701", 1L);

        String techToken = loginOnly(techEmail, PASSWORD);

        mockMvc.perform(post("/api/technician/checkups/machines/" + machineId + "/results")
                        .header("Authorization", "Bearer " + techToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "values", List.of(90, 22, 1, 1, 5)
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Machine is not assigned to this technician for checkup"));
    }
}
