package com.app.ehps.machine;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = EhpsApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("itest")
@Transactional
class FabMachineIntegrationTest {

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

    private String addMachineJson(String machineCode, long typeId) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "machineCode", machineCode,
                "typeId", typeId,
                "installDate", "2024-01-01"
        ));
    }

    @Test
    void addMachine_success_returns201() throws Exception {
        String token = registerAndLoginFabCoordinator("fab1@ehps.com");

        mockMvc.perform(post("/api/fab/machines")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addMachineJson("LH-100", 1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Machine added successfully"))
                .andExpect(jsonPath("$.data.machineCode").value("LH-100"))
                .andExpect(jsonPath("$.data.typeId").value(1));
    }

    @Test
    void addMachine_duplicateCode_returns409() throws Exception {
        String token = registerAndLoginFabCoordinator("fab2@ehps.com");

        mockMvc.perform(post("/api/fab/machines")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addMachineJson("LH-101", 1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/fab/machines")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addMachineJson("LH-101", 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Machine code already exists"));
    }

    @Test
    void addMachine_wrongPrefix_returns400() throws Exception {
        String token = registerAndLoginFabCoordinator("fab3@ehps.com");

        mockMvc.perform(post("/api/fab/machines")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addMachineJson("XX-1", 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Machine code must start with LH for selected machine type"));
    }

    @Test
    void getMyMachines_containsAddedMachine() throws Exception {
        String token = registerAndLoginFabCoordinator("fab4@ehps.com");

        mockMvc.perform(post("/api/fab/machines")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addMachineJson("LH-102", 1)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/fab/machines")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Machines fetched successfully"))
                .andExpect(jsonPath("$.data[?(@.machineCode == 'LH-102')]").exists());
    }

    @Test
    void getMachineById_own_returns200_andForeignOrUnknown_returns404() throws Exception {
        String token = registerAndLoginFabCoordinator("fab5@ehps.com");
        String otherToken = registerAndLoginFabCoordinator("fab6@ehps.com");

        MvcResult addResult = mockMvc.perform(post("/api/fab/machines")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addMachineJson("LH-103", 1)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(addResult.getResponse().getContentAsString());
        long machineId = json.at("/data/machineId").asLong();

        mockMvc.perform(get("/api/fab/machines/" + machineId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Machine fetched successfully"))
                .andExpect(jsonPath("$.data.machineCode").value("LH-103"));

        // Unknown id.
        mockMvc.perform(get("/api/fab/machines/999999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Machine not found for this fab coordinator"));

        // Foreign id (belongs to fab5, accessed by fab6).
        mockMvc.perform(get("/api/fab/machines/" + machineId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Machine not found for this fab coordinator"));
    }

    @Test
    void updateMachine_success_returns200() throws Exception {
        String token = registerAndLoginFabCoordinator("fab7@ehps.com");

        MvcResult addResult = mockMvc.perform(post("/api/fab/machines")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addMachineJson("LH-104", 1)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(addResult.getResponse().getContentAsString());
        long machineId = json.at("/data/machineId").asLong();

        mockMvc.perform(put("/api/fab/machines/" + machineId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addMachineJson("LH-105", 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Machine updated successfully"))
                .andExpect(jsonPath("$.data.machineCode").value("LH-105"));
    }
}
