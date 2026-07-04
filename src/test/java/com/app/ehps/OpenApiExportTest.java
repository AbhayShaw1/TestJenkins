package com.app.ehps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exports the live-generated OpenAPI spec (served by springdoc at {@code /v3/api-docs}) to a
 * committed, versioned file at {@code docs/openapi.json}. Running this test keeps the static
 * spec in sync with the actual controller annotations.
 */
@SpringBootTest(classes = com.app.ehps.EhpsApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("itest")
class OpenApiExportTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void exportsOpenApiSpecToDocsDirectory() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();

        String rawJson = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

        ObjectMapper prettyPrintingMapper = objectMapper.copy();
        prettyPrintingMapper.enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);

        JsonNode spec = objectMapper.readTree(rawJson);
        String prettyJson = prettyPrintingMapper.writeValueAsString(spec);

        assertThat(rawJson).contains("openapi");
        assertThat(rawJson).contains("/api/auth/login");

        Path outputPath = Path.of("docs", "openapi.json");
        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, prettyJson, StandardCharsets.UTF_8);
    }
}
