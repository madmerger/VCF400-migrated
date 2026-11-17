package org.vcf400;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HexFormat;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiCasesTest {
    private static final String PASSWORD = UUID.randomUUID().toString().replace("-", "").substring(0, 9);
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("vcf.seed-admpswrd", () -> PASSWORD);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:cases;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired BaselineSeeder seeder;
    @Autowired Votes votes;
    @Autowired Comments comments;
    private final List<Map<String, Object>> results = new ArrayList<>();

    @TestFactory
    Stream<DynamicTest> commonCases() throws Exception {
        JsonNode document = mapper.readTree(Path.of("../tests/cases.json").toFile());
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode scenario : document.path("cases")) {
            tests.add(DynamicTest.dynamicTest(scenario.path("id").asText() + " " + scenario.path("name").asText(), () -> runCase(document, scenario)));
        }
        return tests.stream();
    }

    private void runCase(JsonNode document, JsonNode scenario) throws Exception {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("caseId", scenario.path("id").asText());
        record.put("name", scenario.path("name").asText());
        record.put("executedAt", Instant.now().toString());
        record.put("environment", "API (Spring Boot/H2)");
        record.put("expected", scenario.path("expected"));
        Object canonical = mapper.convertValue(Map.of("inputs", scenario.path("inputs"), "expected", scenario.path("expected")), Object.class);
        byte[] payload = mapper.writer().with(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsBytes(canonical);
        record.put("fingerprint", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload)));
        record.put("video", "");
        JsonNode input = scenario.path("inputs");
        if (input.path("action").asText().equals("F12") || input.path("operation").asText().equals("compareLedger")) {
            record.put("status", "UI専用・フェーズ3/4");
            record.put("actual", Map.of());
            record.put("notes", "取消ボタン・全画面配置の照合はUIで実施");
            results.add(record);
            return;
        }
        try {
            ObjectNode baseline = document.path("baseline").deepCopy();
            JsonNode setup = scenario.path("setup");
            setup.fields().forEachRemaining(entry -> {
                if (entry.getKey().equals("settings")) {
                    entry.getValue().fields().forEachRemaining(setting -> ((ObjectNode) baseline.path("settings")).set(setting.getKey(), setting.getValue()));
                } else if (entry.getKey().equals("replacePage")) {
                    for (JsonNode page : baseline.path("pages")) {
                        if (page.path("PAGENBR").equals(entry.getValue().path("PAGENBR"))) ((ObjectNode) page).setAll((ObjectNode) entry.getValue());
                    }
                } else baseline.set(entry.getKey(), entry.getValue());
            });
            seeder.reset(baseline);
            long votesBefore = votes.count();
            long commentsBefore = comments.count();
            MvcResult response = execute(scenario, input);
            JsonNode body = mapper.readTree(response.getResponse().getContentAsByteArray());
            assertFalse(body.toString().contains(PASSWORD), "レスポンスにパスワードが含まれる");
            assertFalse(body.has("ADMPSWRD"));
            JsonNode expected = scenario.path("expected");
            boolean invalid = expected.path("validationError").asBoolean();
            assertEquals(invalid ? 400 : 200, response.getResponse().getStatus());
            ObjectNode actual = mapper.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = expected.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                if (field.getKey().equals("note")) continue;
                actual.set(field.getKey(), body.path(field.getKey()));
                assertEquals(field.getValue(), body.path(field.getKey()), field.getKey());
            }
            if (scenario.path("function").asText().equals("投票")) {
                assertEquals(votesBefore + (expected.path("saved").asBoolean() ? 1 : 0), votes.count());
            }
            if (scenario.path("function").asText().equals("ゲストブック投稿") && input.has("name")) {
                assertEquals(commentsBefore + (expected.path("saved").asBoolean() ? 1 : 0), comments.count());
                if (expected.path("saved").asBoolean()) {
                    var saved = comments.findById(body.path("CMTID").asInt()).orElseThrow();
                    assertEquals(input.path("comment").asText(), saved.GUESTCMT);
                    assertEquals(input.path("exhibit").asText(), saved.EXHBID);
                }
            }
            record.put("actual", actual);
            record.put("status", "合格");
            record.put("notes", "期待値の全業務フィールドを照合。投票・投稿は保存件数も検証。");
        } catch (Throwable failure) {
            record.put("status", "不合格");
            record.put("actual", Map.of("failure", failure.getClass().getSimpleName()));
            record.put("notes", "JUnitレポート参照");
            throw failure;
        } finally {
            results.add(record);
        }
    }

    private MvcResult execute(JsonNode scenario, JsonNode input) throws Exception {
        String function = scenario.path("function").asText();
        String profile = input.path("profile").asText("MM2024");
        return switch (function) {
            case "投票" -> {
                if (input.has("open") || input.has("selectMenuOption")) yield request(get("/api/vote").param("profile", profile), null);
                yield request(post("/api/vote"), input);
            }
            case "ゲストブック投稿" -> {
                if (input.has("open")) yield request(get("/api/guestbook/form").param("profile", profile), null);
                yield request(post("/api/guestbook"), input);
            }
            case "ゲストブック閲覧" -> request(get("/api/guestbook/" + input.path("id").asInt(1)).param("profile", profile), null);
            case "LEARN/400" -> {
                if (input.has("open")) yield request(get("/api/learn/" + input.path("open").asText()), null);
                yield request(post("/api/learn/global/resolve"), input);
            }
            case "キオスク" -> {
                if (input.has("password")) yield authentication(input);
                yield request(get("/api/kiosk/" + profile), null);
            }
            case "管理者認証" -> authentication(input);
            case "設定" -> request(get("/api/settings"), null);
            case "展示一覧" -> request(get("/api/exhibits"), null);
            default -> throw new IllegalStateException("未実装のケース機能");
        };
    }

    private MvcResult authentication(JsonNode input) throws Exception {
        String password = input.path("password").asText();
        if (password.equals("${VCF_SEED_ADMPSWRD}")) password = PASSWORD;
        return request(post("/api/admin/auth"), mapper.valueToTree(Map.of("password", password)));
    }

    private MvcResult request(MockHttpServletRequestBuilder request, JsonNode body) throws Exception {
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body));
        return mvc.perform(request).andReturn();
    }

    @AfterAll
    void report() throws Exception {
        Path directory = Path.of("test-results");
        Files.createDirectories(directory);
        long passed = results.stream().filter(r -> r.get("status").equals("合格")).count();
        long failed = results.stream().filter(r -> r.get("status").equals("不合格")).count();
        long ui = results.size() - passed - failed;
        mapper.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("api_e2e_results.json").toFile(), Map.of(
                "summary", Map.of("total", results.size(), "passed", passed, "failed", failed, "uiOnly", ui), "results", results));
        StringBuilder markdown = new StringBuilder("# API E2E結果\n\n環境: API (Spring Boot/H2) / MockMvc（HTTPコントローラー・JPA・実H2）\n\n");
        markdown.append("全").append(results.size()).append("件 / 合格 ").append(passed).append(" / 不合格 ").append(failed).append(" / UI専用 ").append(ui).append("\n\n");
        markdown.append("| ケースID | 名称 | 合否 | 期待値 | 実際値 |\n|---|---|---|---|---|\n");
        for (var result : results) {
            markdown.append("| ").append(result.get("caseId")).append(" | ").append(result.get("name")).append(" | ").append(result.get("status"))
                    .append(" | ").append(mapper.writeValueAsString(result.get("expected"))).append(" | ").append(mapper.writeValueAsString(result.get("actual"))).append(" |\n");
        }
        Files.writeString(directory.resolve("api_e2e_results.md"), markdown.toString());
        System.out.printf("API共通ケース: 全%d件 / 合格%d / 不合格%d / UI専用%d%n", results.size(), passed, failed, ui);
    }
}
