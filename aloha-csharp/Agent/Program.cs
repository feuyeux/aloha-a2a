using Aloha.A2A.Agent;
using Microsoft.AspNetCore.Server.Kestrel.Core;

var builder = WebApplication.CreateBuilder(args);

var restPort = builder.Configuration.GetValue<int>("Ports:Rest", 15002);

// Configure Kestrel to listen on REST port
builder.WebHost.ConfigureKestrel(options =>
{
    // REST endpoint (HTTP/1.1)
    options.ListenLocalhost(restPort, listenOptions =>
    {
        listenOptions.Protocols = HttpProtocols.Http1AndHttp2;
    });
});

// Add services
builder.Services.AddControllers();

// Register agent services
builder.Services.AddSingleton<IAgentExecutor, DiceAgentExecutor>();
builder.Services.AddSingleton<DiceAgent>();
builder.Services.AddSingleton<RestTransportHandler>();

// Add CORS for REST API
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

var grpcPort = configuration.GetValue<int>("Ports:Grpc", 15000);
var jsonRpcPort = configuration.GetValue<int>("Ports:JsonRpc", 15001);

logger.LogInformation("Starting Dice Agent with multi-transport support");
logger.LogInformation("=".PadRight(60, '='));
logger.LogInformation("Dice Agent is running with the following transports:");
logger.LogInformation("  - REST:         http://localhost:{RestPort}", restPort);
logger.LogInformation("  - Agent Card:   http://localhost:{RestPort}/.well-known/agent-card.json", restPort);
logger.LogInformation("");
logger.LogInformation("Note: JSON-RPC and gRPC transports require A2A SDK support");
logger.LogInformation("  - JSON-RPC would be on port: {JsonRpcPort}", jsonRpcPort);
logger.LogInformation("  - gRPC would be on port: {GrpcPort}", grpcPort);
logger.LogInformation("=".PadRight(60, '='));

app.UseCors();
app.UseRouting();

var transportHandler = app.Services.GetRequiredService<RestTransportHandler>();

// Map REST transport endpoints
logger.LogDebug("Configuring REST transport endpoints");
app.MapPost("/v1/message:send", async (HttpContext context) =>
{
    logger.LogDebug("Received POST /v1/message:send request");
    var message = await context.Request.ReadFromJsonAsync<Message>();
    if (message != null)
    {
        return await transportHandler.HandleMessageSend(message);
    }
    else
    {
        logger.LogWarning("Received null message in send request");
        context.Response.StatusCode = 400;
        return Results.Problem("Invalid message");
    }
});
app.MapPost("/v1/message:stream", async (HttpContext context) =>
{
    logger.LogDebug("Received POST /v1/message:stream request");
    var message = await context.Request.ReadFromJsonAsync<Message>();
    if (message != null)
    {
        await transportHandler.HandleMessageStream(context, message);
    }
    else
    {
        logger.LogWarning("Received null message in stream request");
        context.Response.StatusCode = 400;
        await context.Response.WriteAsJsonAsync(new { error = "Invalid message" });
    }
});
app.MapGet("/v1/tasks/{taskId}", (string taskId) =>
{
    logger.LogDebug("Received GET /v1/tasks/{TaskId} request", taskId);
    return transportHandler.GetTask(taskId);
});
app.MapPost("/v1/tasks/{taskId}:cancel", (string taskId) =>
{
    logger.LogDebug("Received POST /v1/tasks/{TaskId}:cancel request", taskId);
    return transportHandler.CancelTask(taskId);
});

// Map agent card endpoint
app.MapGet("/.well-known/agent-card.json", (DiceAgent agent) =>
{
    logger.LogDebug("Received GET /.well-known/agent-card.json request");
    return Results.Json(agent.GetAgentCard());
});

// Health check endpoint
app.MapGet("/health", () =>
{
    logger.LogDebug("Received GET /health request");
    return Results.Ok(new { status = "healthy", timestamp = DateTime.UtcNow });
});

logger.LogInformation("Dice Agent started successfully");

app.Run();
