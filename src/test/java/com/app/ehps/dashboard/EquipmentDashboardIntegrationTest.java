package com.app.ehps.dashboard;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = EhpsApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("itest")
@Transactional
class EquipmentDashboardIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String tokenFor(String email, String role) throws Exception {
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
        return json.get("data").get("token").asText();
    }

    @Test
    void getEquipmentHistory_returns200_withSuccessTrue() throws Exception {
        String token = tokenFor("mgr.dash@ehps.com", "manager");

        mockMvc.perform(get("/api/dashboard/equipment")
                        .param("fromDate", "2024-01-01")
                        .param("toDate", "2030-01-01")
                        .param("typeId", "0")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Equipment history fetched successfully"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getEquipmentHistory_fabCoordinatorToken_returns200() throws Exception {
        String token = tokenFor("fab.dash@ehps.com", "fab_coordinator");

        mockMvc.perform(get("/api/dashboard/equipment")
                        .param("fromDate", "2024-01-01")
                        .param("toDate", "2030-01-01")
                        .param("typeId", "0")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getEquipmentHistory_missingFromDate_returns400() throws Exception {
        String token = tokenFor("mgr.dash2@ehps.com", "manager");

        mockMvc.perform(get("/api/dashboard/equipment")
                        .param("toDate", "2030-01-01")
                        .param("typeId", "0")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("From date is required"));
    }

    @Test
    void getEquipmentHistory_technicianToken_returns403() throws Exception {
        String token = tokenFor("tech.dash@ehps.com", "technician");

        mockMvc.perform(get("/api/dashboard/equipment")
                        .param("fromDate", "2024-01-01")
                        .param("toDate", "2030-01-01")
                        .param("typeId", "0")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }
}
