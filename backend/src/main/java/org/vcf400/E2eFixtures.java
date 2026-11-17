package org.vcf400;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Profile("e2e")
@RestController
@RequestMapping("/api/e2e")
public class E2eFixtures {
    private final BaselineSeeder seeder;
    private final Votes votes;
    private final Comments comments;
    private final String key;

    public E2eFixtures(BaselineSeeder seeder, Votes votes, Comments comments,
                       @Value("${VCF_E2E_KEY:}") String key) {
        this.seeder = seeder; this.votes = votes; this.comments = comments; this.key = key;
    }

    private void authorize(String supplied) {
        if (key.isBlank() || supplied == null || !MessageDigest.isEqual(
                key.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }

    @PostMapping("/reset")
    public Map<String, Long> reset(@RequestHeader(value = "X-E2E-Key", required = false) String supplied,
                                  @RequestBody JsonNode setup) throws IOException {
        authorize(supplied);
        ObjectNode baseline = (ObjectNode) seeder.baseline();
        if (!setup.isObject()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        setup.fields().forEachRemaining(entry -> {
            if (entry.getKey().equals("settings")) {
                ((ObjectNode) baseline.path("settings")).setAll((ObjectNode) entry.getValue());
            } else if (entry.getKey().equals("replacePage")) {
                for (JsonNode page : baseline.path("pages")) {
                    if (page.path("PAGENBR").equals(entry.getValue().path("PAGENBR"))) {
                        ((ObjectNode) page).setAll((ObjectNode) entry.getValue());
                    }
                }
            } else baseline.set(entry.getKey(), entry.getValue());
        });
        seeder.reset(baseline);
        return Map.of("votes", votes.count(), "comments", comments.count());
    }

    @GetMapping("/counts")
    public Map<String, Long> counts(@RequestHeader(value = "X-E2E-Key", required = false) String supplied) {
        authorize(supplied);
        return Map.of("votes", votes.count(), "comments", comments.count());
    }
}
