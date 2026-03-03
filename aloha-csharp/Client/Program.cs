using A2A;

var options = ParseArgs(args);

if (options.ShowHelp)
{
    PrintUsage();
    return;
}

if (string.IsNullOrWhiteSpace(options.Message))
{
    Log.Error("client", "--message is required.");
    PrintUsage();
    Environment.Exit(1);
    return;
}

var transport = options.Transport.ToLowerInvariant();
if (transport is not ("jsonrpc" or "rest" or "grpc"))
{
    Log.Error("client", $"Unsupported transport: {options.Transport}");
    PrintUsage();
    Environment.Exit(1);
    return;
}

if (transport == "grpc")
{
    Log.Error("client", "gRPC is not supported in aloha-csharp (A2A .NET SDK v0.3.3-preview).");
    Environment.Exit(1);
    return;
}

var port = options.Port ?? (transport == "rest" ? 15002 : 15001);

// Initialize log file output
Log.InitLogFile(transport);

Log.Info("client", "============================================================");
Log.Info("client", "A2A Host Client (SDK)");
Log.Info("client", $"  Transport: {transport.ToUpperInvariant()}");
Log.Info("client", "  SDK Mode: JSON-RPC");
Log.Info("client", $"  Agent: {options.Host}:{port}");
Log.Info("client", $"  Message: {options.Message}");
Log.Info("client", $"  Streaming: {options.Stream}");
Log.Info("client", "============================================================");

try
{
    var baseUrl = new Uri($"http://{options.Host}:{port}");

    // Resolve agent card
    Log.Info("client", "Resolving agent card...");
    var cardResolver = new A2ACardResolver(baseUrl);
    var card = await cardResolver.GetAgentCardAsync();
    Log.Info("client", $"Agent: {card.Name} v{card.Version}");
    Log.Info("client", $"Description: {card.Description}");
    if (card.Skills != null)
    {
        Log.Info("client", $"Skills: {card.Skills.Count}");
        foreach (var skill in card.Skills)
        {
            Log.Info("client", $"  - {skill.Name}: {skill.Description}");
        }
    }

    // Create A2A client (uses JSON-RPC internally)
    var client = new A2AClient(new Uri(card.Url));

    // Build message
    var agentMessage = new AgentMessage
    {
        Role = MessageRole.User,
        Parts = [new TextPart { Text = options.Message }],
        MessageId = Guid.NewGuid().ToString()
    };

    var sendParams = new MessageSendParams
    {
        Message = agentMessage
    };

    if (options.Stream)
    {
        await HandleStreaming(client, sendParams);
    }
    else
    {
        await HandleNonStreaming(client, sendParams);
    }
}
catch (Exception ex)
{
    Log.Error("client", $"Error: {ex.Message}");
    Environment.Exit(1);
}

static async Task HandleNonStreaming(A2AClient client, MessageSendParams sendParams)
{
    Log.Info("client", "Sending message (non-streaming)...");

    var response = await client.SendMessageAsync(sendParams);

    Console.WriteLine("=".PadRight(60, '='));
    Console.WriteLine("Agent Response:");
    Console.WriteLine("=".PadRight(60, '='));

    switch (response)
    {
        case AgentTask task:
            Console.WriteLine($"Task ID: {task.Id}");
            Console.WriteLine($"State: {task.Status.State}");
            if (task.Status.Message != null)
            {
                PrintMessageParts(task.Status.Message);
            }
            if (task.Artifacts != null)
            {
                foreach (var artifact in task.Artifacts)
                {
                    Console.WriteLine("--- Artifact ---");
                    PrintParts(artifact.Parts);
                }
            }
            break;

        case AgentMessage msg:
            PrintMessageParts(msg);
            break;

        default:
            Console.WriteLine($"Unknown response type: {response?.GetType().Name}");
            break;
    }

    Console.WriteLine("=".PadRight(60, '='));
    Console.WriteLine("Done.");
}

static async Task HandleStreaming(A2AClient client, MessageSendParams sendParams)
{
    Log.Info("client", "Sending message (streaming)...");

    Console.WriteLine("=".PadRight(60, '='));
    Console.WriteLine("Agent Response (Streaming):");
    Console.WriteLine("=".PadRight(60, '='));

    await foreach (var sseItem in client.SendMessageStreamingAsync(sendParams))
    {
        var evt = sseItem.Data;
        if (evt == null) continue;

        switch (evt)
        {
            case TaskStatusUpdateEvent statusUpdate:
                Console.Write($"[Status] State: {statusUpdate.Status.State}");
                if (statusUpdate.Status.Message != null)
                {
                    Console.Write(" | ");
                    PrintMessagePartsInline(statusUpdate.Status.Message);
                }
                Console.WriteLine();
                if (statusUpdate.Final == true)
                {
                    Console.WriteLine("[Final event]");
                }
                break;

            case TaskArtifactUpdateEvent artifactUpdate:
                Console.Write("[Artifact] ");
                PrintParts(artifactUpdate.Artifact.Parts);
                break;

            case AgentMessage msg:
                Console.Write("[Message] ");
                PrintMessageParts(msg);
                break;

            case AgentTask task:
                Console.WriteLine($"[Task] ID: {task.Id}, State: {task.Status.State}");
                break;

            default:
                Console.WriteLine($"[Event] {evt.GetType().Name}");
                break;
        }
    }

    Console.WriteLine("=".PadRight(60, '='));
    Console.WriteLine("Done.");
}

static void PrintMessageParts(AgentMessage msg)
{
    if (msg.Parts == null) return;
    PrintParts(msg.Parts);
}

static void PrintMessagePartsInline(AgentMessage msg)
{
    if (msg.Parts == null) return;
    foreach (var part in msg.Parts)
    {
        if (part is TextPart textPart)
        {
            Console.Write(textPart.Text);
        }
        else
        {
            Console.Write($"[{part.GetType().Name}]");
        }
    }
}

static void PrintParts(List<Part>? parts)
{
    if (parts == null) return;
    foreach (var part in parts)
    {
        if (part is TextPart textPart)
        {
            Console.WriteLine(textPart.Text);
        }
        else if (part is FilePart)
        {
            Console.WriteLine("[File part]");
        }
        else if (part is DataPart)
        {
            Console.WriteLine("[Data part]");
        }
        else
        {
            Console.WriteLine($"[Unknown part type: {part.GetType().Name}]");
        }
    }
}

static HostCliOptions ParseArgs(string[] args)
{
    var options = new HostCliOptions();

    for (var index = 0; index < args.Length; index++)
    {
        var arg = args[index];

        static string? NextValue(string[] values, ref int i)
        {
            if (i + 1 >= values.Length)
            {
                return null;
            }

            i++;
            return values[i];
        }

        switch (arg)
        {
            case "--transport":
            case "-t":
                options.Transport = NextValue(args, ref index) ?? options.Transport;
                break;
            case "--host":
            case "-h":
                options.Host = NextValue(args, ref index) ?? options.Host;
                break;
            case "--port":
            case "-p":
                if (int.TryParse(NextValue(args, ref index), out var parsedPort))
                    options.Port = parsedPort;
                break;
            case "--message":
            case "-m":
                options.Message = NextValue(args, ref index);
                break;
            case "--stream":
            case "-s":
                options.Stream = true;
                break;
            case "--help":
            case "-?":
                options.ShowHelp = true;
                break;
        }
    }

    return options;
}

static void PrintUsage()
{
    Console.WriteLine("Usage: client --message <text> [--transport <jsonrpc|rest>] [--host <hostname>] [--port <port>] [--stream]");
    Console.WriteLine();
    Console.WriteLine("Options:");
    Console.WriteLine("  --transport, -t  Transport protocol (jsonrpc, rest) [default: jsonrpc]");
    Console.WriteLine("  --host, -h       Agent hostname (default: localhost)");
    Console.WriteLine("  --port, -p       Agent port (default: 15001 for jsonrpc, 15002 for rest)");
    Console.WriteLine("  --message, -m    Message to send [required]");
    Console.WriteLine("  --stream, -s     Use streaming mode");
    Console.WriteLine();
    Console.WriteLine("gRPC is not supported in the current C# SDK integration.");
    Console.WriteLine("The client runtime uses the A2A SDK JSON-RPC mode.");
    Console.WriteLine();
    Console.WriteLine("Examples:");
    Console.WriteLine("  client --message \"Roll a 20-sided dice\"");
    Console.WriteLine("  client --transport rest --message \"Roll a 20-sided dice\"");
    Console.WriteLine("  client --port 15001 --message \"Is 17 prime?\" --stream");
}

sealed class HostCliOptions
{
    public string Transport { get; set; } = "jsonrpc";
    public string Host { get; set; } = "localhost";
    public int? Port { get; set; }
    public string? Message { get; set; }
    public bool Stream { get; set; }
    public bool ShowHelp { get; set; }
}

// Simple logger helper for consistent log format: TIMESTAMP - COMPONENT - LEVEL - message
// Outputs to both stderr and log file under aloha-log/
static class Log
{
    private static StreamWriter? _writer;

    public static void InitLogFile(string transport)
    {
        try
        {
            var logDir = Environment.GetEnvironmentVariable("ALOHA_LOG_DIR") ?? @"D:\coding\aloha-a2a\aloha-log";
            Directory.CreateDirectory(logDir);
            var logPath = Path.Combine(logDir, $"csharp-client-{transport}.log");
            _writer = new StreamWriter(logPath, append: true) { AutoFlush = true };
            Info("client", $"Log file: {logPath}");
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine($"WARNING: failed to open log file: {ex.Message}");
        }
    }

    private static void Write(string level, string component, string message)
    {
        var timestamp = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss,fff");
        var line = $"{timestamp} - {component} - {level} - {message}";
        Console.Error.WriteLine(line);
        _writer?.WriteLine(line);
    }

    public static void Info(string component, string message) => Write("INFO", component, message);
    public static void Warn(string component, string message) => Write("WARN", component, message);
    public static void Error(string component, string message) => Write("ERROR", component, message);
}
