package org.vcf400;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.vcf400.Models.*;
import org.vcf400.Requests.*;

@Service
public class VisitorService {
    private final Exhibits exhibits;
    private final Votes votes;
    private final Awards awards;
    private final Comments comments;
    private final Settings settings;
    private final Pages pages;
    private final Inserts inserts;

    public VisitorService(Exhibits exhibits, Votes votes, Awards awards, Comments comments, Settings settings, Pages pages, Inserts inserts) {
        this.exhibits = exhibits; this.votes = votes; this.awards = awards;
        this.comments = comments; this.settings = settings; this.pages = pages;
        this.inserts = inserts;
    }

    public Map<String, Object> voteStatus() {
        if (settings.findById("ALWVOTE").map(s -> s.VALUE.equals("N")).orElse(false)) {
            Map<String, Object> result = Screens.response("ENDOFCON");
            result.putAll(Map.of("saved", false, "voteFormVisible", false));
            return result;
        }
        Map<String, Object> result = Screens.response("VOTE1");
        result.put("voteFormVisible", true);
        return result;
    }

    public synchronized Map<String, Object> vote(VoteInput input) {
        Map<String, Object> result = voteStatus();
        if (Boolean.FALSE.equals(result.get("voteFormVisible"))) return result;
        result.put("saved", false);
        if (input.exhibit().equals("DAVE")) {
            result = Screens.response("DAVE"); result.put("saved", false); return result;
        }
        String error = null;
        if (input.badge() == 0) error = "Must enter badge number";
        if (input.award() == 0) error = "Must enter Award ID";
        if (input.exhibit().isBlank()) error = "Must enter Exhibit ID";
        if (error != null) return error(result, error, 45);
        int checks = 0;
        if (votes.existsById(input.badge())) error = "You have already voted."; else checks++;
        Exhibit exhibit = exhibits.findById(input.exhibit()).orElse(null);
        if (exhibit == null || exhibit.ELIGIBLE == 0) error = "Exhibit ineligible for award"; else checks++;
        if (exhibit == null) error = "Exhibit does not exist"; else checks++;
        if (!awards.existsById(input.award())) error = "Award does not exist."; else checks++;
        result.put("checkOk", checks);
        if (checks != 4) return error(result, error, 45);
        Vote vote = new Vote();
        vote.BADGENBR = input.badge(); vote.EXHBNBR = input.exhibit(); vote.AWARDNBR = input.award();
        inserts.persist(vote);
        result = Screens.response("VOTEEND");
        result.putAll(Map.of("saved", true, "checkOk", 4));
        return result;
    }

    private Map<String, Object> error(Map<String, Object> result, String error, int length) {
        result.put("error", error);
        result.put("errorDisplay", error.substring(0, Math.min(error.length(), length)).stripTrailing());
        return result;
    }

    public synchronized Map<String, Object> post(CommentInput input) {
        Map<String, Object> result = Screens.response("ADDCMT");
        result.put("saved", false);
        String error = null;
        if (input.exhibit().isBlank()) error = "Must enter Exhibit ID";
        if (input.name().isBlank()) error = "Must enter your name    ";
        if (input.comment().isBlank()) error = "Must enter a comment";
        if (error != null) return error(result, error, 21);
        int id = comments.findFirstByOrderByCMTIDDesc().map(c -> c.CMTID + 1).orElse(1);
        if (id > 9999) throw new ResponseStatusException(HttpStatus.CONFLICT, "コメント番号上限");
        GuestComment comment = new GuestComment();
        comment.CMTID = id; comment.VISIBLE = "Y"; comment.EXHBID = input.exhibit();
        comment.GUESTNAME = input.name(); comment.GUESTCMT = input.comment();
        inserts.persist(comment);
        result = Screens.response("ENDCMT");
        result.putAll(Map.of("saved", true, "CMTID", id, "VISIBLE", "Y"));
        return result;
    }

    public Map<String, Object> read(int id, String profile) {
        Map<String, Object> result = Screens.response("READCMT");
        result.put("displayedTotal", comments.findFirstByOrderByCMTIDDesc().map(c -> c.CMTID).orElse(0));
        if (id == 0) return error(result, "Must enter CommentID", 20);
        GuestComment comment = comments.findFirstByCMTIDGreaterThanEqualOrderByCMTIDAsc(id)
                .or(() -> comments.findFirstByOrderByCMTIDDesc()).orElse(null);
        if (comment == null) { result.put("empty", true); return result; }
        String name = ""; String content; String title = "";
        if (comment.VISIBLE.equals("N")) {
            name = "Name Hidden"; content = "This comment hidden by an admin - offensive content.";
        } else if (profile.equals("MM2024") || profile.equals(comment.EXHBID)) {
            name = comment.GUESTNAME.substring(0, Math.min(11, comment.GUESTNAME.length()));
            content = comment.GUESTCMT;
            title = exhibits.findById(comment.EXHBID).map(e -> e.EXHBTITLE).orElse("");
        } else {
            content = "This comment is not part of this guestbook.";
        }
        result.putAll(Map.of("CMTID", comment.CMTID, "name", name, "comment", content, "title", title));
        return result;
    }

    public Map<String, Object> firstPage(String collection) {
        return pages.findFirstByCOLLECTIONOrderByPAGENBRAsc(collection).map(this::page)
                .orElseGet(() -> { var result = Screens.response("MAIN"); result.put("empty", true); return result; });
    }

    public Map<String, Object> fetchPage(String collection, int number) {
        return pages.findById(new PageKey(collection, number)).map(this::page)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private Map<String, Object> page(LearnPage page) {
        Map<String, Object> result = Screens.response("MAIN");
        result.putAll(Map.of("page", page.PAGENBR, "content", page.CONTENT, "collection", page.COLLECTION, "allowForward", true));
        return result;
    }

    public Map<String, Object> resolve(String collection, BranchInput input) {
        if (input.action().equals("F3")) {
            var result = Screens.response("MAIN"); result.put("ended", true); return result;
        }
        if (input.from() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "開始ページが必要です");
        if (input.action().equals("F8")) {
            int target = input.backPage() != null && input.backPage() != 0 ? input.backPage() : Math.max(0, input.from() - 1);
            return fetchPage(collection, target);
        }
        LearnPage next = pages.findById(new PageKey(collection, input.from() + 1)).orElse(null);
        if (next == null) {
            var result = Screens.response("MAIN");
            result.putAll(Map.of("content", "", "allowForward", false)); return result;
        }
        if (next.CONTENT.equals("CALL")) {
            if (!List.of("CREDITS", "NTRSTIT").contains(next.EXTRA)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "呼出し先は許可されていません");
            }
            Map<String, Object> result = fetchPage(collection, next.PAGENBR + 1);
            result.putAll(Map.of("action", "CALL", "program", next.EXTRA, "pageAfterReturn", next.PAGENBR + 1,
                    "backPage", next.PAGENBR - 1, "callScreen", Screens.response(next.EXTRA.equals("NTRSTIT") ? "INTERTEST" : "CREDITS")));
            return result;
        }
        if (next.CONTENT.equals("JUMP")) {
            if (!next.EXTRA.matches("[0-9]{4}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ページ番号が不正です");
            Map<String, Object> result = fetchPage(collection, Integer.parseInt(next.EXTRA));
            result.putAll(Map.of("action", "JUMP", "backPage", next.PAGENBR - 1)); return result;
        }
        if (next.EXTRA.equals("END")) {
            var result = Screens.response("MAIN"); result.put("ended", true); return result;
        }
        return page(next);
    }

    public Map<String, Object> kiosk(String profile) {
        Exhibit exhibit = exhibits.findById(profile).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Map<String, Object> result = Screens.response("MENU");
        var options = new java.util.ArrayList<Integer>();
        var blocked = new java.util.ArrayList<Integer>();
        if (exhibit.ELIGIBLE == 1) options.add(1); else blocked.add(1);
        if (exhibit.ENLRN400 == 1) options.add(2); else blocked.add(2);
        options.addAll(List.of(3, 4));
        result.putAll(Map.of("visibleOptions", options, "blockedOptions", blocked, "hiddenOptions", List.of(7), "title", "WELCOME TO...", "exhibit", exhibit));
        return result;
    }
}
