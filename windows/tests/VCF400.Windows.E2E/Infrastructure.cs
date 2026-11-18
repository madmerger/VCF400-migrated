using System.Collections.Concurrent;
using System.Diagnostics;
using System.Net;
using System.Net.Http;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json.Nodes;
using System.IO;

namespace VCF400.Windows.E2E;

internal static class Check
{
    public static void That(bool value, string description) { if (!value) throw new InvalidOperationException(description); }
    public static void Equal(JsonNode? actual, JsonNode? expected, string description) => That(JsonNode.DeepEquals(actual, expected), description);
    public static T Until<T>(Func<T?> get, string description, int seconds = 20) where T : class
    {
        var watch = Stopwatch.StartNew();
        while (watch.Elapsed.TotalSeconds < seconds)
        {
            try { if (get() is T value) return value; }
            catch (Exception ex) when (ex is System.Runtime.InteropServices.COMException or InvalidOperationException or TimeoutException) { }
            Thread.Sleep(80);
        }
        throw new TimeoutException(description);
    }
}

internal sealed class Backend : IDisposable
{
    public readonly string Password = Convert.ToHexString(RandomNumberGenerator.GetBytes(5))[..9];
    private readonly string key = Convert.ToHexString(RandomNumberGenerator.GetBytes(32));
    private readonly Process process;
    private readonly StreamWriter log;
    private readonly HttpClient http = new();
    public string Address { get; }
    public static int FreePort()
    {
        var listener = new TcpListener(IPAddress.Loopback, 0); listener.Start();
        var port = ((IPEndPoint)listener.LocalEndpoint).Port; listener.Stop(); return port;
    }
    public Backend(string root, string artifacts, JsonNode baseline)
    {
        Address = $"http://127.0.0.1:{FreePort()}";
        var info = new ProcessStartInfo("cmd.exe") { WorkingDirectory = Path.Combine(root, "backend"), UseShellExecute = false, RedirectStandardOutput = true, RedirectStandardError = true, CreateNoWindow = true };
        info.ArgumentList.Add("/c"); info.ArgumentList.Add($"mvn -s maven-settings-gcs.xml -q spring-boot:run -Dspring-boot.run.profiles=e2e -Dspring-boot.run.arguments=--server.port={new Uri(Address).Port}");
        info.Environment["VCF_SEED_ADMPSWRD"] = Password; info.Environment["VCF_E2E_KEY"] = key;
        info.Environment["SPRING_DATASOURCE_URL"] = "jdbc:h2:mem:vcf400;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        info.Environment["SPRING_PROFILES_ACTIVE"] = "e2e";
        info.Environment["VCF_SEED"] = "true";
        info.Environment.Remove("DATABASE_URL");
        log = new StreamWriter(Path.Combine(artifacts, "backend.log")) { AutoFlush = true };
        process = new Process { StartInfo = info };
        void Output(object sender, DataReceivedEventArgs e)
        {
            if (e.Data is not null) lock (log) log.WriteLine(e.Data.Replace(Password, "[redacted]").Replace(key, "[redacted]"));
        }
        process.OutputDataReceived += Output; process.ErrorDataReceived += Output;
        process.Start(); process.BeginOutputReadLine(); process.BeginErrorReadLine();
        http.BaseAddress = new Uri(Address); http.DefaultRequestHeaders.Add("X-E2E-Key", key); http.Timeout = TimeSpan.FromSeconds(3);
        try
        {
            var timer = Stopwatch.StartNew(); var ready = false;
            while (timer.Elapsed.TotalSeconds < 180)
            {
                Check.That(!process.HasExited, "backend terminated during startup; see backend.log");
                try
                {
                    var counts = Api("/api/e2e/counts");
                    if (counts["votes"]?.GetValue<int>() == baseline["votes"]!.AsArray().Count && counts["comments"]?.GetValue<int>() == baseline["comments"]!.AsArray().Count) { ready = true; break; }
                }
                catch (Exception ex) when (ex is HttpRequestException or TaskCanceledException) { }
                Thread.Sleep(100);
            }
            Check.That(ready, "backend seed readiness timed out");
            Console.WriteLine("Backend ready: authenticated counts match baseline before first reset");
        }
        catch { Dispose(); throw; }
    }
    public JsonObject Api(string path, string method = "GET", JsonNode? body = null, string? token = null, int expectedStatus = 200)
    {
        using var request = new HttpRequestMessage(new HttpMethod(method), path);
        if (body is not null) request.Content = new StringContent(body.ToJsonString(), Encoding.UTF8, "application/json");
        if (token is not null) request.Headers.TryAddWithoutValidation("Authorization", "Bearer " + token);
        using var response = http.Send(request);
        Check.That((int)response.StatusCode == expectedStatus, $"Backend {method} {path}: HTTP {(int)response.StatusCode}, expected {expectedStatus}");
        return JsonNode.Parse(response.Content.ReadAsStringAsync().GetAwaiter().GetResult())!.AsObject();
    }
    public void Dispose()
    {
        if (!process.HasExited) { process.Kill(true); process.WaitForExit(10000); }
        process.Dispose(); http.Dispose(); lock (log) log.Dispose();
    }
}

internal sealed record Exchange(long Sequence, string Path, JsonObject Body, int Status, string CacheControl);

internal sealed class ApiObserver : IDisposable
{
    private readonly HttpListener listener = new();
    private readonly HttpClient http = new();
    private readonly ConcurrentQueue<Exchange> exchanges = new();
    private long sequence;
    private readonly Task worker;
    public string Address { get; }
    public bool FailExhibits { get; set; }
    public string AuthToken { get; private set; } = "";
    public long Sequence => Interlocked.Read(ref sequence);
    public int Resolves => exchanges.Count(e => e.Path.Contains("/resolve"));
    public ApiObserver(string backend)
    {
        Address = $"http://127.0.0.1:{Backend.FreePort()}";
        http.BaseAddress = new Uri(backend);
        listener.Prefixes.Add(Address + "/"); listener.Start();
        worker = Task.Run(async () =>
        {
            while (listener.IsListening)
            {
                HttpListenerContext context;
                try { context = await listener.GetContextAsync(); }
                catch (Exception ex) when (ex is HttpListenerException or ObjectDisposedException) { break; }
                await Relay(context);
            }
        });
    }
    private async Task Relay(HttpListenerContext context)
    {
        try
        {
            var path = context.Request.RawUrl!;
            if (FailExhibits && path.StartsWith("/api/exhibits")) { context.Response.Abort(); return; }
            using var message = new HttpRequestMessage(new HttpMethod(context.Request.HttpMethod), path);
            if (context.Request.HasEntityBody)
                message.Content = new StringContent(await new StreamReader(context.Request.InputStream).ReadToEndAsync(), Encoding.UTF8, "application/json");
            if (context.Request.Headers["Authorization"] is string auth) message.Headers.TryAddWithoutValidation("Authorization", auth);
            using var response = await http.SendAsync(message);
            var bytes = await response.Content.ReadAsByteArrayAsync();
            var json = JsonNode.Parse(bytes) as JsonObject ?? new JsonObject();
            if (path.StartsWith("/api/admin/auth") && json["token"] is not null) AuthToken = json["token"]!.GetValue<string>();
            json.Remove("token");
            var cache = response.Headers.CacheControl?.ToString() ?? "";
            exchanges.Enqueue(new Exchange(Interlocked.Increment(ref sequence), path, json, (int)response.StatusCode, cache));
            context.Response.StatusCode = (int)response.StatusCode; context.Response.ContentType = "application/json";
            context.Response.Headers["Cache-Control"] = cache;
            await context.Response.OutputStream.WriteAsync(bytes);
            context.Response.Close();
        }
        catch (Exception ex) when (ex is HttpRequestException or IOException or ObjectDisposedException or HttpListenerException) { context.Response.Abort(); }
    }
    public Exchange After(long after, string path) => Check.Until(() => exchanges.FirstOrDefault(e => e.Sequence > after && e.Path.Contains("/api" + path)), "Missing UI request: " + path);
    public void Dispose() { listener.Stop(); worker.GetAwaiter().GetResult(); listener.Close(); http.Dispose(); AuthToken = ""; }
}

internal sealed class Recording : IDisposable
{
    private readonly Process process;
    public string Path { get; }
    public Recording(string path, System.Drawing.Rectangle bounds)
    {
        Check.That(bounds.Width >= 1280 && bounds.Height >= 720 && bounds.X >= 0 && bounds.Y >= 0, "App recording region must be at least 1280x720 and on-screen");
        bounds.Width -= bounds.Width % 2; bounds.Height -= bounds.Height % 2;
        Path = path;
        process = Start("ffmpeg", ["-hide_banner", "-loglevel", "error", "-y", "-f", "gdigrab", "-framerate", "15", "-offset_x", bounds.X.ToString(), "-offset_y", bounds.Y.ToString(), "-video_size", $"{bounds.Width}x{bounds.Height}", "-draw_mouse", "0", "-i", "desktop", "-an", "-c:v", "libx264", "-preset", "ultrafast", "-crf", "25", "-pix_fmt", "yuv420p", path], true);
        Thread.Sleep(300);
        Check.That(!process.HasExited, "ffmpeg gdigrab failed");
    }
    public static Process Start(string exe, IEnumerable<string> args, bool input = false)
    {
        var info = new ProcessStartInfo(exe) { UseShellExecute = false, CreateNoWindow = true, RedirectStandardInput = input, RedirectStandardOutput = true, RedirectStandardError = true };
        foreach (var arg in args) info.ArgumentList.Add(arg);
        var p = Process.Start(info)!;
        p.BeginOutputReadLine(); p.BeginErrorReadLine(); return p;
    }
    public static void Command(string exe, params string[] args)
    {
        using var p = Start(exe, args); p.WaitForExit(); Check.That(p.ExitCode == 0, exe + " failed");
    }
    public void Dispose()
    {
        if (!process.HasExited) { process.StandardInput.WriteLine("q"); process.StandardInput.Flush(); if (!process.WaitForExit(15000)) process.Kill(true); }
        Check.That(process.ExitCode == 0 && File.Exists(Path) && new FileInfo(Path).Length > 0, "Recording failed"); process.Dispose();
    }
}
