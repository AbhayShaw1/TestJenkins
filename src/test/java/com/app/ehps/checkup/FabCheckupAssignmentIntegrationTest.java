package com.app.ehps.checkup;

import com.app.ehps.EhpsApplication;
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

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage for {@code POST /api/fab/checkup-assignments} (docs/API-CONTRACT.md
 * "Fab — checkup assignment"; BEHAVIOR-BASELINE.md §11).
 */
@SpringBootTest(classes = EhpsApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("itest")
@Transactional
class FabCheckupAssignmentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    private String assignCheckupJson(long machineId, long technicianId, String workDate) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "machineId", machineId,
                "technicianId", technicianId,
                "workDate", workDate
        ));
    }

    @Test
    void assignTechnicianForCheckup_success_returns201() throws Exception {
        String token = registerAndLoginFabCoordinator("fab@ehps.com");
        long technicianId = createTechnician(token, "checkuptech1@ehps.com", "lithography");
        long machineId = createMachine(token, "LH-500", 1);

        mockMvc.perform(post("/api/fab/checkup-assignments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignCheckupJson(machineId, technicianId, "2026-07-04")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Technician assigned successfully for checkup"))
                .andExpect(jsonPath("$.data.machineId").value(machineId))
                .andExpect(jsonPath("$.data.technicianId").value(technicianId))
                .andExpect(jsonPath("$.data.workType").value("checkup"))
                .andExpect(jsonPath("$.data.workDate").value("2026-07-04"));
    }

    @Test
    void assignTechnicianForCheckup_duplicateActiveAssignment_returns409() throws Exception {
        String token = registerAndLoginFabCoordinator("fab2@ehps.com");
        long technicianId = createTechnician(token, "checkuptech2@ehps.com", "lithography");
        long machineId = createMachine(token, "LH-501", 1);

        mockMvc.perform(post("/api/fab/checkup-assignments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignCheckupJson(machineId, technicianId, "2026-07-04")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/fab/checkup-assignments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignCheckupJson(machineId, technicianId, "2026-07-04")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Machine already has an active checkup assignment"));
    }

    @Test
    void assignTechnicianForCheckup_specialityMismatch_returns400() throws Exception {
        String token = registerAndLoginFabCoordinator("fab3@ehps.com");
        long technicianId = createTechnician(token, "checkuptech3@ehps.com", "etcher");
        long machineId = createMachine(token, "LH-502", 1);

        mockMvc.perform(post("/api/fab/checkup-assignments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignCheckupJson(machineId, technicianId, "2026-07-04")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Technician speciality does not match machine type"));
    }
}
