using System.IO;
using System.Reflection;

namespace VCF400.Windows.E2E;

internal static class ConfigurationChecks
{
    public static void Run(string root)
    {
        var assembly = Assembly.LoadFrom(Path.Combine(root, "windows/src/VCF400.Windows/bin/Release/net8.0-windows/VCF400.Windows.dll"));
        var parse = assembly.GetType("VCF400.Windows.Options", true)!.GetMethod("Parse")!;
        foreach (var address in new[] { "http://127.0.0.1:8080", "http://localhost:8080", "http://[::1]:8080", "https://example.com/api" })
            Check.That(parse.Invoke(null, new object[] { new[] { "--api", address } }) is not null, "Allowed backend URL");
        foreach (var address in new[] { "", "http://example.com", "http://192.168.1.2", "http://127.0.0.1.example.com", "http://[2001:db8::1]", "https://user:password@example.com", "https://example.com?key=value", "https://example.com#fragment", "ftp://localhost" })
        {
            var rejected = false;
            try { parse.Invoke(null, new object[] { new[] { "--api", address } }); }
            catch (TargetInvocationException ex) when (ex.InnerException is ArgumentException) { rejected = true; }
            Check.That(rejected, "Unsafe or empty backend URL must be rejected");
        }
        Console.WriteLine("Backend URL policy: 4 allowed / 9 rejected PASS");
    }
}
