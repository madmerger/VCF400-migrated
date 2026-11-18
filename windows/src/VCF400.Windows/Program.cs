using System.Globalization;
using System.IO;
using System.Text.Json.Nodes;
using System.Windows;

namespace VCF400.Windows;

internal sealed record Options(Uri ApiBase, string Collection, int? Page, string Banner, double Width, double Height)
{
    public static Options Parse(string[] args)
    {
        var values = new Dictionary<string, string>();
        for (var i = 0; i < args.Length; i += 2)
        {
            if (i + 1 == args.Length || !new[] { "--api", "--collection", "--page", "--banner", "--width", "--height" }.Contains(args[i]))
                throw new ArgumentException("引数: --api URL --collection ID --page NUMBER --banner TEXT --width NUMBER --height NUMBER");
            values[args[i]] = args[i + 1];
        }
        var configPath = Path.Combine(AppContext.BaseDirectory, "appsettings.json");
        var config = File.Exists(configPath) ? JsonNode.Parse(File.ReadAllText(configPath)) : null;
        var address = values.GetValueOrDefault("--api") ?? Environment.GetEnvironmentVariable("VCF_API_BASE") ?? config?["ApiBase"]?.GetValue<string>() ?? "http://127.0.0.1:8080";
        if (!Uri.TryCreate(address.TrimEnd('/') + "/", UriKind.Absolute, out var uri) || (uri.Scheme != "http" && uri.Scheme != "https") || !string.IsNullOrEmpty(uri.UserInfo) || uri.Query.Length != 0 || uri.Fragment.Length != 0)
            throw new ArgumentException("API URLには認証情報・クエリー・フラグメントを含めずhttp(s) URLを指定してください。");
        if (uri.Scheme == "http" && !uri.IsLoopback)
            throw new ArgumentException("ループバック以外のAPI URLにはHTTPSが必要です。HTTPは127.0.0.1・localhost・::1などのループバックにのみ使用できます。");
        int? page = values.TryGetValue("--page", out var p) ? int.Parse(p, CultureInfo.InvariantCulture) : null;
        double Dimension(string key, double fallback, double minimum)
        {
            var value = values.TryGetValue(key, out var text) ? double.Parse(text, CultureInfo.InvariantCulture) : fallback;
            if (!double.IsFinite(value) || value < minimum) throw new ArgumentException($"{key} は {minimum} 以上で指定してください。");
            return value;
        }
        return new(uri, values.GetValueOrDefault("--collection", "global"), page, values.GetValueOrDefault("--banner", ""), Dimension("--width", 1280, 1280), Dimension("--height", 800, 720));
    }
}

internal static class Program
{
    [STAThread]
    public static void Main(string[] args)
    {
        try
        {
            var options = Options.Parse(args);
            new Application().Run(new MainWindow(options));
        }
        catch (Exception ex) when (ex is ArgumentException or IOException or System.Text.Json.JsonException or FormatException or OverflowException)
        {
            MessageBox.Show("起動設定を確認してください。\n" + (ex is ArgumentException ? ex.Message : "設定ファイルまたは数値引数が不正です。"), "VCF/400", MessageBoxButton.OK, MessageBoxImage.Error);
        }
    }
}
