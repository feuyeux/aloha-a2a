using A2A;
using A2A.AspNetCore;
using Microsoft.AspNetCore.Server.Kestrel.Core;

var builder = WebApplication.CreateBuilder(args);

var jsonrpcPort = builder.Configuration.GetValue<int>("Ports:JsonRpc", 15001);
var restPort = builder.Configuration.GetValue<int>("Ports:Rest", 15002);

// Configure Kestrel to listen on both JSON-RPC and REST ports
builder.WebHost.ConfigureKestrel(options =>
{
    options.ListenLocalhost(jsonrpcPort, listenOptions =>
    {
        listenOptions.Protocols = HttpProtocols.Http1AndHttp2;
    });
    options.ListenLocalhost(restPort, listenOptions =>
    {
        listenOptions.Protocols = HttpProtocols.Http1AndHttp2;
    });
});

// Add services
builder.Services.AddSingleton<Aloha.A2A.Server.DiceAgentExecutor>();

// Add CORS
builder.Services.AddCors(options =>
{
    options.AddDefaultPolicy(policy =>
    {
        policy.AllowAnyOrigin()
              .AllowAnyMethod()
              .AllowAnyHeader()
              .WithExposedHeaders("Content-Type");
    });
});

var app = builder.Build();

var logger = app.Services.GetRequiredService<ILogger<Program>>();
var configuration = app.Services.GetRequiredService<IConfiguration>();

// Create TaskManager from the A2A SDK
var taskManager = new TaskManager();

// Get the DiceAgentExecutor
var diceExecutor = app.Services.GetRequiredService<Aloha.A2A.Server.DiceAgentExecutor>();

// Wire up OnMessageReceived - this is where the agent logic runs
taskManager.OnMessageReceived = async (messageSendParams, ct) =>
{
    return await diceExecutor.ProcessMessageAsync(taskManager, messageSendParams, ct);
};

// Wire up OnAgentCardQuery - returns the agent card
taskManager.OnAgentCardQuery = (agentUrl, ct) =>
{
    var agentName = configuration["Agent:Name"] ?? "Dice Agent";
    var agentDescription = configuration["Agent:Description"]
        ?? "An agent that can roll arbitrary dice and check prime numbers";
    var agentVersion = configuration["Agent:Version"] ?? "1.0.0";

    var card = new AgentCard
    {
        Name = agentName,
        Description = agentDescription,
        Url = $"http://localhost:{jsonrpcPort}",
        Version = agentVersion,
        Capabilities = new AgentCapabilities
        {
            Streaming = true,
            PushNotifications = false
        },
        DefaultInputModes = ["text"],
        DefaultOutputModes = ["text"],
        Skills =
        [
            new AgentSkill
            {
                Id = "roll-dice",
                Name = "Roll Dice",
                Description = "Rolls an N-sided dice",
                Tags = ["dice", "random"],
                Examples = ["Roll a 20-sided dice", "Roll a 6-sided dice"]
            },
            new AgentSkill
            {
                Id = "check-prime",
                Name = "Prime Checker",
                Description = "Checks if numbers are prime",
                Tags = ["math", "prime"],
                Examples = ["Is 17 prime?", "Check if 2, 4, 7, 9, 11 are prime"]
            }
        ],
        AdditionalInterfaces =
        [
            new AgentInterface
            {
                Transport = AgentTransport.JsonRpc,
                Url = $"http://localhost:{jsonrpcPort}"
            },
            new AgentInterface
            {
                Transport = new AgentTransport("HTTP+JSON"),
                Url = $"http://localhost:{restPort}"
            }
        ],
        PreferredTransport = AgentTransport.JsonRpc
    };

    return Task.FromResult(card);
};

logger.LogInformation("============================================================");
logger.LogInformation("=== Dice Agent starting ===");
logger.LogInformation("============================================================");
logger.LogInformation("Dice Agent initialized with A2A SDK");
logger.LogInformation("============================================================");
logger.LogInformation("Dice Agent is running with the following transports:");
logger.LogInformation("  - JSON-RPC 2.0: http://localhost:{JsonRpcPort}", jsonrpcPort);
logger.LogInformation("  - REST:         http://localhost:{RestPort}", restPort);
logger.LogInformation("  - Agent Card:   http://localhost:{JsonRpcPort}/.well-known/agent-card.json", jsonrpcPort);
logger.LogInformation("  - SDK: A2A + A2A.AspNetCore 0.3.3-preview");
logger.LogInformation("============================================================");

app.UseCors();
app.UseRouting();

// Map A2A JSON-RPC endpoint at root (SDK handles JSON-RPC 2.0 protocol)
app.MapA2A(taskManager, "/");

// Map A2A REST endpoints (SDK experimental HTTP REST)
app.MapHttpA2A(taskManager, "/");

// Map well-known agent card endpoint
app.MapWellKnownAgentCard(taskManager, "/");

// Health check endpoint
app.MapGet("/health", () =>
{
    return Results.Ok(new { status = "healthy", timestamp = DateTime.UtcNow });
});

app.Run();
