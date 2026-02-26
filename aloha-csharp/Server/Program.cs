using Aloha.A2A.Server;
using Microsoft.AspNetCore.Server.Kestrel.Core;
using System.Text.Json.Serialization;

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
builder.Services.AddSingleton<AlohaServer>();
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
var experimentalTransportsEnabled =
    string.Equals(Environment.GetEnvironmentVariable("A2A_EXPERIMENTAL_TRANSPORTS"), "1", StringComparison.Ordinal);

logger.LogInformation("Starting Dice Agent with REST transport support");
logger.LogInformation("=".PadRight(60, '='));
logger.LogInformation("Dice Agent is running with the following transport:");
logger.LogInformation("  - REST:         http://localhost:{RestPort}", restPort);
logger.LogInformation("  - Agent Card:   http://localhost:{RestPort}/.well-known/agent-card.json", restPort);
if (experimentalTransportsEnabled)
{
    logger.LogInformation("  - Experimental transport mode enabled (SDK transport POC wiring pending)");
}
else
{
    logger.LogInformation("  - Experimental transports disabled (set A2A_EXPERIMENTAL_TRANSPORTS=1 for POC mode)");
}
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
app.MapGet("/.well-known/agent-card.json", (AlohaServer agent) =>
{
    logger.LogDebug("Received GET /.well-known/agent-card.json request");
    return Results.Json(agent.GetAgentCard());
});

app.MapGet("/v1/transports", () =>
{
    logger.LogDebug("Received GET /v1/transports request");
    return Results.Json(new
    {
        rest = new
        {
            implemented = true,
            stream = true
        },
        jsonrpc = new
        {
            enabled = experimentalTransportsEnabled,
            implemented = experimentalTransportsEnabled,
            stream = false
        },
        grpc = new
        {
            enabled = experimentalTransportsEnabled,
            implemented = false,
            stream = false
        },
        experimentalTransports = experimentalTransportsEnabled
    });
});

if (experimentalTransportsEnabled)
{
    app.MapPost("/jsonrpc", async (HttpContext context) =>
    {
        logger.LogDebug("Received POST /jsonrpc request");

        JsonRpcRequest? request;
        try
        {
            request = await context.Request.ReadFromJsonAsync<JsonRpcRequest>();
        }
        catch
        {
            return Results.Json(new JsonRpcErrorResponse
            {
                Jsonrpc = "2.0",
                Error = new JsonRpcError { Code = -32700, Message = "Parse error" },
                Id = null
            });
        }

        if (request == null || request.Jsonrpc != "2.0" || string.IsNullOrWhiteSpace(request.Method))
        {
            return Results.Json(new JsonRpcErrorResponse
            {
                Jsonrpc = "2.0",
                Error = new JsonRpcError { Code = -32600, Message = "Invalid Request" },
                Id = request?.Id
            });
        }

        if (request.Method == "message/stream")
        {
            return Results.Json(new JsonRpcErrorResponse
            {
                Jsonrpc = "2.0",
                Error = new JsonRpcError { Code = -32001, Message = "message/stream is not implemented on C# JSON-RPC transport" },
                Id = request.Id
            });
        }

        if (request.Method != "message/send")
        {
            return Results.Json(new JsonRpcErrorResponse
            {
                Jsonrpc = "2.0",
                Error = new JsonRpcError { Code = -32601, Message = "Method not found" },
                Id = request.Id
            });
        }

        var message = request.Params?.Message;
        if (message == null)
        {
            return Results.Json(new JsonRpcErrorResponse
            {
                Jsonrpc = "2.0",
                Error = new JsonRpcError { Code = -32602, Message = "Invalid params" },
                Id = request.Id
            });
        }

        try
        {
            var task = await transportHandler.ExecuteMessageSendAsync(message);
            return Results.Json(new JsonRpcSuccessResponse
            {
                Jsonrpc = "2.0",
                Result = task,
                Id = request.Id
            });
        }
        catch (Exception ex)
        {
            logger.LogError(ex, "JSON-RPC message/send execution failed");
            return Results.Json(new JsonRpcErrorResponse
            {
                Jsonrpc = "2.0",
                Error = new JsonRpcError { Code = -32603, Message = "Internal error" },
                Id = request.Id
            });
        }
    });
}

// Health check endpoint
app.MapGet("/health", () =>
{
    logger.LogDebug("Received GET /health request");
    return Results.Ok(new { status = "healthy", timestamp = DateTime.UtcNow });
});

logger.LogInformation("Dice Agent started successfully");

app.Run();

internal sealed class JsonRpcRequest
{
    [JsonPropertyName("jsonrpc")]
    public string Jsonrpc { get; set; } = string.Empty;

    [JsonPropertyName("method")]
    public string Method { get; set; } = string.Empty;

    [JsonPropertyName("params")]
    public JsonRpcParams? Params { get; set; }

    [JsonPropertyName("id")]
    public object? Id { get; set; }
}

internal sealed class JsonRpcParams
{
    [JsonPropertyName("message")]
    public Message? Message { get; set; }
}

internal sealed class JsonRpcSuccessResponse
{
    [JsonPropertyName("jsonrpc")]
    public string Jsonrpc { get; set; } = "2.0";

    [JsonPropertyName("result")]
    public object? Result { get; set; }

    [JsonPropertyName("id")]
    public object? Id { get; set; }
}

internal sealed class JsonRpcErrorResponse
{
    [JsonPropertyName("jsonrpc")]
    public string Jsonrpc { get; set; } = "2.0";

    [JsonPropertyName("error")]
    public JsonRpcError Error { get; set; } = new();

    [JsonPropertyName("id")]
    public object? Id { get; set; }
}

internal sealed class JsonRpcError
{
    [JsonPropertyName("code")]
    public int Code { get; set; }

    [JsonPropertyName("message")]
    public string Message { get; set; } = string.Empty;
}
