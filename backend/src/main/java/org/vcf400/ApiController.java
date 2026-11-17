package org.vcf400;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.vcf400.Models.Setting;
import org.vcf400.Requests.*;

@RestController
@RequestMapping("/api")
@Validated
public class ApiController {
    private final VisitorService visitors;
    private final Exhibits exhibits;
    private final Settings settings;
    private final AdminSecurity security;

    public ApiController(VisitorService visitors, Exhibits exhibits, Settings settings, AdminSecurity security) {
        this.visitors = visitors; this.exhibits = exhibits; this.settings = settings; this.security = security;
    }

    @GetMapping("/menu")
    public Map<String, Object> menu() {
        var response = Screens.response("VCFMAIN");
        response.put("items", List.of(
                item(1, "Learn AS/400 Navigation", "Start Here", "learn"),
                item(2, "IBM Midrange History", "Start Here", ""),
                item(3, "About This System", "Start Here", ""),
                item(4, "About Gertie the System/34", "Start Here", ""),
                item(5, "Start OfficeVision/400", "Office Tasks", ""),
                item(6, "Start a Calendar", "Office Tasks", ""),
                item(7, "Start the Clock", "Office Tasks", ""),
                item(8, "Play Snake", "Entertainment", ""),
                item(9, "Play Yahtzee", "Entertainment", ""),
                item(10, "Play PacMan", "Entertainment", ""),
                item(11, "Nominate Exhibit for Award", "Exhibit Tasks", "vote"),
                item(12, "Sign Exhibit Guestbook", "Exhibit Tasks", "guestbook-post"),
                item(13, "Read a Guestbook Comment", "Exhibit Tasks", "guestbook-read"),
                item(80, "Sign Off the System", "System Tasks", "signoff"),
                item(90, "Start Admin Menu", "Administration", "")));
        response.put("navigation", Screens.response("INTERTEST"));
        return response;
    }

    private Map<String, Object> item(int number, String title, String group, String action) {
        return Map.of("number", number, "title", title, "group", group, "action", action, "implemented", !action.isEmpty());
    }

    @GetMapping("/exhibits")
    public Map<String, Object> exhibits() {
        var rows = exhibits.findAllByOrderByEXHUSRPRFAsc();
        return Map.of("exhibits", rows, "ids", rows.stream().map(e -> e.EXHUSRPRF).toList(), "count", rows.size());
    }

    @GetMapping("/vote")
    public Map<String, Object> voteForm(@RequestParam(defaultValue = "MM2024") @Pattern(regexp = "[A-Za-z0-9_]{1,9}") String profile) {
        var result = visitors.voteStatus();
        result.putAll(Map.of("exhibitEditable", profile.equals("MM2024"), "exhibit", profile.equals("MM2024") ? "" : profile));
        return result;
    }

    @PostMapping("/vote")
    public Map<String, Object> vote(@Valid @RequestBody VoteInput input,
            @RequestParam(defaultValue = "MM2024") @Pattern(regexp = "[A-Za-z0-9_]{1,9}") String profile) {
        return visitors.vote(profile.equals("MM2024") ? input : new VoteInput(input.badge(), profile, input.award()));
    }

    @GetMapping("/guestbook/form")
    public Map<String, Object> commentForm(@RequestParam(defaultValue = "MM2024") @Pattern(regexp = "[A-Za-z0-9_]{1,9}") String profile) {
        var result = Screens.response("ADDCMT");
        result.putAll(Map.of("exhibitEditable", profile.equals("MM2024"), "exhibit", profile.equals("MM2024") ? "" : profile));
        return result;
    }

    @PostMapping("/guestbook")
    public Map<String, Object> post(@Valid @RequestBody CommentInput input,
            @RequestParam(defaultValue = "MM2024") @Pattern(regexp = "[A-Za-z0-9_]{1,9}") String profile) {
        return visitors.post(profile.equals("MM2024") ? input : new CommentInput(input.name(), profile, input.comment()));
    }

    @GetMapping("/guestbook/{id}")
    public Map<String, Object> read(@PathVariable @Min(0) @Max(9999) int id,
            @RequestParam(defaultValue = "MM2024") @Pattern(regexp = "[A-Za-z0-9_]{1,9}") String profile) {
        return visitors.read(id, profile);
    }

    @GetMapping("/learn/{collection}")
    public Map<String, Object> first(@PathVariable @Pattern(regexp = "[A-Za-z0-9_]{1,9}") String collection) {
        return visitors.firstPage(collection);
    }

    @GetMapping("/learn/{collection}/pages/{page}")
    public Map<String, Object> page(@PathVariable @Pattern(regexp = "[A-Za-z0-9_]{1,9}") String collection,
                                  @PathVariable @Min(0) @Max(9999) int page) {
        return visitors.fetchPage(collection, page);
    }

    @PostMapping("/learn/{collection}/resolve")
    public Map<String, Object> resolve(@PathVariable @Pattern(regexp = "[A-Za-z0-9_]{1,9}") String collection,
                                      @Valid @RequestBody BranchInput input) {
        return visitors.resolve(collection, input);
    }

    @GetMapping("/kiosk/{profile}")
    public Map<String, Object> kiosk(@PathVariable @Pattern(regexp = "[A-Za-z0-9_]{1,9}") String profile) {
        return visitors.kiosk(profile);
    }

    @PostMapping("/admin/auth")
    public Map<String, Object> auth(@Valid @RequestBody AuthInput input) {
        synchronized (security) {
        var result = Screens.response("ADMPSWRD");
        boolean authenticated = security.verify(input.password());
        result.putAll(Map.of("authenticated", authenticated, "exited", authenticated, "passwordNotExposed", true));
        result.put("customError", null);
        if (authenticated) result.putAll(Map.of("token", security.issue(), "expiresInSeconds", 600));
        return result;
        }
    }

    @GetMapping("/settings")
    public Map<String, Object> settings() {
        return Map.of("ALWVOTE", settings.findById("ALWVOTE").map(s -> s.VALUE).orElse("Y"), "passwordNotExposed", true);
    }

    @PatchMapping("/settings")
    public Map<String, Object> update(@RequestHeader(value = "Authorization", required = false) String authorization,
                                      @Valid @RequestBody SettingsInput input) {
        synchronized (security) {
        security.require(authorization);
        if (input.ALWVOTE() == null && input.ADMPSWRD() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        if (input.ADMPSWRD() != null && input.ADMPSWRD().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        if (input.ALWVOTE() != null) {
            synchronized (visitors) {
                Setting setting = new Setting(); setting.SETTING = "ALWVOTE"; setting.VALUE = input.ALWVOTE();
                settings.saveAndFlush(setting);
            }
        }
        if (input.ADMPSWRD() != null) {
            Setting password = new Setting(); password.SETTING = "ADMPSWRD"; password.VALUE = security.hash(input.ADMPSWRD());
            settings.saveAndFlush(password);
            security.revokeAll();
        }
        return settings();
        }
    }
}
