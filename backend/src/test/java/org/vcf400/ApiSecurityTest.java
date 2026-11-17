package org.vcf400;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
class ApiSecurityTest {
    static final String PASSWORD = UUID.randomUUID().toString().replace("-", "").substring(0, 9);
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("vcf.seed-admpswrd", () -> PASSWORD);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:security;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired BaselineSeeder seeder;
    @Autowired Settings settings;
    @Autowired Votes votes;
    @Autowired Comments comments;
    @Autowired Pages pages;

    @BeforeEach void reset() throws Exception { seeder.reset(seeder.baseline()); }

    private String token() throws Exception {
        var response = mvc.perform(post("/api/admin/auth").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(java.util.Map.of("password", PASSWORD))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.expiresInSeconds").value(600)).andReturn();
        return mapper.readTree(response.getResponse().getContentAsByteArray()).path("token").asText();
    }

    @Test void settingsRequireToken() throws Exception {
        mvc.perform(patch("/api/settings").contentType(MediaType.APPLICATION_JSON).content("{\"ALWVOTE\":\"N\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/settings").header("Authorization", "Bearer invalid")
                .contentType(MediaType.APPLICATION_JSON).content("{\"ALWVOTE\":\"N\"}"))
                .andExpect(status().isUnauthorized());
        assertEquals("Y", settings.findById("ALWVOTE").orElseThrow().VALUE);
    }

    @Test void tokenCanUpdateVotingSetting() throws Exception {
        mvc.perform(patch("/api/settings").header("Authorization", "Bearer " + token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"ALWVOTE\":\"N\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ALWVOTE").value("N"));
        mvc.perform(get("/api/vote")).andExpect(jsonPath("$.screen").value("ENDOFCON"));
        mvc.perform(post("/api/vote").contentType(MediaType.APPLICATION_JSON)
                .content("{\"badge\":1002,\"exhibit\":\"EXH001\",\"award\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.saved").value(false));
        assertEquals(1, votes.count());
    }

    @Test void credentialsAreHashedAndNeverReturned() throws Exception {
        String hash = settings.findById("ADMPSWRD").orElseThrow().VALUE;
        assertTrue(hash.startsWith("pbkdf2$"));
        assertNotEquals(PASSWORD, hash);
        String body = mvc.perform(get("/api/settings")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(body.contains(hash));
        assertFalse(body.contains(PASSWORD));
        assertFalse(body.contains("ADMPSWRD"));
    }

    @Test void voteRejectsWrongTypesRangesAndLengths() throws Exception {
        for (String body : List.of(
                "{\"badge\":\"1002\",\"exhibit\":\"EXH001\",\"award\":1}",
                "{\"badge\":1.5,\"exhibit\":\"EXH001\",\"award\":1}",
                "{\"badge\":10000,\"exhibit\":\"EXH001\",\"award\":1}",
                "{\"badge\":-1,\"exhibit\":\"EXH001\",\"award\":1}",
                "{\"badge\":1002,\"exhibit\":\"1234567890\",\"award\":1}",
                "{\"badge\":1002,\"exhibit\":\"EXH001\",\"award\":1000}",
                "{\"badge\":null,\"exhibit\":\"EXH001\",\"award\":1}", "{}")) {
            mvc.perform(post("/api/vote").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.validationError").value(true));
        }
        assertEquals(1, votes.count());
    }

    @Test void commentLengthsAndReadIdAreValidated() throws Exception {
        for (var body : List.of(
                java.util.Map.of("name", "x".repeat(17), "exhibit", "EXH001", "comment", "hello"),
                java.util.Map.of("name", "Visitor", "exhibit", "EXH001", "comment", "x".repeat(201)))) {
            mvc.perform(post("/api/guestbook").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body)))
                    .andExpect(status().isBadRequest());
        }
        for (String id : List.of("-1", "10000", "not-a-number")) {
            mvc.perform(get("/api/guestbook/" + id)).andExpect(status().isBadRequest());
        }
        assertEquals(3, comments.count());
    }

    @Test void settingsRejectUnknownOrInvalidValues() throws Exception {
        String token = token();
        for (String body : List.of("{\"ALWVOTE\":\"X\"}", "{\"ALWVOTE\":\"Y\",\"UNKNOWN\":\"new\"}", "{\"ADMPSWRD\":\"1234567890\"}", "{}")) {
            mvc.perform(patch("/api/settings").header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        assertEquals("Y", settings.findById("ALWVOTE").orElseThrow().VALUE);
    }

    @Test void passwordRotationIsHashedAndRevokesTokens() throws Exception {
        String token = token();
        String nextPassword = UUID.randomUUID().toString().substring(0, 9);
        var result = mvc.perform(patch("/api/settings").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(java.util.Map.of("ADMPSWRD", nextPassword))))
                .andExpect(status().isOk()).andReturn();
        assertFalse(result.getResponse().getContentAsString().contains(nextPassword));
        assertTrue(settings.findById("ADMPSWRD").orElseThrow().VALUE.startsWith("pbkdf2$"));
        mvc.perform(post("/api/admin/auth").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(java.util.Map.of("password", nextPassword))))
                .andExpect(jsonPath("$.authenticated").value(true));
        mvc.perform(post("/api/admin/auth").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(java.util.Map.of("password", PASSWORD))))
                .andExpect(jsonPath("$.authenticated").value(false));
        mvc.perform(patch("/api/settings").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"ALWVOTE\":\"N\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test void authErrorsDoNotEchoInput() throws Exception {
        var response = mvc.perform(post("/api/admin/auth").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(java.util.Map.of("password", "x".repeat(10)))))
                .andExpect(status().isBadRequest()).andReturn();
        assertFalse(response.getResponse().getContentAsString().contains("xxxxxxxxxx"));
        mvc.perform(post("/api/admin/auth").contentType(MediaType.APPLICATION_JSON).content("{\"password\":123}"))
                .andExpect(status().isBadRequest());
    }

    @Test void learnCannotExecuteArbitraryProgramsOrMissingTargets() throws Exception {
        var page = pages.findById(new Models.PageKey("global", 6)).orElseThrow();
        page.EXTRA = "SHELL"; pages.saveAndFlush(page);
        mvc.perform(post("/api/learn/global/resolve").contentType(MediaType.APPLICATION_JSON)
                .content("{\"from\":5,\"action\":\"F5\"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/learn/global/pages/9999")).andExpect(status().isNotFound());
    }

    @Test void menuAndMetadataExposeLegacyContract() throws Exception {
        JsonNode menu = mapper.readTree(mvc.perform(get("/api/menu")).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertEquals("AS/400 DEMO MENU", menu.path("screenTitle").asText());
        assertEquals("メニュー標準", menu.path("fKeys").path("F3").asText());
        assertEquals(15, menu.path("items").size());
        assertEquals(11, menu.path("items").get(10).path("number").asInt());
        assertEquals("Nominate Exhibit for Award", menu.path("items").get(10).path("title").asText());
        assertFalse(menu.path("items").get(14).path("implemented").asBoolean());
        mvc.perform(get("/api/vote")).andExpect(jsonPath("$.screenTitle").value("NOMINATE EXHIBIT FOR AWARD"))
                .andExpect(jsonPath("$.inputOrder[0]").value("badge"))
                .andExpect(jsonPath("$.inputOrder[1]").value("exhibit"))
                .andExpect(jsonPath("$.inputOrder[2]").value("award"))
                .andExpect(jsonPath("$.errorPosition.row").value(6))
                .andExpect(jsonPath("$.fKeys.F12").value("Cancel"));
    }

    @Test void profileOverridesUserSuppliedExhibit() throws Exception {
        mvc.perform(post("/api/vote").param("profile", "EXH002").contentType(MediaType.APPLICATION_JSON)
                .content("{\"badge\":1002,\"exhibit\":\"EXH001\",\"award\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.error").value("Exhibit ineligible for award"));
        assertFalse(votes.existsById(1002));
    }
}
