using Aloha.A2A.Client;

const string ExperimentalTransportsEnv = "A2A_EXPERIMENTAL_TRANSPORTS";

var options = ParseArgs(args);

if (options.ShowHelp)
{
    PrintUsage();
    return;
}

if (!options.Probe && string.IsNullOrWhiteSpace(options.Message))
{
    Console.WriteLine("--message is required unless --probe is set.");
    Environment.Exit(1);
    return;
}

var transport = options.Transport.ToLowerInvariant();
var port = options.Port ?? transport switch
{
    "grpc" => 15000,
    "jsonrpc" => 15002,
    _ => 15002
};

Console.WriteLine("=".PadRight(60, '='));
Console.WriteLine("A2A Host Client");
Console.WriteLine($"Transport: {transport}");
Console.WriteLine($"Agent: {options.Host}:{port}");
Console.WriteLine($"Message: {options.Message}");
Console.WriteLine($"Streaming: {options.Stream}");
Console.WriteLine($"Probe: {options.Probe}");
if (!string.IsNullOrEmpty(options.ContextId))
{
    Console.WriteLine($"Context ID: {options.ContextId}");
}
Console.WriteLine("=".PadRight(60, '='));
Console.WriteLine();

var experimentalTransportsEnabled =
    string.Equals(Environment.GetEnvironmentVariable(ExperimentalTransportsEnv), "1", StringComparison.Ordinal);

if (transport != "rest" && !experimentalTransportsEnabled)
{
    Console.WriteLine($"Transport '{transport}' requires experimental mode.");
    Console.WriteLine($"Set {ExperimentalTransportsEnv}=1 to enable JSON-RPC/gRPC POC, or use --transport rest.");
    Environment.Exit(1);
    return;
}

try
{
    switch (transport)
    {
        case "rest":
            if (options.Probe)
            {
                await HandleProbe(options.Host, port);
                break;
            }

            await HandleRestTransport(options.Host, port, options.Message ?? string.Empty, options.Stream, options.ContextId);
            break;

        case "jsonrpc":
            await HandleJsonRpcTransport(options.Host, port, options.Message ?? string.Empty, options.Stream, options.ContextId);
            break;

        case "grpc":
            Console.WriteLine("gRPC transport is in experimental mode but not yet implemented in this host.");
            Console.WriteLine("Use --transport rest for production usage.");
            Environment.Exit(1);
            break;

        default:
            Console.WriteLine($"Unknown transport: {transport}");
            Console.WriteLine("Supported transports: rest, grpc, jsonrpc");
            Environment.Exit(1);
            break;
    }
}
catch (Exception ex)
{
    Console.WriteLine($"Error: {ex.Message}");
    Environment.Exit(1);
}

static async Task HandleRestTransport(string host, int port, string message, bool stream, string? contextId)
{
    var client = new RestClient(host, port);

    // First, try to get agent card
    try
    {
        Console.WriteLine("Fetching agent card...");
        var agentCard = await client.GetAgentCardAsync();
        if (agentCard != null)
        {
            Console.WriteLine($"Agent: {agentCard.Name} v{agentCard.Version}");
            Console.WriteLine($"Description: {agentCard.Description}");
            Console.WriteLine();
        }
    }
    catch (Exception ex)
    {
        Console.WriteLine($"Warning: Could not fetch agent card: {ex.Message}");
        Console.WriteLine();
    }

    if (stream)
    {
        Console.WriteLine("Sending message with streaming...");
        Console.WriteLine();

        await client.SendMessageStreamAsync(message, (eventObj) =>
        {
            if (eventObj is A2ATask task)
            {
                Console.WriteLine($"[Task] ID: {task.Id}, State: {task.Status.State}");
            }
            else if (eventObj is TaskStatusUpdate statusUpdate)
            {
                Console.WriteLine($"[Status Update] State: {statusUpdate.Status.State}");
                if (statusUpdate.Status.Message?.Parts != null)
                {
                    foreach (var part in statusUpdate.Status.Message.Parts)
                    {
                        if (part.Kind == "text" && !string.IsNullOrEmpty(part.Text))
                        {
                            Console.WriteLine($"  {part.Text}");
                        }
                    }
                }

                if (statusUpdate.Final)
                {
                    Console.WriteLine();
                    Console.WriteLine("Task completed.");
                }
            }
        }, contextId);
    }
    else
    {
        Console.WriteLine("Sending message...");
        var task = await client.SendMessageAsync(message, contextId);

        if (task != null)
        {
            Console.WriteLine();
            Console.WriteLine($"Task ID: {task.Id}");
            Console.WriteLine($"Context ID: {task.ContextId}");
            Console.WriteLine($"Status: {task.Status.State}");
            Console.WriteLine();

            // Poll for completion
            var maxAttempts = 30;
            var attempt = 0;

            while (attempt < maxAttempts && task.Status.State != "completed" && task.Status.State != "failed" && task.Status.State != "canceled")
            {
                await Task.Delay(1000);
                task = await client.GetTaskAsync(task.Id);
                Console.WriteLine($"Status: {task?.Status.State}");
                attempt++;
            }

            if (task != null && task.Status.State == "completed")
            {
                Console.WriteLine();
                Console.WriteLine("Response:");
                if (task.Status.Message?.Parts != null)
                {
                    foreach (var part in task.Status.Message.Parts)
                    {
                        if (part.Kind == "text" && !string.IsNullOrEmpty(part.Text))
                        {
                            Console.WriteLine(part.Text);
                        }
                    }
                }
            }
            else if (task != null)
            {
                Console.WriteLine();
                Console.WriteLine($"Task ended with status: {task.Status.State}");
            }
        }
    }

    Console.WriteLine();
    Console.WriteLine("Done.");
}

static async Task HandleProbe(string host, int port)
{
    var client = new RestClient(host, port);
    var transportJson = await client.GetTransportCapabilitiesAsync();

    Console.WriteLine();
    Console.WriteLine("=".PadRight(60, '='));
    Console.WriteLine("Transport Capabilities");
    Console.WriteLine("=".PadRight(60, '='));
    Console.WriteLine(transportJson);
    Console.WriteLine("=".PadRight(60, '='));
}

static async Task HandleJsonRpcTransport(string host, int port, string message, bool stream, string? contextId)
{
    if (stream)
    {
        Console.WriteLine("JSON-RPC stream is not implemented yet in C# experimental transport.");
        Environment.Exit(1);
        return;
    }

    var client = new JsonRpcClient(host, port);

    Console.WriteLine("Sending message via JSON-RPC experimental transport...");
    var task = await client.SendMessageAsync(message, contextId);

    if (task == null)
    {
        Console.WriteLine("No task returned from JSON-RPC response.");
        Environment.Exit(1);
        return;
    }

    Console.WriteLine();
    Console.WriteLine($"Task ID: {task.Id}");
    Console.WriteLine($"Context ID: {task.ContextId}");
    Console.WriteLine($"Status: {task.Status.State}");
    Console.WriteLine();

    var restClient = new RestClient(host, port);
    var maxAttempts = 30;
    var attempt = 0;
    while (attempt < maxAttempts && task.Status.State != "completed" && task.Status.State != "failed" && task.Status.State != "canceled")
    {
        await Task.Delay(1000);
        var latestTask = await restClient.GetTaskAsync(task.Id);
        if (latestTask == null)
        {
            break;
        }

        task = latestTask;
        Console.WriteLine($"Status: {task.Status.State}");
        attempt++;
    }

    Console.WriteLine();

    if (task.Status.Message?.Parts != null)
    {
        Console.WriteLine("Response:");
        foreach (var part in task.Status.Message.Parts)
        {
            if (part.Kind == "text" && !string.IsNullOrWhiteSpace(part.Text))
            {
                Console.WriteLine(part.Text);
            }
        }
    }

    Console.WriteLine();
    Console.WriteLine("Done.");
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
                {
                    var rawPort = NextValue(args, ref index);
                    if (int.TryParse(rawPort, out var parsedPort))
                    {
                        options.Port = parsedPort;
                    }
                }
                break;
            case "--message":
            case "-m":
                options.Message = NextValue(args, ref index);
                break;
            case "--context":
            case "-c":
                options.ContextId = NextValue(args, ref index);
                break;
            case "--stream":
            case "-s":
                options.Stream = true;
                break;
            case "--probe":
                options.Probe = true;
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
    Console.WriteLine("Usage: host --transport <jsonrpc|grpc|rest> --host <hostname> --port <port> --message <text> [--stream] [--probe]");
    Console.WriteLine();
    Console.WriteLine("Options:");
    Console.WriteLine("  --transport, -t  Transport protocol (default: rest)");
    Console.WriteLine("  --host, -h       Agent hostname (default: localhost)");
    Console.WriteLine("  --port, -p       Agent port (default: 15002 for REST/JSON-RPC, 15000 for gRPC)");
    Console.WriteLine("  --message, -m    Message to send");
    Console.WriteLine("  --stream, -s     Use streaming mode");
    Console.WriteLine("  --probe          Probe transport capabilities and exit (REST)");
    Console.WriteLine("  --context, -c    Context ID for conversation continuity");
}

sealed class HostCliOptions
{
    public string Transport { get; set; } = "rest";
    public string Host { get; set; } = "localhost";
    public int? Port { get; set; }
    public string? Message { get; set; }
    public bool Stream { get; set; }
    public bool Probe { get; set; }
    public string? ContextId { get; set; }
    public bool ShowHelp { get; set; }
}
