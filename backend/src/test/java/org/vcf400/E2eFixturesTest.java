package org.vcf400;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
class E2eFixturesTest {
    private static final String KEY = UUID.randomUUID().toString();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("VCF_E2E_KEY", () -> KEY);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:e2e-fixtures;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
    }
    @Autowired MockMvc mvc;

    @Test void resetRequiresKeyAndAppliesSetup() throws Exception {
        mvc.perform(post("/api/e2e/reset").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/e2e/reset").header("X-E2E-Key", "invalid").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/e2e/reset").header("X-E2E-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                .content("{\"comments\":[],\"settings\":{\"ALWVOTE\":\"N\"}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.comments").value(0));
        mvc.perform(get("/api/vote")).andExpect(jsonPath("$.screen").value("ENDOFCON"));
        mvc.perform(post("/api/e2e/reset").header("X-E2E-Key", KEY).contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/e2e/reset").header("X-E2E-Key", KEY).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.comments").value(3));
        mvc.perform(get("/api/e2e/counts").header("X-E2E-Key", KEY)).andExpect(status().isOk())
                .andExpect(jsonPath("$.votes").value(1));
        mvc.perform(get("/api/e2e/counts")).andExpect(status().isUnauthorized());
    }
}
