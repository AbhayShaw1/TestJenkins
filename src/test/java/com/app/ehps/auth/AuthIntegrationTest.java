package com.app.ehps.auth;

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

@SpringBootTest(classes = EhpsApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("itest")
@Transactional
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String registerJson(String name, String email, String phone, String password, String role) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "name", name,
                "email", email,
                "phone", phone,
                "password", password,
                "role", role
        ));
    }

    @Test
    void register_returns201_withSuccessTrue_andEmail() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Jane Doe", "jane.reg@ehps.com", "9876543210", "Password@123", "technician")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Registration successful"))
                .andExpect(jsonPath("$.data.email").value("jane.reg@ehps.com"))
                .andExpect(jsonPath("$.data.role").value("technician"));
    }

    @Test
    void login_returns200_withNonEmptyToken() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Login User", "login.user@ehps.com", "9876543211", "Password@123", "manager")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "login.user@ehps.com",
                                "password", "Password@123"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value("login.user@ehps.com"));
    }

    @Test
    void register_invalidEmailAndWeakPassword_returns400_withErrors() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Bad Input", "not-an-ehps-email@gmail.com", "9876543210", "weak", "technician")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        String json = registerJson("Dup User", "dup.user@ehps.com", "9876543210", "Password@123", "fab_coordinator");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("User already exists with email: dup.user@ehps.com"));
    }

    @Test
    void protectedRoute_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/machine-types"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unauthorized. Please provide a valid JWT token."));
    }

    @Test
    void protectedRoute_withBogusToken_returns401() throws Exception {
        mockMvc.perform(get("/api/machine-types")
                        .header("Authorization", "Bearer this.is.not.a.valid.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unauthorized. Please provide a valid JWT token."));
    }
}
