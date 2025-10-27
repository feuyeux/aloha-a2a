using System.CommandLine;
using Aloha.A2A.Host;

// Create root command
var rootCommand = new RootCommand("A2A Host Client - Communicate with A2A agents");

// Add options
var transportOption = new Option<string>(
    name: "--transport",
    description: "Transport protocol to use (rest, grpc, jsonrpc)",
    getDefaultValue: () => "rest");
transportOption.AddAlias("-t");

var hostOption = new Option<string>(
    name: "--host",
    description: "Agent hostname",
    getDefaultValue: () => "localhost");
hostOption.AddAlias("-h");

var portOption = new Option<int?>(
    name: "--port",
    description: "Agent port (default: 15000 for gRPC, 15001 for JSON-RPC, 15002 for REST)");
portOption.AddAlias("-p");

var messageOption = new Option<string>(
    name: "--message",
    description: "Message to send to the agent");
messageOption.AddAlias("-m");
messageOption.IsRequired = true;

var streamOption = new Option<bool>(
    name: "--stream",
    description: "Use streaming mode",
    getDefaultValue: () => false);
streamOption.AddAlias("-s");

var contextOption = new Option<string?>(
    name: "--context",
    description: "Context ID for conversation continuity");
contextOption.AddAlias("-c");

// Add options to root command
rootCommand.AddOption(transportOption);
rootCommand.AddOption(hostOption);
rootCommand.AddOption(portOption);
rootCommand.AddOption(messageOption);
rootCommand.AddOption(streamOption);
rootCommand.AddOption(contextOption);

// Set handler
rootCommand.SetHandler(async (transport, host, port, message, stream, contextId) =>
{
    try
    {
        // Set default port based on transport if not specified
        if (!port.HasValue)
        {
            port = transport.ToLower() switch
            {
                "grpc" => 15000,
                "jsonrpc" => 15001,
                "rest" => 15002,
                _ => 15002
            };
        }
        
        Console.WriteLine("=".PadRight(60, '='));
        Console.WriteLine($"A2A Host Client");
        Console.WriteLine($"Transport: {transport}");
        Console.WriteLine($"Agent: {host}:{port}");
        Console.WriteLine($"Message: {message}");
        Console.WriteLine($"Streaming: {stream}");
        if (!string.IsNullOrEmpty(contextId))
        {
            Console.WriteLine($"Context ID: {contextId}");
        }
        Console.WriteLine("=".PadRight(60, '='));
        Console.WriteLine();

        switch (transport.ToLower())
        {
            case "rest":
                await HandleRestTransport(host, port.Value, message, stream, contextId);
                break;

            case "grpc":
                Console.WriteLine("gRPC transport is not yet implemented.");
                Console.WriteLine("Please use REST transport with --transport rest");
                break;

            case "jsonrpc":
                Console.WriteLine("JSON-RPC transport is not yet implemented.");
                Console.WriteLine("Please use REST transport with --transport rest");
                break;

            default:
                Console.WriteLine($"Unknown transport: {transport}");
                Console.WriteLine("Supported transports: rest, grpc, jsonrpc");
                break;
        }
    }
    catch (Exception ex)
    {
        Console.WriteLine($"Error: {ex.Message}");
        Environment.Exit(1);
    }
}, transportOption, hostOption, portOption, messageOption, streamOption, contextOption);

// Execute command
return await rootCommand.InvokeAsync(args);

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
