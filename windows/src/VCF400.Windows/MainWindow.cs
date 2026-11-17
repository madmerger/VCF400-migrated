using System.ComponentModel;
using System.Globalization;
using System.Text.Json.Nodes;
using System.Windows;
using System.Windows.Automation;
using System.Windows.Automation.Peers;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Input;
using System.Windows.Media;

namespace VCF400.Windows;

internal sealed class MainWindow : Window
{
    private sealed class AutomationCard : Border
    {
        protected override AutomationPeer OnCreateAutomationPeer() => new CardPeer(this);
        private sealed class CardPeer(AutomationCard owner) : FrameworkElementAutomationPeer(owner)
        {
            protected override AutomationControlType GetAutomationControlTypeCore() => AutomationControlType.Group;
            protected override string GetClassNameCore() => "Card";
        }
    }
    private readonly Options options;
    private readonly ApiClient api;
    private readonly Dictionary<string, string> fields = new();
    private readonly Grid root = new();
    private JsonObject data = new();
    private JsonObject? call;
    private string view = "menu", profile = "MM2024", collection = "global";
    private bool busy, closing;
    private PasswordBox? password;
    private static readonly Brush Ink = Color("#183C48"), Accent = Color("#126C73"), Muted = Color("#627780");
    private static readonly Dictionary<string, string> Labels = new()
    {
        ["badge"] = "Badge Number", ["exhibit"] = "Exhibit ID", ["award"] = "Award ID",
        ["name"] = "Your Name", ["comment"] = "Your Comment", ["id"] = "Comment ID", ["password"] = "Password"
    };
    private static readonly Dictionary<int, string> KioskViews = new() { [1] = "vote", [2] = "learn", [3] = "post", [4] = "read" };
    private static readonly Dictionary<int, string> KioskNames = new()
    {
        [1] = "Nominate Exhibit for Award", [2] = "Learn AS/400 Navigation", [3] = "Sign Exhibit Guestbook", [4] = "Read a Guestbook Comment"
    };

    public MainWindow(Options options)
    {
        this.options = options;
        api = new ApiClient(options.ApiBase);
        Title = "VCF/400 — Visitor Studio";
        Width = options.Width; Height = options.Height;
        MinWidth = 1280; MinHeight = 720;
        ResizeMode = ResizeMode.NoResize;
        WindowStartupLocation = WindowStartupLocation.CenterScreen;
        Background = Color("#F3F6F5"); Foreground = Ink;
        FontFamily = new FontFamily("Segoe UI"); FontSize = 14;
        Content = root;
        Loaded += async (_, _) =>
        {
            await Run(async () =>
            {
                if (options.Page is int page)
                {
                    view = "learn"; collection = options.Collection;
                    data = await api.Request($"/learn/{Escape(collection)}/pages/{page}");
                }
                else await Open("menu");
            });
        };
        PreviewKeyDown += async (_, e) =>
        {
            if (e.Key is Key.F3 or Key.F5 or Key.F8 or Key.F12)
            {
                e.Handled = true;
                await Run(() => Keypress(e.Key.ToString()));
            }
            else if (e.Key == Key.Enter && e.OriginalSource is TextBox or PasswordBox)
            {
                if (e.OriginalSource is TextBox { AcceptsReturn: true }) return;
                e.Handled = true;
                await Run(view == "kiosk" ? SelectKiosk : Submit);
            }
        };
        Closing += OnClosing;
        Closed += (_, _) => api.Dispose();
        Render();
    }

    private static Brush Color(string hex) => new SolidColorBrush((System.Windows.Media.Color)ColorConverter.ConvertFromString(hex));
    private static string Text(JsonNode? node, string key) => node?[key]?.ToString() ?? "";
    private string Text(string key) => Text(data, key);
    private static bool Flag(JsonNode? node, string key) => node?[key]?.GetValue<bool>() == true;
    private static bool IsFalse(JsonNode? node, string key) => node?[key]?.GetValue<bool>() == false;
    private int Number(string key) => data[key]?.GetValue<int>() ?? 0;
    private static string Escape(string value) => Uri.EscapeDataString(value);
    private string Endpoint(string path, string? p = null) => path + (path.Contains('?') ? "&" : "?") + "profile=" + Escape(p ?? profile);
    private string FieldValue(string key) => fields.GetValueOrDefault(key, "");
    private double NumericValue(string key) => double.TryParse(FieldValue(key), NumberStyles.None, CultureInfo.InvariantCulture, out var value) ? value : 0;
    private string ParentView => profile == "MM2024" ? "menu" : "kiosk";

    private async Task Run(Func<Task> action)
    {
        if (busy) return;
        busy = true;
        root.IsEnabled = false;
        Mouse.OverrideCursor = Cursors.Wait;
        try { await action(); }
        finally
        {
            busy = false;
            Mouse.OverrideCursor = null;
            root.IsEnabled = true;
            Render();
        }
    }

    private async Task Open(string next, string? p = null, string? c = null)
    {
        call = null; ClearFields(); profile = p ?? profile; collection = c ?? collection; view = next;
        if (next == "auth")
        {
            data = new JsonObject { ["screen"] = "ADMPSWRD", ["screenTitle"] = "Are you sure you want to exit the kiosk?" };
            return;
        }
        var path = next switch
        {
            "menu" => "/menu", "vote" => Endpoint("/vote"), "post" => Endpoint("/guestbook/form"),
            "read" => Endpoint("/guestbook/1"), "learn" => $"/learn/{Escape(collection)}",
            "kiosk" => $"/kiosk/{Escape(profile)}", "exhibits" => "/exhibits", "settings" => "/settings", _ => null
        };
        if (path is null) return;
        data = await api.Request(path);
        if (next is "vote" or "post") fields["exhibit"] = data["exhibit"] is JsonValue value ? value.ToString() : "";
    }

    private void ClearFields() { password?.Clear(); fields.Clear(); }

    private async Task Logout()
    {
        try { if (api.Token.Length > 0) await api.Request("/admin/logout", "POST"); }
        finally { api.Token = ""; ClearFields(); call = null; }
    }

    private async Task SignOff()
    {
        await Logout(); view = "info";
        data = new JsonObject { ["screen"] = "SIGNOFF", ["screenTitle"] = "Sign Off the System" };
    }

    private async void OnClosing(object? sender, CancelEventArgs e)
    {
        if (closing) return;
        e.Cancel = true;
        if (busy) return;
        await Run(Logout);
        closing = true;
        Close();
    }

    private async Task Submit()
    {
        if (view == "vote") data = await api.Request(Endpoint("/vote"), "POST", new { badge = NumericValue("badge"), exhibit = FieldValue("exhibit"), award = NumericValue("award") });
        if (view == "post") data = await api.Request(Endpoint("/guestbook"), "POST", new { name = FieldValue("name"), exhibit = FieldValue("exhibit"), comment = FieldValue("comment") });
        if (view == "read") data = await api.Request(Endpoint($"/guestbook/{NumericValue("id").ToString(CultureInfo.InvariantCulture)}"));
        if (view == "auth")
        {
            JsonObject result;
            try { result = await api.Request("/admin/auth", "POST", new { password = password?.Password ?? "" }); }
            finally { ClearFields(); }
            if (Flag(result, "authenticated")) { api.Token = Text(result, "token"); await Open("settings"); }
            else data["authenticated"] = false;
        }
    }

    private async Task Keypress(string key)
    {
        if (call is not null) return;
        if (key == "F3" && view is "settings" or "auth") { await Logout(); await Open(ParentView); return; }
        if (key == "F12" && view is "vote" or "post" or "read") { await Open(ParentView); return; }
        if (view == "learn" && data["fKeys"]?[key] is not null)
        {
            if (key == "F8" && Number("page") <= 1 && Number("backPage") == 0) return;
            var result = await api.Request($"/learn/{Escape(collection)}/resolve", "POST", new { from = Number("page"), action = key, backPage = Number("backPage") });
            if (!string.IsNullOrEmpty(Text(result, "message"))) return;
            if (result["callScreen"] is JsonObject screen) call = (JsonObject)screen.DeepClone();
            if (IsFalse(result, "allowForward")) result["page"] = data["page"]?.DeepClone();
            data = result; return;
        }
        if (key == "F5" && data["fKeys"]?["F5"] is not null) await Submit();
        if (key == "F3" && view == "menu")
        {
            view = "info";
            data = new JsonObject { ["screen"] = "INTERTEST", ["screenTitle"] = "VCF/400 - How to Navigate", ["content"] = "番号を選んで機能を開き、画面下部のFキーボタンまたはキーボードで操作します。" };
        }
    }

    private async Task SelectKiosk()
    {
        var option = FieldValue("option");
        if (option == "7") await Open("auth");
        else if (int.TryParse(option, out var number) && (data["visibleOptions"] as JsonArray)?.Any(n => n?.GetValue<int>() == number) == true && KioskViews.TryGetValue(number, out var next))
            await Open(next, profile, profile);
    }

    private static T Id<T>(T element, string id) where T : DependencyObject { AutomationProperties.SetAutomationId(element, id); return element; }
    private static TextBlock Label(string text, string id = "", double size = 14, Brush? color = null)
    {
        var block = new TextBlock { Text = text, TextWrapping = TextWrapping.Wrap, FontSize = size, Foreground = color ?? Ink, Margin = new Thickness(0, 3, 0, 7) };
        if (id.Length > 0) Id(block, id);
        return block;
    }
    private Button Button(string text, string id, Func<Task> action, bool enabled = true)
    {
        var button = Id(new Button
        {
            Content = new TextBlock { Text = text, TextWrapping = TextWrapping.Wrap }, IsEnabled = enabled,
            HorizontalContentAlignment = HorizontalAlignment.Left, Background = Brushes.White, Foreground = Ink,
            BorderBrush = Color("#D6E2E2"), BorderThickness = new Thickness(1), Padding = new Thickness(12, 9, 12, 9), Margin = new Thickness(0, 0, 8, 6), Cursor = Cursors.Hand
        }, id);
        button.Click += async (_, _) => await Run(action);
        return button;
    }
    private static Border Card(UIElement child, string id = "")
    {
        var border = new AutomationCard { Background = Brushes.White, BorderBrush = Color("#DAE4E3"), BorderThickness = new Thickness(1), CornerRadius = new CornerRadius(10), Padding = new Thickness(18, 12, 18, 12), Margin = new Thickness(0, 0, 12, 12), Child = child };
        if (id.Length > 0) Id(border, id);
        return border;
    }

    private void Field(Panel panel, string name, int maxLength, bool numeric = false)
    {
        var caption = new System.Windows.Controls.Label { Content = Labels.GetValueOrDefault(name, "Selection"), Padding = new Thickness(0, 2, 0, 3), FontWeight = FontWeights.SemiBold };
        panel.Children.Add(caption);
        Control input;
        if (name == "password")
        {
            password = Id(new PasswordBox { MaxLength = maxLength }, name);
            input = password;
        }
        else
        {
            var box = Id(new TextBox { Text = FieldValue(name), MaxLength = maxLength, IsReadOnly = name == "exhibit" && profile != "MM2024", AcceptsReturn = name == "comment", TextWrapping = name == "comment" ? TextWrapping.Wrap : TextWrapping.NoWrap }, name == "option" ? "kiosk-selection" : name);
            var previous = box.Text;
            box.TextChanged += (_, _) =>
            {
                if (numeric && box.Text.Any(ch => ch < '0' || ch > '9')) { var caret = box.CaretIndex; box.Text = previous; box.CaretIndex = Math.Min(caret, previous.Length); return; }
                previous = box.Text; fields[name] = box.Text;
            };
            input = box;
        }
        input.Background = Color("#F8FAFA"); input.Foreground = Ink; input.BorderBrush = Color("#B4CBCD");
        input.Padding = new Thickness(10, 6, 10, 6); input.FontSize = 16;
        input.Margin = new Thickness(0, 0, 0, 8); input.MinHeight = name == "comment" ? 76 : 34;
        caption.Target = input;
        AutomationProperties.SetName(input, Labels.GetValueOrDefault(name, "Selection"));
        AutomationProperties.SetHelpText(input, numeric ? "数字を入力" : $"最大{maxLength}文字");
        panel.Children.Add(input);
    }

    private void Render()
    {
        root.Children.Clear(); root.ColumnDefinitions.Clear(); root.RowDefinitions.Clear();
        root.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(224) });
        root.ColumnDefinitions.Add(new ColumnDefinition());
        var sidebar = new StackPanel { Margin = new Thickness(22, 28, 14, 18) };
        var brand = Button("VCF/400", "brand", () => Open("menu", "MM2024"));
        brand.FontSize = 30; brand.Foreground = Brushes.White; brand.Background = Ink; brand.BorderThickness = new Thickness(0); brand.Padding = new Thickness(0);
        sidebar.Children.Add(brand);
        sidebar.Children.Add(Label("VISITOR STUDIO\nA living computer history", color: Color("#AED0D0")));
        sidebar.Children.Add(new Border { Height = 32 });
        sidebar.Children.Add(Button("01   メインメニュー", "nav-menu", () => Open("menu", "MM2024")));
        sidebar.Children.Add(Button("02   展示を探す", "nav-exhibits", () => Open("exhibits")));
        sidebar.Children.Add(Button("03   展示キオスク", "nav-kiosk", () => Open("kiosk", "EXH001", "EXH001")));
        sidebar.Children.Add(Button("04   投票受付状況", "nav-settings", () => Open("settings")));
        sidebar.Children.Add(new Border { Height = 40 });
        sidebar.Children.Add(Label("●  VISITOR TERMINAL\nIBM midrange · Modern experience", size: 12, color: Color("#AED0D0")));
        root.Children.Add(new Border { Background = Ink, Child = sidebar });

        var layout = new DockPanel { Margin = new Thickness(28, 18, 20, 12) };
        Grid.SetColumn(layout, 1); root.Children.Add(layout);
        var top = Label("VINTAGE COMPUTER FESTIVAL    /    " + (profile == "MM2024" ? "Visitor access" : "Kiosk · " + profile), size: 12, color: Muted);
        DockPanel.SetDock(top, Dock.Top); layout.Children.Add(top);
        if (options.Banner.Length > 0)
        {
            var banner = new Border { Background = Color("#DCEFEB"), Padding = new Thickness(12, 6, 12, 6), Margin = new Thickness(0, 4, 0, 8), Child = Label(options.Banner, "case-banner", 16) };
            DockPanel.SetDock(banner, Dock.Top); layout.Children.Add(banner);
        }
        var actions = new WrapPanel { Margin = new Thickness(0, 8, 0, 4) };
        if (data["fKeys"] is JsonObject keys)
            foreach (var pair in keys.OrderBy(k => k.Key, StringComparer.Ordinal))
            {
                var key = pair.Key;
                var disabled = view == "learn" && (Flag(data, "ended") || (key == "F5" && IsFalse(data, "allowForward")) || (key == "F8" && Number("page") <= 1 && Number("backPage") == 0));
                actions.Children.Add(Button(key + "   " + pair.Value, "key-" + key, () => Keypress(key), !disabled));
            }
        if (view is "settings" or "auth") actions.Children.Add(Button("F3   Exit", "admin-exit", () => Keypress("F3")));
        if (view != "menu") actions.Children.Add(Button("メニューへ戻る", "return-menu", () => Open(ParentView)));
        DockPanel.SetDock(actions, Dock.Bottom); layout.Children.Add(actions);
        var body = new StackPanel();
        layout.Children.Add(new ScrollViewer { Content = body, VerticalScrollBarVisibility = ScrollBarVisibility.Auto, HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled });
        body.Children.Add(Label("EXPLORE. CONNECT. REMEMBER.", size: 11, color: Accent));
        var heading = Text("screenTitle");
        if (heading.Length == 0) heading = view == "exhibits" ? "Exhibits" : view == "settings" ? "Visitor Settings" : Text("screen");
        body.Children.Add(Label(heading.Length > 0 ? heading : "Visitor Studio", "screen-title", 29));
        body.Children.Add(Label("コンピューターの歴史を、体験から。展示を巡り、学び、あなたの声を残しましょう。", size: 12, color: Muted));
        var error = new[] { api.Error, Text("errorDisplay"), Text("error") }.FirstOrDefault(s => s.Length > 0) ?? "\u00a0";
        var errorRow = Label(error, "error-row", color: Color("#A52F3E"));
        errorRow.MinHeight = 28;
        AutomationProperties.SetLiveSetting(errorRow, AutomationLiveSetting.Assertive);
        AutomationProperties.SetHelpText(errorRow, data["errorPosition"]?.ToJsonString() ?? "{}");
        body.Children.Add(errorRow);
        RenderContent(body);
        if (call is not null)
        {
            layout.IsEnabled = false; sidebar.IsEnabled = false;
            var modal = new StackPanel();
            modal.Children.Add(Label("LEARN/400 · PROGRAM", color: Accent));
            modal.Children.Add(Label(Text(call, "screenTitle"), "call-title", 24));
            modal.Children.Add(Label("Vintage Computer Festival · VCF/400"));
            modal.Children.Add(Button("LEARN/400へ戻る", "call-return", () => { call = null; return Task.CompletedTask; }));
            var overlay = new Border { Background = new SolidColorBrush(System.Windows.Media.Color.FromArgb(130, 15, 35, 45)), Child = new Border { Width = 540, VerticalAlignment = VerticalAlignment.Center, HorizontalAlignment = HorizontalAlignment.Center, Child = Card(modal) } };
            Grid.SetColumnSpan(overlay, 2); root.Children.Add(overlay);
        }
    }

    private void RenderContent(Panel body)
    {
        if (view == "menu")
        {
            var grid = new UniformGrid { Columns = 2 };
            foreach (var group in new[] { "Start Here", "Office Tasks", "Entertainment", "Exhibit Tasks", "System Tasks", "Administration" })
            {
                var panel = new StackPanel(); panel.Children.Add(Label(group, size: 17, color: Accent));
                foreach (var item in (data["items"] as JsonArray ?? new JsonArray()).OfType<JsonObject>().Where(i => Text(i, "group") == group))
                {
                    var number = item["number"]!.GetValue<int>();
                    panel.Children.Add(Button($"{number:00}   {Text(item, "title")}  ↗", "menu-" + number, async () =>
                    {
                        var action = Text(item, "action");
                        if (action == "signoff") { await SignOff(); return; }
                        await Open(action switch { "learn" => "learn", "vote" => "vote", "guestbook-post" => "post", "guestbook-read" => "read", _ => "info" }, "MM2024", "global");
                    }, Flag(item, "implemented")));
                }
                grid.Children.Add(Card(panel));
            }
            body.Children.Add(grid);
        }
        if (view == "exhibits")
        {
            var exhibits = data["exhibits"] as JsonArray;
            if (exhibits is not null)
                foreach (var exhibit in exhibits.OfType<JsonObject>())
                {
                    var id = Text(exhibit, "EXHUSRPRF"); var panel = new StackPanel();
                    panel.Children.Add(Label(id, color: Accent)); panel.Children.Add(Label(Text(exhibit, "EXHBTITLE"), size: 22));
                    panel.Children.Add(Label(Text(exhibit, "EXHBDESC"))); panel.Children.Add(Label(Text(exhibit, "EXHBITOR")));
                    panel.Children.Add(Button("展示を開く ↗", "open-exhibit-" + id, () => Open("kiosk", id, id)));
                    body.Children.Add(Card(panel, "exhibit-" + id));
                }
            if (exhibits?.Count == 0) body.Children.Add(Label("展示はまだありません。", "empty"));
        }
        if (view == "kiosk")
        {
            var panel = new StackPanel(); panel.Children.Add(Label(data["exhibit"] is JsonObject exhibit ? Text(exhibit, "EXHBTITLE") : "", size: 22));
            foreach (var option in data["visibleOptions"] as JsonArray ?? new JsonArray())
            {
                var n = option!.GetValue<int>();
                panel.Children.Add(Button($"{n}   {KioskNames[n]}  ↗", "kiosk-" + n, () => Open(KioskViews[n], profile, profile)));
            }
            Field(panel, "option", 0); panel.Children.Add(Button("選択", "kiosk-submit", SelectKiosk)); body.Children.Add(Card(panel));
        }
        var formVisible = (view == "vote" && Text("screen") == "VOTE1") || (view == "post" && Text("screen") == "ADDCMT") || view is "read" or "auth";
        if (formVisible)
        {
            var form = new StackPanel { MaxWidth = 650, HorizontalAlignment = HorizontalAlignment.Stretch };
            if (view == "vote") { Field(form, "badge", 4, true); Field(form, "exhibit", 9); Field(form, "award", 3, true); }
            if (view == "post") { Field(form, "name", 16); Field(form, "exhibit", 9); Field(form, "comment", 200); }
            if (view == "read") { Field(form, "id", 4, true); form.Children.Add(Label("Comments: " + Text("displayedTotal"), "displayed-total")); }
            if (view == "auth")
            {
                Field(form, "password", 9); form.Children.Add(Button("確認", "auth-submit", Submit));
                if (IsFalse(data, "authenticated")) form.Children.Add(Label("認証されませんでした。もう一度入力してください。", "auth-rejected"));
            }
            body.Children.Add(Card(form, "entry-form"));
        }
        if (view == "vote" && Text("screen") == "VOTE1") body.Children.Add(Label("Available Awards for This Year:\n001. Best in Show Award\n002. The Ed Fair Award\nYou may only vote for ONE award. Choose your nomination carefully!", color: Muted));
        if (view == "read")
        {
            var comment = new StackPanel();
            comment.Children.Add(Label(Text("CMTID"), "comment-id")); comment.Children.Add(Label(Text("name"), "comment-name", 22));
            comment.Children.Add(Label(Text("title"), "comment-title")); comment.Children.Add(Label(Text("comment"), "comment-text"));
            body.Children.Add(Card(comment, "comment-card"));
        }
        if (view == "learn")
        {
            var learn = new StackPanel();
            if (Flag(data, "ended")) learn.Children.Add(Label("学習を終了しました。", "learn-ended"));
            else
            {
                learn.Children.Add(Label(Number("page") != 0 ? $"PAGE {Number("page"):00}" : "LEARN/400", "page-number", 16, Accent));
                learn.Children.Add(Label(Text("content"), "learn-content", 18));
                if (IsFalse(data, "allowForward")) learn.Children.Add(Label("これ以上のページはありません。", "learn-eof"));
            }
            body.Children.Add(Card(learn, "learn-card"));
        }
        if (Flag(data, "empty")) body.Children.Add(Label("表示するデータはありません。", "empty"));
        if (new[] { "VOTEEND", "ENDCMT", "ENDOFCON", "DAVE" }.Contains(Text("screen")))
        {
            var completion = new StackPanel(); completion.Children.Add(Label(Text("screen") == "ENDOFCON" ? "◷" : "✓", size: 36, color: Accent));
            if (data["CMTID"] is not null) completion.Children.Add(Label(Text("CMTID"), "new-comment-id", 24));
            completion.Children.Add(Button("メニューへ戻る", "completion-return", () => Open(ParentView))); body.Children.Add(Card(completion, "completion"));
        }
        if (view == "settings")
        {
            var settings = new StackPanel(); settings.Children.Add(Label("VOTING PERIOD", color: Accent));
            settings.Children.Add(Label(Text("ALWVOTE") == "Y" ? "受付中 / Open" : "受付終了 / Closed", "voting-status", 24));
            if (api.Token.Length > 0) settings.Children.Add(Button("投票受付を切り替える", "toggle-voting", async () =>
            {
                var result = await api.Request("/settings", "PATCH", new { ALWVOTE = Text("ALWVOTE") == "Y" ? "N" : "Y" });
                if (!string.IsNullOrEmpty(Text(result, "ALWVOTE"))) data = result;
            }));
            else settings.Children.Add(Label("変更には展示キオスクでの管理者認証が必要です。"));
            body.Children.Add(Card(settings));
        }
        if (view == "info" && Text("content").Length > 0) body.Children.Add(Card(Label(Text("content"))));
    }
}
