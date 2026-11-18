using System.Net.Http;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;

namespace VCF400.Windows;

internal sealed class ApiClient(Uri baseUri) : IDisposable
{
    private readonly HttpClient http = new() { BaseAddress = baseUri, Timeout = TimeSpan.FromSeconds(30) };
    public string Token { get; set; } = "";
    public string Error { get; private set; } = "";

    public async Task<JsonObject> Request(string path, string method = "GET", object? body = null)
    {
        Error = "";
        try
        {
            using var request = new HttpRequestMessage(new HttpMethod(method), "api" + path);
            request.Headers.CacheControl = new CacheControlHeaderValue { NoStore = true };
            if (Token.Length > 0) request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", Token);
            if (body is not null) request.Content = new StringContent(JsonSerializer.Serialize(body), Encoding.UTF8, "application/json");
            using var response = await http.SendAsync(request);
            var result = JsonNode.Parse(await response.Content.ReadAsStringAsync()) as JsonObject ?? new JsonObject();
            if (!response.IsSuccessStatusCode) Error = result["message"]?.GetValue<string>() ?? "要求を処理できません";
            return result;
        }
        catch (Exception ex) when (ex is HttpRequestException or TaskCanceledException or JsonException)
        {
            Error = "接続できません。接続を確認して再試行してください。";
            return new JsonObject();
        }
    }

    public void Dispose() => http.Dispose();
}
