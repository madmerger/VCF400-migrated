using System.Diagnostics;
using System.IO;
using System.Text;
using System.Text.Encodings.Web;
using System.Text.Json;
using System.Text.Json.Nodes;

namespace VCF400.Windows.E2E;

internal static class Program
{
    private static readonly JsonSerializerOptions Json = new() { WriteIndented = true, Encoder = JavaScriptEncoder.UnsafeRelaxedJsonEscaping };
    [STAThread]
    private static int Main(string[] args)
    {
        var root = Directory.GetCurrentDirectory();
        if (!File.Exists(Path.Combine(root, "tests/cases.json"))) throw new InvalidOperationException("Run from repository root");
        if (args.Length == 2 && args[0] == "--finalize")
        {
            var existing = JsonNode.Parse(File.ReadAllText(Path.Combine(root, "windows/test-results/windows_e2e_results.json")))!.AsObject();
            Check.That(existing["runId"]?.ToString() == args[1], "Requested runId does not match current report");
            return FinalizeVideo(root, existing);
        }
        var runId = "windows-" + DateTime.UtcNow.ToString("yyyyMMddTHHmmssfff") + "-" + Guid.NewGuid().ToString("N")[..8];
        var artifacts = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "artifacts", runId);
        Directory.CreateDirectory(artifacts);
        var output = Path.Combine(root, "windows/test-results"); Directory.CreateDirectory(output);
        Directory.CreateDirectory(Path.Combine(output, "screenshots"));
        var recordings = Path.Combine(output, "recordings", runId); Directory.CreateDirectory(recordings);
        var document = JsonNode.Parse(File.ReadAllText(Path.Combine(root, "tests/cases.json")))!;
        var cases = document["cases"]!.AsArray().OfType<JsonObject>().ToArray();
        var selected = args.Length == 2 && args[0] == "--case" ? args[1] : null;
        if (args.Length > 0 && selected is null) throw new ArgumentException("Optional arguments: --case E2E-001");
        if (selected is not null) cases = cases.Where(c => c["id"]!.ToString() == selected).ToArray();
        Check.That(cases.Length > 0, "No matching cases");
        var fingerprints = Fingerprints(root);
        var results = new JsonArray();
        foreach (var c in cases) results.Add(new JsonObject
        {
            ["caseId"] = c["id"]!.ToString(), ["name"] = c["name"]!.DeepClone(), ["fingerprint"] = fingerprints[c["id"]!.ToString()]!.DeepClone(),
            ["executedAt"] = "", ["environment"] = "Windows (.NET 8 WPF)", ["status"] = "不合格", ["expected"] = c["expected"]!.DeepClone(),
            ["actual"] = new JsonObject(), ["video"] = "", ["recording"] = "", ["notes"] = "未実行"
        });
        var report = new JsonObject { ["runId"] = runId, ["results"] = results };
        Write(output, report);
        try
        {
            using var backend = new Backend(root, artifacts, document["baseline"]!);
            var ledger = JsonNode.Parse(File.ReadAllText(Path.Combine(root, "spec/tools/spec_data.json")))!["画面台帳"]!.AsArray();
            for (var i = 0; i < cases.Length; i++)
            {
                var c = cases[i]; var result = results[i]!.AsObject(); var id = c["id"]!.ToString();
                result["executedAt"] = DateTime.UtcNow.ToString("O");
                try
                {
                    var before = backend.Api("/api/e2e/reset", "POST", c["setup"] ?? new JsonObject());
                    using var observer = new ApiObserver(backend.Address);
                    using var ui = new Visitor(root, observer, id + " · " + c["name"], c["inputs"]?["from"]?.GetValue<int>() == 8 ? 8 : null);
                    var recording = Path.Combine(recordings, id + ".mp4");
                    using (var capture = new Recording(recording, ui.Bounds))
                    {
                        result["actual"] = new Scenarios(backend, observer, ledger).Run(ui, c);
                        var after = backend.Api("/api/e2e/counts");
                        foreach (var (key, function) in new[] { ("votes", "投票"), ("comments", "ゲストブック投稿") })
                        {
                            var increment = c["function"]!.ToString() == function && c["expected"]?["saved"]?.GetValue<bool>() == true ? 1 : 0;
                            Check.That(after[key]!.GetValue<int>() == before[key]!.GetValue<int>() + increment, id + " DB count " + key);
                        }
                        if (new[] { "E2E-001", "E2E-003", "E2E-027", "E2E-037", "E2E-048", "E2E-058" }.Contains(id)) ui.Screenshot(Path.Combine(output, "screenshots", id + ".png"));
                        Thread.Sleep(350);
                    }
                    result["recording"] = Path.GetRelativePath(root, recording).Replace('\\', '/');
                    result["status"] = "合格"; result["notes"] = "実WPF UI操作・表示照合・DB件数照合";
                }
                catch (Exception ex)
                {
                    result["status"] = "不合格"; result["recording"] = "";
                    result["notes"] = ex.GetType().Name + ": " + ex.Message.Replace(backend.Password, "[redacted]");
                }
                Console.WriteLine(id + (Passed(result) ? " PASS" : " FAIL: " + result["notes"]));
                Write(output, report);
            }
            var reviews = new JsonArray(); report["reviews"] = reviews;
            foreach (var name in new[] { "signoff", "F3", "eof", "first-page" })
            {
                try
                {
                    backend.Api("/api/e2e/reset", "POST", new JsonObject());
                    using var observer = new ApiObserver(backend.Address);
                    using var ui = new Visitor(root, observer, "REVIEW · " + name, name == "eof" ? 8 : null);
                    using (var capture = new Recording(Path.Combine(recordings, "review-" + name + ".mp4"), ui.Bounds))
                    {
                        new Scenarios(backend, observer, ledger).Review(ui, name);
                        ui.Screenshot(Path.Combine(output, "screenshots", "review-" + name + ".png"));
                    }
                    reviews.Add(new JsonObject { ["name"] = name, ["status"] = "合格" }); Console.WriteLine("REVIEW " + name + " PASS");
                }
                catch (Exception ex)
                {
                    reviews.Add(new JsonObject { ["name"] = name, ["status"] = "不合格", ["notes"] = ex.Message.Replace(backend.Password, "[redacted]") });
                    Console.WriteLine("REVIEW " + name + " FAIL: " + ex.Message.Replace(backend.Password, "[redacted]"));
                }
                Write(output, report);
            }
            Write(output, report);
            var passed = results.Count(Passed); Console.WriteLine($"Windows UI: {passed}/{cases.Length} PASS; reviews: {reviews.Count(Passed)}/4 PASS");
            if (cases.Length == 58 && results.All(Passed) && reviews.All(Passed)) return FinalizeVideo(root, report);
            return passed == cases.Length && reviews.All(Passed) ? 0 : 1;
        }
        catch (Exception ex)
        {
            foreach (var r in results.OfType<JsonObject>()) r["video"] = "";
            Write(output, report); Console.Error.WriteLine(ex.GetType().Name + ": " + ex.Message); return 1;
        }
    }
    private static int FinalizeVideo(string root, JsonObject report)
    {
        var output = Path.GetFullPath(Path.Combine(root, "windows/test-results"));
        var results = report["results"]!.AsArray();
        foreach (var r in results.OfType<JsonObject>()) r["video"] = "";
        report.Remove("videoGeneratedAt"); Write(output, report);
        try
        {
            var runId = report["runId"]!.ToString();
            Check.That(runId.StartsWith("windows-") && runId.All(c => char.IsAsciiLetterOrDigit(c) || c == '-'), "Invalid runId");
            var artifacts = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "artifacts", runId); Directory.CreateDirectory(artifacts);
            var recordings = Path.GetFullPath(Path.Combine(output, "recordings", runId));
            var current = Fingerprints(root);
            Check.That(current.Count == 58 && results.Count == 58 && results.All(Passed) && results.Select(r => r!["caseId"]!.ToString()).Distinct().Count() == 58, "58 successful cases required");
            var reviews = report["reviews"]!.AsArray();
            Check.That(reviews.Count == 4 && reviews.All(Passed) && reviews.Select(r => r!["name"]!.ToString()).Order().SequenceEqual(new[] { "F3", "eof", "first-page", "signoff" }.Order()), "Four successful review flows required");
            foreach (var r in results.OfType<JsonObject>())
            {
                Check.Equal(r["fingerprint"], current[r["caseId"]!.ToString()], "Current case fingerprint required");
                Check.That(r["environment"]?.ToString() == "Windows (.NET 8 WPF)", "Windows result environment required");
                var path = Path.GetFullPath(Path.Combine(root, r["recording"]!.ToString()));
                Check.That(path.StartsWith(recordings + Path.DirectorySeparatorChar, StringComparison.OrdinalIgnoreCase) && File.Exists(path) && new FileInfo(path).Length > 0, "Current-run recording missing");
            }
            var concat = Path.Combine(artifacts, "concat.txt");
            File.WriteAllLines(concat, results.Select(r => "file '" + Path.Combine(root, r!["recording"]!.ToString()).Replace('\\', '/').Replace("'", "'\\''") + "'"), new UTF8Encoding(false));
            var video = Path.Combine(root, "video/3_Windows.mp4"); Directory.CreateDirectory(Path.GetDirectoryName(video)!);
            Recording.Command("ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-f", "concat", "-safe", "0", "-i", concat, "-an", "-c:v", "libx264", "-preset", "fast", "-crf", "26", "-pix_fmt", "yuv420p", "-movflags", "+faststart", video);
            Check.That(File.Exists(video) && new FileInfo(video).Length > 0, "Combined video missing");
            Check.That(new FileInfo(video).Length <= 100_000_000 || File.Exists(Path.Combine(root, ".gitattributes")) && File.ReadAllText(Path.Combine(root, ".gitattributes")).Contains("filter=lfs"), "Video exceeds 100MB; configure Git LFS before publishing");
            foreach (var r in results.OfType<JsonObject>()) r["video"] = "video/3_Windows.mp4";
            report["videoGeneratedAt"] = DateTime.UtcNow.ToString("O"); Write(output, report);
            Console.WriteLine($"Video finalized: {runId}; 58/58 PASS; reviews 4/4 PASS; video/3_Windows.mp4"); return 0;
        }
        catch (Exception ex) { Write(output, report); Console.Error.WriteLine(ex.GetType().Name + ": " + ex.Message); return 1; }
    }
    private static bool Passed(JsonNode? result) => result?["status"]?.ToString() == "合格";
    private static JsonObject Fingerprints(string root)
    {
        var info = new ProcessStartInfo("python") { WorkingDirectory = root, UseShellExecute = false, RedirectStandardOutput = true, RedirectStandardError = true };
        info.Environment["PYTHONUTF8"] = "1";
        info.ArgumentList.Add("-c");
        info.ArgumentList.Add("import json,sys;from pathlib import Path;sys.path.insert(0,'tests/tools');from case_results import fingerprint;d=json.loads(Path('tests/cases.json').read_text(encoding='utf-8'));print(json.dumps({c['id']:fingerprint(c,d['baseline']) for c in d['cases']}))");
        using var process = Process.Start(info)!; var json = process.StandardOutput.ReadToEnd(); var error = process.StandardError.ReadToEnd(); process.WaitForExit();
        Check.That(process.ExitCode == 0, "Python fingerprint calculation failed: " + error); return JsonNode.Parse(json)!.AsObject();
    }
    private static void Write(string output, JsonObject report)
    {
        var results = report["results"]!.AsArray(); var passed = results.Count(Passed);
        report["summary"] = new JsonObject { ["total"] = results.Count, ["passed"] = passed, ["failed"] = results.Count - passed };
        File.WriteAllText(Path.Combine(output, "windows_e2e_results.json"), report.ToJsonString(Json) + "\n", new UTF8Encoding(false));
        static string Cell(JsonNode? node) => (node is JsonValue ? node.ToString() : node?.ToJsonString(Json) ?? "").Replace("\r", "").Replace("\n", " ").Replace("|", "\\|");
        var text = $"# Windows E2E結果\n\nrunId: {report["runId"]}\n\n全{results.Count}件 / 合格 {passed} / 不合格 {results.Count - passed}\n\n|ケース|合否|期待値|実際値|動画|備考|\n|---|---|---|---|---|---|\n";
        text += string.Join("\n", results.Select(r => $"|{Cell(r!["caseId"])}|{Cell(r["status"])}|{Cell(r["expected"])}|{Cell(r["actual"])}|{Cell(r["video"])}|{Cell(r["notes"])}|"));
        File.WriteAllText(Path.Combine(output, "windows_e2e_results.md"), text + "\n", new UTF8Encoding(false));
    }
}
