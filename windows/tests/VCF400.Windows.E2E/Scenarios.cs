using System.Text.Json.Nodes;
using FlaUI.Core.AutomationElements;

namespace VCF400.Windows.E2E;

internal sealed class Scenarios(Backend backend, ApiObserver observer, JsonArray ledger)
{
    private static string S(JsonNode node, string key, string fallback = "") => string.IsNullOrEmpty(node[key]?.ToString()) ? fallback : node[key]!.ToString();
    private static bool Yes(JsonNode node, string key) => node[key]?.GetValue<bool>() == true;
    private static IEnumerable<JsonNode> Rows(JsonNode? node) => node is JsonArray a ? a.OfType<JsonNode>() : [];
    private static JsonObject Values(string json) => JsonNode.Parse(json)!.AsObject();
    private static void Readonly(Visitor ui, string value)
    {
        Check.That(ui.Exists("exhibit").Patterns.Value.Pattern.IsReadOnly, "Kiosk Exhibit ID readonly");
        Check.That(ui.Exists("exhibit").AsTextBox().Text == value, "Kiosk Exhibit ID value");
    }
    private void Authenticate(Visitor ui, JsonObject input)
    {
        var password = S(input, "password");
        ui.Password(password == "${VCF_SEED_ADMPSWRD}" ? backend.Password : password);
    }
    private static void ClosedVoting(Visitor ui)
    {
        ui.HasText("voting-status", "受付中 / Open");
        ui.Click("toggle-voting"); ui.HasText("voting-status", "受付終了 / Closed");
        ui.Click("return-menu"); ui.Click("kiosk-1");
        ui.HasText("screen-title", "The voting period has ended and you can no longer vote."); ui.Absent("badge");
    }
    public JsonObject Run(Visitor ui, JsonObject scenario)
    {
        var input = scenario["inputs"]!.AsObject(); var expected = scenario["expected"]!.AsObject();
        var function = S(scenario, "function"); JsonObject actual = new();
        if (function is "投票" or "ゲストブック投稿")
        {
            var vote = function == "投票"; var menu = vote ? 11 : 12; var path = vote ? "/vote" : "/guestbook/form";
            if (input["profile"] is not null)
            {
                ui.Kiosk(S(input, "profile")); actual = ui.Act(path, () => ui.Click(vote ? "kiosk-1" : "kiosk-3"));
            }
            else actual = ui.Menu(menu);
            if (input["open"] is not null) Readonly(ui, S(expected, "exhibit"));
            else if (S(input, "action") == "F12")
            {
                ui.Fill(vote ? Values("{\"badge\":1002,\"exhibit\":\"EXH001\",\"award\":1}") : Values("{\"name\":\"Visitor\",\"exhibit\":\"EXH001\",\"comment\":\"Cancelled\"}"), vote ? ["badge", "exhibit", "award"] : ["name", "exhibit", "comment"]);
                if (vote) ui.Key("F12"); else ui.Click("key-F12");
                ui.HasText("screen-title", "AS/400 DEMO MENU"); actual = Values("{\"saved\":false,\"returned\":true}");
            }
            else if (input["selectMenuOption"] is not null) ui.Absent("badge");
            else
            {
                ui.Fill(input, vote ? ["badge", "exhibit", "award"] : ["name", "exhibit", "comment"]);
                actual = ui.Act(vote ? "/vote" : "/guestbook", () => { if (vote) ui.Key("F5"); else ui.Click("key-F5"); });
            }
        }
        else if (function == "ゲストブック閲覧")
        {
            if (input["profile"] is not null) { ui.Kiosk(S(input, "profile")); actual = ui.Act("/guestbook/1", () => ui.Click("kiosk-4")); }
            else actual = ui.Menu(13);
            if (input["open"] is null) { ui.Set("id", S(input, "id")); actual = ui.Act("/guestbook/" + S(input, "id"), () => ui.Key("F5")); }
            foreach (var pair in new Dictionary<string, string> { ["name"] = "comment-name", ["comment"] = "comment-text", ["title"] = "comment-title", ["CMTID"] = "comment-id" })
                if (expected.ContainsKey(pair.Key)) ui.HasText(pair.Value, S(expected, pair.Key));
            if (expected.ContainsKey("displayedTotal")) ui.HasText("displayed-total", "Comments: " + S(expected, "displayedTotal"));
        }
        else if (function == "LEARN/400")
        {
            if (input["open"] is not null)
            {
                if (S(input, "open") == "global") actual = ui.Menu(1);
                else { var profile = S(input, "open"); ui.Kiosk(profile); actual = ui.Act("/learn/" + profile, () => ui.Click("kiosk-2")); }
            }
            else
            {
                var from = input["from"]?.GetValue<int>() ?? 1;
                if (from != 8) ui.LearnTo(from);
                actual = ui.Act("/learn/global/resolve", () => ui.Key(S(input, "action")));
                if (actual["callScreen"] is not null) { ui.HasText("call-title", "VCF/400 - Credits and Copyright"); ui.Click("call-return"); ui.HasText("page-number", "PAGE 07"); }
            }
            if (expected["page"] is not null) ui.HasText("page-number", $"PAGE {expected["page"]!.GetValue<int>():00}");
            if (expected.ContainsKey("content")) ui.HasText("learn-content", S(expected, "content"));
            if (Yes(expected, "ended")) ui.Exists("learn-ended");
            if (expected["allowForward"]?.GetValue<bool>() == false) { ui.Exists("learn-eof"); Check.That(!ui.Exists("key-F5").IsEnabled, "EOF F5 disabled"); }
            if (Yes(expected, "validationError")) ui.HasText("error-row", S(actual, "message"));
        }
        else if (function == "キオスク" && !input.ContainsKey("password"))
        {
            var profile = S(input, "profile"); ui.Click("nav-exhibits");
            actual = ui.Act("/kiosk/" + profile, () => ui.Click("open-exhibit-" + profile));
            foreach (var n in Rows(expected["visibleOptions"])) ui.Exists("kiosk-" + n);
            foreach (var n in Rows(expected["blockedOptions"])) ui.Absent("kiosk-" + n);
            ui.Absent("kiosk-7");
        }
        else if (function is "管理者認証" or "キオスク")
        {
            ui.OpenAuth(S(input, "profile", "EXH001")); Authenticate(ui, input);
            actual = ui.Act("/admin/auth", () => ui.Click("auth-submit"));
            if (Yes(actual, "authenticated")) ClosedVoting(ui);
            else ui.Exists("auth-rejected");
        }
        else if (function == "展示一覧")
        {
            actual = ui.Act("/exhibits", () => ui.Click("nav-exhibits"));
            foreach (var id in Rows(expected["ids"])) ui.Exists("exhibit-" + id);
            Check.That(Rows(actual["ids"]).All(id => ui.Find("exhibit-" + id) is not null), "Exhibit cards match API");
            if (expected["count"]!.GetValue<int>() == 0) ui.Exists("empty");
        }
        else if (function == "設定")
        {
            actual = ui.Act("/settings", () => ui.Click("nav-settings")); ui.HasText("voting-status", "受付中 / Open"); ui.Absent("toggle-voting");
        }
        else if (S(input, "operation") == "compareLedger") actual = Ledger(ui, input["screens"]!.AsArray());
        else throw new InvalidOperationException("Unimplemented scenario " + S(scenario, "id"));
        if (!string.IsNullOrEmpty(actual["screenTitle"]?.ToString())) Check.That(ledger.Any(row => row?[3]?.ToString() == S(actual, "screenTitle")), "API title belongs to ledger");
        if (expected["error"] is not null || expected["errorDisplay"] is not null) ui.HasText("error-row", S(expected, "errorDisplay", S(expected, "error")));
        if (Yes(expected, "saved")) ui.Exists("completion");
        if (expected["screen"] is not null) ui.HasText("screen-title", S(actual, "screenTitle", S(actual, "screen")));
        if (Yes(expected, "empty")) ui.Exists("empty");
        var captured = new JsonObject();
        foreach (var pair in expected)
        {
            if (pair.Key == "note") continue;
            Check.Equal(actual[pair.Key], pair.Value, S(scenario, "id") + " response " + pair.Key);
            captured[pair.Key] = actual[pair.Key]?.DeepClone();
        }
        Check.That(!ui.Text("screen-title").Contains(backend.Password) && !ui.Text("error-row").Contains(backend.Password), "No password in UI text");
        return captured;
    }

    private JsonObject Ledger(Visitor ui, JsonArray required)
    {
        var visited = new HashSet<string>();
        void Title(string screen, string title, bool source = true)
        {
            ui.HasText("screen-title", title);
            if (source) Check.That(ledger.Any(row => row?[3]?.ToString() == title), "Ledger title " + screen);
            visited.Add(screen);
        }
        var menu = backend.Api("/api/menu");
        foreach (var item in Rows(menu["items"]))
        {
            var text = ui.Text("menu-" + S(item, "number"));
            Check.That(text.Contains(S(item, "title")) && text.Contains(item["number"]!.GetValue<int>().ToString("00")), "Menu number/title");
            Check.That(ui.Exists("menu-" + S(item, "number")).IsEnabled == Yes(item, "implemented"), "Menu availability");
        }
        foreach (var (screen, number, path) in new[] { ("VCFMAIN", 0, "/menu"), ("VOTE1", 11, "/vote"), ("ADDCMT", 12, "/guestbook/form"), ("READCMT", 13, "/guestbook/1"), ("MAIN", 1, "/learn/global") })
        {
            if (number == 0) ui.Click("nav-menu"); else ui.MenuPage(number);
            var info = backend.Api("/api" + path); Title(screen, S(info, "screenTitle"));
            foreach (var row in ledger.OfType<JsonArray>().Where(r => r[2]?.ToString() == "Fキー"))
            {
                var origin = row[1]!.ToString(); var target = origin == "VCFMAIN" ? "VCFMAIN" : origin.Split(" → ").Last() == "LEARN/400" ? "MAIN" : origin.Split(" → ").Last();
                if (target != screen) continue;
                var label = row[3]!.ToString(); var split = label.IndexOf(' '); var key = label[..split];
                Check.That(ui.Text("key-" + key).Replace(" ", "") == label.Replace(" ", ""), "Ledger F key " + key);
            }
            if (info["errorPosition"] is JsonObject position)
            {
                var help = JsonNode.Parse(ui.Exists("error-row").HelpText);
                Check.Equal(help, position, "UI error row/column/length");
            }
            if (info["inputOrder"] is JsonArray order && order.Count > 0)
                Check.That(ui.InputOrder().SequenceEqual(order.Select(v => v!.ToString())), "UI input order " + screen);
        }
        ui.Click("nav-menu"); ui.Key("F3"); Title("INTERTEST", "VCF/400 - How to Navigate");
        ui.Kiosk("EXH001"); Title("MENU", "WELCOME TO..."); foreach (var n in new[] { 1, 2, 3, 4 }) ui.Exists("kiosk-" + n); ui.Absent("kiosk-7");
        ui.Set("kiosk-selection", "7"); ui.Click("kiosk-submit"); Title("ADMPSWRD", "Are you sure you want to exit the kiosk?");
        ui.Password(backend.Password); ui.Click("auth-submit"); Title("SETTINGS", "Visitor Settings", false);
        ui.Click("toggle-voting"); ui.HasText("voting-status", "受付終了 / Closed");
        ui.Click("nav-menu"); ui.Click("menu-11"); Title("ENDOFCON", "The voting period has ended and you can no longer vote."); ui.Absent("badge");
        ui.Click("nav-settings"); ui.Click("toggle-voting"); ui.HasText("voting-status", "受付中 / Open");
        ui.MenuPage(11); ui.Fill(Values("{\"badge\":1002,\"exhibit\":\"EXH001\",\"award\":1}"), "badge", "exhibit", "award");
        var vote = ui.Act("/vote", () => ui.Click("key-F5")); Check.That(Yes(vote, "saved"), "Ledger vote saved"); Title("VOTEEND", S(vote, "screenTitle"));
        ui.MenuPage(11); ui.Fill(Values("{\"badge\":1003,\"exhibit\":\"DAVE\",\"award\":1}"), "badge", "exhibit", "award"); ui.Click("key-F5"); Title("DAVE", "DAVE", false);
        ui.MenuPage(12); ui.Fill(Values("{\"name\":\"Ledger\",\"exhibit\":\"EXH001\",\"comment\":\"Screen verification\"}"), "name", "exhibit", "comment");
        ui.Click("key-F5"); Title("ENDCMT", "Thank you for commenting on this exhibit! Your Comment ID Is:"); ui.HasText("new-comment-id", "6");
        ui.Click("nav-menu"); ui.LearnTo(5); ui.Click("key-F5"); ui.HasText("call-title", "VCF/400 - Credits and Copyright"); visited.Add("CREDITS");
        Check.That(ledger.Any(row => row?[3]?.ToString() == "VCF/400 - Credits and Copyright"), "CALL ledger title"); ui.Click("call-return"); ui.HasText("page-number", "PAGE 07");
        ui.Click("nav-menu"); ui.Click("menu-80"); Title("SIGNOFF", "Sign Off the System", false);
        observer.FailExhibits = true;
        try { ui.Click("nav-exhibits"); ui.HasText("error-row", "接続できません。接続を確認して再試行してください。"); }
        finally { observer.FailExhibits = false; }
        ui.Click("nav-menu"); ui.HasText("screen-title", "AS/400 DEMO MENU");
        Check.That(visited.SetEquals(required.Select(n => n!.ToString())), "All and only required ledger screens visited");
        backend.Api("/api/e2e/reset", "POST", new JsonObject());
        return Values("{\"matchesAllInScopeScreens\":true}");
    }

    public void Review(Visitor ui, string name)
    {
        if (name is "signoff" or "F3")
        {
            ui.Click("nav-kiosk"); ui.Set("kiosk-selection", "7"); ui.Click("kiosk-submit"); ui.Password(backend.Password);
            ui.Act("/admin/auth", () => ui.Click("auth-submit")); ui.Exists("toggle-voting"); var token = observer.AuthToken;
            Check.That(token.Length > 0, "Authenticated token observed in memory");
            if (name == "signoff") { ui.Click("nav-menu"); ui.Click("menu-80"); ui.HasText("screen-title", "Sign Off the System"); }
            else { ui.Key("F3"); ui.Exists("kiosk-selection"); }
            ui.Click("nav-settings"); ui.Exists("voting-status"); ui.Absent("toggle-voting");
            backend.Api("/api/settings", "PATCH", Values("{\"ALWVOTE\":\"N\"}"), token, 401);
        }
        else if (name == "eof")
        {
            ui.Click("key-F5"); ui.Exists("learn-eof"); ui.HasText("page-number", "PAGE 08");
            ui.Key("F8"); ui.HasText("page-number", "PAGE 07"); ui.HasText("error-row", ""); Check.That(ui.Exists("key-F5").IsEnabled, "EOF restored F5");
        }
        else
        {
            ui.Menu(1); ui.HasText("page-number", "PAGE 01"); Check.That(!ui.Exists("key-F8").IsEnabled, "First-page F8 disabled");
            var requests = observer.Resolves; ui.Key("F8"); ui.HasText("page-number", "PAGE 01");
            Check.That(observer.Resolves == requests, "First-page physical F8 sends no request");
            ui.Key("F5"); ui.HasText("page-number", "PAGE 02"); Check.That(observer.Resolves == requests + 1, "One forward request");
            Check.That(ui.Exists("key-F8").IsEnabled, "F8 enabled on page 2"); ui.Click("key-F8"); ui.HasText("page-number", "PAGE 01"); ui.HasText("error-row", "");
        }
    }
}
