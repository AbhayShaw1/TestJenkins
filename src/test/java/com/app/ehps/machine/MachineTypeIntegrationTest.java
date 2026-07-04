package com.app.ehps.machine;

import com.app.ehps.EhpsApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration coverage for the shared machine-types endpoints (docs/API-CONTRACT.md
 * "Machine types"; BEHAVIOR-BASELINE.md §6, §11). The H2 seed already has 6 machine_types and
 * their machine_type_parameters; users are registered/logged-in per test.
 */
@SpringBootTest(classes = EhpsApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("itest")
@Transactional
class MachineTypeIntegrationTest {

    private static final String PASSWORD = "Password@123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String registerJson(String name, String email, String role) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "name", name,
                "email", email,
                "phone", "9876543210",
                "password", PASSWORD,
                "role", role
        ));
    }

    private String registerAndLogin(String name, String email, String role) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(name, email, role)))
                .andExpect(status().isCreated());

        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", PASSWORD
                        ))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(body).get("data").get("token").asText();
    }

    @Test
    void getAllTypes_withValidToken_returns200WithSixItems() throws Exception {
        String token = registerAndLogin("Any User", "any.user@ehps.com", "technician");

        mockMvc.perform(get("/api/machine-types")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Machine types fetched successfully"))
                .andExpect(jsonPath("$.data.length()").value(6));
    }

    @Test
    void getAllTypes_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/machine-types"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void getParameters_type1_returns200WithFiveItems() throws Exception {
        String token = registerAndLogin("Param User", "param.user@ehps.com", "technician");

        mockMvc.perform(get("/api/machine-types/1/parameters")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Parameters fetched successfully"))
                .andExpect(jsonPath("$.data.length()").value(5));
    }

    @Test
    void getMachinesByType_withFabToken_returns200() throws Exception {
        String token = registerAndLogin("Fab User", "fab.user@ehps.com", "fab_coordinator");

        mockMvc.perform(get("/api/machine-types/1/machines")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Machines fetched successfully"));
    }

    @Test
    void getMachinesByType_withManagerToken_returns200() throws Exception {
        String token = registerAndLogin("Manager User", "manager.user@ehps.com", "manager");

        mockMvc.perform(get("/api/machine-types/1/machines")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Machines fetched successfully"));
    }

    @Test
    void getTechniciansByType_withFabToken_returns200() throws Exception {
        String token = registerAndLogin("Fab Tech Lookup", "fab.techlookup@ehps.com", "fab_coordinator");

        mockMvc.perform(get("/api/machine-types/1/technicians")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Technicians fetched successfully"));
    }

    @Test
    void getTechniciansByType_withManagerToken_returns403() throws Exception {
        String token = registerAndLogin("Manager Tech Lookup", "manager.techlookup@ehps.com", "manager");

        mockMvc.perform(get("/api/machine-types/1/technicians")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
