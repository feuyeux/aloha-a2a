using A2A;

var options = ParseArgs(args);

if (options.ShowHelp)
{
    PrintUsage();
    return;
}

if (string.IsNullOrWhiteSpace(options.Message))
{
    Console.WriteLine("--message is required.");
    PrintUsage();
    Environment.Exit(1);
    return;
}

var port = options.Port ?? 15001; // Default to JSON-RPC port

Console.WriteLine("=".PadRight(60, '='));
Console.WriteLine("A2A Host Client (SDK)");
Console.WriteLine($"  Transport: JSON-RPC (via A2A SDK)");
Console.WriteLine($"  Agent: {options.Host}:{port}");
Console.WriteLine($"  Message: {options.Message}");
Console.WriteLine($"  Streaming: {options.Stream}");
Console.WriteLine("=".PadRight(60, '='));
Console.WriteLine();

try
{
    var baseUrl = new Uri($"http://{options.Host}:{port}");

    // Resolve agent card
    Console.WriteLine("Resolving agent card...");
    var cardResolver = new A2ACardResolver(baseUrl);
    var card = await cardResolver.GetAgentCardAsync();
    Console.WriteLine($"Agent: {card.Name} v{card.Version}");
    Console.WriteLine($"Description: {card.Description}");
    if (card.Skills != null)
    {
        Console.WriteLine($"Skills: {card.Skills.Count}");
        foreach (var skill in card.Skills)
        {
            Console.WriteLine($"  - {skill.Name}: {skill.Description}");
        }
    }
    Console.WriteLine();

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
    Console.WriteLine($"Error: {ex.Message}");
    Environment.Exit(1);
}

static async Task HandleNonStreaming(A2AClient client, MessageSendParams sendParams)
{
    Console.WriteLine("Sending message (non-streaming)...");
    Console.WriteLine();

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
    Console.WriteLine("Sending message (streaming)...");
    Console.WriteLine();

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
    Console.WriteLine("Usage: client --message <text> [--host <hostname>] [--port <port>] [--stream]");
    Console.WriteLine();
    Console.WriteLine("Options:");
    Console.WriteLine("  --host, -h       Agent hostname (default: localhost)");
    Console.WriteLine("  --port, -p       Agent port (default: 15001 for JSON-RPC)");
    Console.WriteLine("  --message, -m    Message to send [required]");
    Console.WriteLine("  --stream, -s     Use streaming mode");
    Console.WriteLine();
    Console.WriteLine("The client uses the A2A SDK with JSON-RPC transport.");
    Console.WriteLine();
    Console.WriteLine("Examples:");
    Console.WriteLine("  client --message \"Roll a 20-sided dice\"");
    Console.WriteLine("  client --port 15001 --message \"Is 17 prime?\" --stream");
}

sealed class HostCliOptions
{
    public string Host { get; set; } = "localhost";
    public int? Port { get; set; }
    public string? Message { get; set; }
    public bool Stream { get; set; }
    public bool ShowHelp { get; set; }
}
