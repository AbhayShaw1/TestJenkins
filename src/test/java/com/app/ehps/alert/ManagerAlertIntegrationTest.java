package com.app.ehps.alert;

import com.app.ehps.EhpsApplication;
import com.app.ehps.machine.Machine;
import com.app.ehps.machine.MachineRepository;
import com.app.ehps.machine.MachineType;
import com.app.ehps.user.User;
import com.app.ehps.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
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
 * End-to-end coverage for {@code /api/manager/alerts} (docs/API-CONTRACT.md "Manager — alerts";
 * BEHAVIOR-BASELINE.md §10).
 */
@SpringBootTest(classes = EhpsApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("itest")
@Transactional
class ManagerAlertIntegrationTest {

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

    @Autowired
    private EntityManager entityManager;

    private String registerAndLogin(String email, String role) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Test User",
                                "email", email,
                                "phone", "9876543210",
                                "password", "Password@123",
                                "role", role
                        ))))
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

    private RiskAlert createSentToManagerAlert(String fabEmail) {
        User fabUser = userRepository.findByEmail(fabEmail).orElseThrow();

        MachineType type = entityManager.find(MachineType.class, 1L);

        Machine machine = new Machine();
        machine.setMachineCode("LH-ALERT-1");
        machine.setMachineType(type);
        machine.setInstallDate(LocalDate.of(2024, 1, 1));
        machine.setFabUser(fabUser);
        machine = machineRepository.save(machine);

        RiskAlert alert = new RiskAlert();
        alert.setMachine(machine);
        alert.setProblemMeasure("Light Intensity: bad");
        alert.setSeverity("high");
        alert.setStatus("sent_to_manager");
        alert.setRaisedOn(LocalDate.now());
        alert.setFabUser(fabUser);
        return riskAlertRepository.save(alert);
    }

    @Test
    void getAllAlerts_returns200_withSuccessTrue() throws Exception {
        registerAndLogin("fabalert1@ehps.com", "fab_coordinator");
        createSentToManagerAlert("fabalert1@ehps.com");

        String managerToken = registerAndLogin("mgralert1@ehps.com", "manager");

        mockMvc.perform(get("/api/manager/alerts")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Alerts fetched successfully"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getPendingManagerApprovalAlerts_containsCreatedAlert() throws Exception {
        registerAndLogin("fabalert2@ehps.com", "fab_coordinator");
        RiskAlert alert = createSentToManagerAlert("fabalert2@ehps.com");

        String managerToken = registerAndLogin("mgralert2@ehps.com", "manager");

        mockMvc.perform(get("/api/manager/alerts/pending-approval")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Pending approval alerts fetched successfully"))
                .andExpect(jsonPath("$.data[?(@.alertId == " + alert.getAlertId() + ")]").exists());
    }

    @Test
    void approveAlert_success_returns200WithApprovedStatus() throws Exception {
        registerAndLogin("fabalert3@ehps.com", "fab_coordinator");
        RiskAlert alert = createSentToManagerAlert("fabalert3@ehps.com");

        String managerToken = registerAndLogin("mgralert3@ehps.com", "manager");

        mockMvc.perform(post("/api/manager/alerts/" + alert.getAlertId() + "/approval")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Alert approved successfully"))
                .andExpect(jsonPath("$.data.alertId").value(alert.getAlertId()))
                .andExpect(jsonPath("$.data.updatedStatus").value("approved"));
    }

    @Test
    void approveAlert_nonExistentAlert_returns404() throws Exception {
        String managerToken = registerAndLogin("mgralert4@ehps.com", "manager");

        mockMvc.perform(post("/api/manager/alerts/999999/approval")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Alert waiting for manager approval not found"));
    }
}
