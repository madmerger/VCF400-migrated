package org.vcf400;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.vcf400.Models.*;

@Component
public class BaselineSeeder implements ApplicationRunner {
    private static final List<String> ENTITIES = List.of("Vote", "GuestComment", "LearnPage", "Exhibit", "Award", "Setting", "SecurityOfficer");
    private final EntityManager em;
    private final ObjectMapper mapper;
    private final TransactionTemplate transaction;
    private final AdminSecurity security;
    private final boolean enabled;
    private final String password;

    public BaselineSeeder(EntityManager em, ObjectMapper mapper, TransactionTemplate transaction, AdminSecurity security,
                          @Value("${vcf.seed:true}") boolean enabled,
                          @Value("${vcf.seed-admpswrd:}") String password) {
        this.em = em; this.mapper = mapper; this.transaction = transaction; this.security = security;
        this.enabled = enabled; this.password = password;
        if (password.length() > 9) throw new IllegalArgumentException("管理者シード値は9文字以内で指定してください");
    }

    public JsonNode baseline() throws IOException {
        try (var stream = new ClassPathResource("baseline/cases.json").getInputStream()) {
            return mapper.readTree(stream).get("baseline");
        }
    }

    @Override public void run(ApplicationArguments args) throws IOException {
        if (!enabled) return;
        JsonNode baseline = baseline();
        transaction.executeWithoutResult(status -> {
            if (ENTITIES.stream().allMatch(entity -> em.createQuery("select count(row) from " + entity + " row", Long.class).getSingleResult() == 0)) {
                populate(baseline);
            }
        });
    }

    public void reset(JsonNode baseline) {
        security.revokeAll();
        transaction.executeWithoutResult(status -> {
            for (String entity : ENTITIES) {
                em.createQuery("delete from " + entity).executeUpdate();
            }
            em.clear();
            populate(baseline);
        });
    }

    private void populate(JsonNode baseline) {
        persistAll(baseline.path("exhibits"), Exhibit.class);
        persistAll(baseline.path("votes"), Vote.class);
        persistAll(baseline.path("comments"), GuestComment.class);
        persistAll(baseline.path("awards"), Award.class);
        baseline.path("settings").fields().forEachRemaining(entry -> {
            if (entry.getKey().equals("ADMPSWRD") && password.isBlank()) return;
            Setting setting = new Setting();
            setting.SETTING = entry.getKey();
            setting.VALUE = entry.getKey().equals("ADMPSWRD") ? security.hash(password) : entry.getValue().asText();
            em.persist(setting);
        });
        baseline.path("pageCollections").fields().forEachRemaining(collection -> {
            for (JsonNode page : baseline.path(collection.getValue().asText())) {
                LearnPage record = mapper.convertValue(page, LearnPage.class);
                record.COLLECTION = collection.getKey();
                em.persist(record);
            }
        });
    }

    private <T> void persistAll(JsonNode rows, Class<T> type) {
        for (JsonNode row : rows) em.persist(mapper.convertValue(row, type));
    }
}
