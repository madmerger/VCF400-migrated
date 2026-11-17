package org.vcf400;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScreensTest {
    @Test void allFunctionKeyLabelsMatchLedgerDefinitionsVerbatim() throws Exception {
        var ledger = new ObjectMapper().readTree(Path.of("../spec/tools/spec_data.json").toFile()).path("画面台帳");
        int checked = 0;
        for (var row : ledger) {
            if (!row.get(2).asText().equals("Fキー")) continue;
            String path = row.get(1).asText();
            String screen = path.substring(path.lastIndexOf(" → ") + (path.contains(" → ") ? 3 : 1));
            if (path.equals("VCFMAIN")) screen = "VCFMAIN";
            if (screen.equals("LEARN/400")) screen = "MAIN";
            String[] key = row.get(3).asText().split(" ", 2);
            Map<?, ?> keys = (Map<?, ?>) Screens.response(screen).get("fKeys");
            assertEquals(key[1], keys.get(key[0]), row.get(0).asText());
            checked++;
        }
        assertEquals(10, checked);
    }
}
