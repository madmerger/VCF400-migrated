using System.Diagnostics;
using System.IO;
using System.Text.Json.Nodes;
using FlaUI.Core.AutomationElements;
using FlaUI.Core.Input;
using FlaUI.Core.WindowsAPI;
using FlaUI.UIA3;

namespace VCF400.Windows.E2E;

internal sealed class Visitor : IDisposable
{
    private readonly UIA3Automation automation = new();
    private readonly FlaUI.Core.Application app;
    private readonly Process process;
    private readonly Window window;
    private readonly ApiObserver observer;
    public Visitor(string root, ApiObserver observer, string banner, int? page = null)
    {
        this.observer = observer;
        var info = new ProcessStartInfo(Path.Combine(root, "windows/src/VCF400.Windows/bin/Release/net8.0-windows/VCF400.Windows.exe")) { UseShellExecute = false };
        foreach (var arg in new[] { "--api", observer.Address, "--banner", banner, "--width", "1280", "--height", "800" }) info.ArgumentList.Add(arg);
        if (page is not null) { info.ArgumentList.Add("--collection"); info.ArgumentList.Add("global"); info.ArgumentList.Add("--page"); info.ArgumentList.Add(page.ToString()!); }
        process = Process.Start(info)!;
        app = FlaUI.Core.Application.Attach(process);
        window = Check.Until(() => app.GetMainWindow(automation), "WPF main window");
        window.Focus();
        Exists("screen-title"); Ready();
        if (page is null) HasText("screen-title", "AS/400 DEMO MENU"); else HasText("page-number", $"PAGE {page:00}");
        HasText("case-banner", banner);
    }
    public System.Drawing.Rectangle Bounds => window.BoundingRectangle;
    public AutomationElement? Find(string id) => window.FindFirstDescendant(cf => cf.ByAutomationId(id));
    public AutomationElement Exists(string id) => Check.Until(() => Find(id), "UI element " + id);
    public void Ready() => Check.Until(() => window.IsEnabled && (Find("nav-menu")?.IsEnabled == true || Find("call-return")?.IsEnabled == true) ? window : null, "UI idle");
    public string Text(string id) => Exists(id).Name;
    public void HasText(string id, string text) => Check.Until(() => Find(id) is AutomationElement e && e.Name.Trim() == text.Trim() ? e : null, "UI text " + id + " expected " + text);
    public void Absent(string id) { Ready(); Check.That(Find(id) is null, "Unexpected UI element " + id); }
    public void Click(string id)
    {
        Ready(); var element = Exists(id); Check.That(element.IsEnabled, "Disabled button " + id);
        if (element.Patterns.ScrollItem.IsSupported) element.Patterns.ScrollItem.Pattern.ScrollIntoView();
        element.AsButton().Invoke(); WaitChanged();
    }
    private void WaitChanged() { Thread.Sleep(100); Ready(); }
    public void Key(string key)
    {
        Ready(); window.Focus(); Keyboard.Press((VirtualKeyShort)Enum.Parse(typeof(VirtualKeyShort), key)); Keyboard.Release((VirtualKeyShort)Enum.Parse(typeof(VirtualKeyShort), key)); WaitChanged();
    }
    public void Fill(JsonObject values, params string[] names)
    {
        foreach (var name in names) Set(name, values[name]?.ToString() ?? "");
    }
    public void Set(string id, string value) { Ready(); Exists(id).AsTextBox().Text = value; }
    public void Password(string value)
    {
        var password = Exists("password"); Check.That(password.Properties.IsPassword.Value, "PasswordBox must be protected");
        password.Focus(); Keyboard.Type(value);
    }
    public JsonObject Act(string path, Action action)
    {
        var before = observer.Sequence; action(); var response = observer.After(before, path); Ready();
        Check.That(response.CacheControl.Contains("no-store"), "API cache-control missing no-store");
        if (path != "/admin/auth" && !string.IsNullOrEmpty(response.Body["screenTitle"]?.ToString())) HasText("screen-title", response.Body["screenTitle"]!.GetValue<string>());
        return (JsonObject)response.Body.DeepClone();
    }
    public JsonObject Menu(int number) => Act(number switch { 1 => "/learn/global", 11 => "/vote", 12 => "/guestbook/form", 13 => "/guestbook/1", _ => throw new ArgumentException("menu") }, () => Click("menu-" + number));
    public void MenuPage(int number) { Click("nav-menu"); HasText("screen-title", "AS/400 DEMO MENU"); Menu(number); }
    public void Kiosk(string profile) { Click("nav-exhibits"); Exists("exhibit-" + profile); Click("open-exhibit-" + profile); Exists("kiosk-selection"); }
    public void OpenAuth(string profile = "EXH001") { Kiosk(profile); Set("kiosk-selection", "7"); Click("kiosk-submit"); Exists("password"); }
    public void LearnTo(int from)
    {
        Menu(1);
        if (from >= 2) { Key("F5"); HasText("page-number", "PAGE 02"); }
        if (from >= 5) { Key("F5"); HasText("page-number", "PAGE 05"); }
        if (from >= 7) { Key("F5"); Exists("call-title"); Click("call-return"); HasText("page-number", "PAGE 07"); }
    }
    public void Screenshot(string path) { Ready(); window.CaptureToFile(path); }
    public string[] InputOrder() => Exists("entry-form").FindAllDescendants().Where(e => e.ControlType == FlaUI.Core.Definitions.ControlType.Edit).Select(e => e.AutomationId).ToArray();
    public void Dispose()
    {
        var pid = app.ProcessId;
        try
        {
            window.Close();
            try { using var alive = Process.GetProcessById(pid); if (!alive.WaitForExit(10000)) alive.Kill(true); }
            catch (ArgumentException) { }
        }
        finally { app.Dispose(); process.Dispose(); automation.Dispose(); }
    }
}
