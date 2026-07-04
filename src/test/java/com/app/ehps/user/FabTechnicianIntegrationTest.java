package com.app.ehps.user;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = EhpsApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("itest")
@Transactional
class FabTechnicianIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String fabToken() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Fab Coordinator",
                                "email", "fab@ehps.com",
                                "phone", "9876543210",
                                "password", "Password@123",
                                "role", "fab_coordinator"
                        ))))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "fab@ehps.com",
                                "password", "Password@123"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        return json.get("data").get("token").asText();
    }

    private String addTechnicianJson(String name, String email, String phone, String password, String speciality) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "name", name,
                "email", email,
                "phone", phone,
                "password", password,
                "speciality", speciality
        ));
    }

    @Test
    void fullTechnicianLifecycle() throws Exception {
        String token = fabToken();

        // Add technician -> 201
        MvcResult addResult = mockMvc.perform(post("/api/fab/technicians")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addTechnicianJson("Tech One", "tech1@ehps.com", "9876543211",
                                "Password@123", "lithography")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Technician added successfully"))
                .andExpect(jsonPath("$.data.email").value("tech1@ehps.com"))
                .andExpect(jsonPath("$.data.role").value("technician"))
                .andExpect(jsonPath("$.data.speciality").value("lithography"))
                .andReturn();

        JsonNode addedJson = objectMapper.readTree(addResult.getResponse().getContentAsString());
        long technicianId = addedJson.get("data").get("technicianId").asLong();

        // Duplicate email -> 409
        mockMvc.perform(post("/api/fab/technicians")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addTechnicianJson("Tech One Dup", "tech1@ehps.com", "9876543212",
                                "Password@123", "lithography")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("User already exists with email: tech1@ehps.com"));

        // GET list contains it
        mockMvc.perform(get("/api/fab/technicians")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Technicians fetched successfully"))
                .andExpect(jsonPath("$.data[?(@.email=='tech1@ehps.com')]").exists());

        // GET by id -> 200
        mockMvc.perform(get("/api/fab/technicians/" + technicianId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("tech1@ehps.com"));

        // PUT update -> 200
        mockMvc.perform(put("/api/fab/technicians/" + technicianId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Tech One Updated",
                                "email", "tech1.updated@ehps.com",
                                "phone", "9876543213",
                                "speciality", "etcher"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Technician updated successfully"))
                .andExpect(jsonPath("$.data.name").value("Tech One Updated"))
                .andExpect(jsonPath("$.data.email").value("tech1.updated@ehps.com"))
                .andExpect(jsonPath("$.data.speciality").value("etcher"))
                .andExpect(jsonPath("$.data.role").value("technician"));
    }

    @Test
    void addTechnician_invalidPasswordAndEmail_returns400() throws Exception {
        String token = fabToken();

        mockMvc.perform(post("/api/fab/technicians")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addTechnicianJson("Bad Input", "not-an-ehps-email@gmail.com", "9876543210",
                                "weak", "lithography")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }
}
