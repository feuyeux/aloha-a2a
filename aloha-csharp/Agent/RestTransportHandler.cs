using Microsoft.AspNetCore.Mvc;
using System.Text.Json;

namespace Aloha.A2A.Agent;

/// <summary>
/// REST transport handler for A2A protocol
/// </summary>
public class RestTransportHandler
{
    private readonly IAgentExecutor _executor;
    private readonly ILogger<RestTransportHandler> _logger;
    private readonly Dictionary<string, A2ATask> _a2aTasks = new();
    private readonly Dictionary<string, List<Message>> _contexts = new();

    public RestTransportHandler(
        IAgentExecutor executor,
        ILogger<RestTransportHandler> logger)
    {
        _executor = executor;
        _logger = logger;
    }

    /// <summary>
    /// Handle message send request
    /// </summary>
    public async System.Threading.Tasks.Task<IResult> HandleMessageSend(Message message)
    {
        try
        {
            _logger.LogInformation("Received message: {MessageId}", message.MessageId);

            var taskId = message.TaskId ?? Guid.NewGuid().ToString();
            var contextId = message.ContextId ?? Guid.NewGuid().ToString();

            // Create request context
            var requestContext = new RequestContext
            {
                TaskId = taskId,
                ContextId = contextId,
                Messages = new List<Message> { message },
                Metadata = message.Metadata
            };

            // Create event queue
            var eventQueue = new InMemoryEventQueue();

            // Create initial task
            var a2aTask = new A2ATask
            {
                Kind = "task",
                Id = taskId,
                ContextId = contextId,
                Status = new TaskStatus
                {
                    State = "submitted",
                    Timestamp = DateTime.UtcNow
                },
                History = new List<Message> { message }
            };

            _a2aTasks[taskId] = a2aTask;

            // Update context history
            if (!_contexts.ContainsKey(contextId))
            {
                _contexts[contextId] = new List<Message>();
            }
            _contexts[contextId].Add(message);

            // Execute asynchronously
            _ = System.Threading.Tasks.Task.Run(async () =>
            {
                await _executor.ExecuteAsync(requestContext, eventQueue);

                // Update task with final status
                var events = ((InMemoryEventQueue)eventQueue).GetEvents();
                var finalUpdate = events.OfType<TaskStatusUpdate>().LastOrDefault(e => e.Final);
                if (finalUpdate != null)
                {
                    a2aTask.Status = finalUpdate.Status;
                    if (finalUpdate.Status.Message != null)
                    {
                        a2aTask.History.Add(finalUpdate.Status.Message);
                        _contexts[contextId].Add(finalUpdate.Status.Message);
                    }
                }
            });

            return Results.Json(a2aTask);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error handling message send");
            return Results.Problem(ex.Message, statusCode: 500);
        }
    }

    /// <summary>
    /// Handle message stream request
    /// </summary>
    public async System.Threading.Tasks.Task HandleMessageStream(HttpContext context, Message message)
    {
        try
        {
            _logger.LogInformation("Received streaming message: {MessageId}", message.MessageId);

            var taskId = message.TaskId ?? Guid.NewGuid().ToString();
            var contextId = message.ContextId ?? Guid.NewGuid().ToString();

            // Set up SSE headers
            context.Response.Headers.Append("Content-Type", "text/event-stream");
            context.Response.Headers.Append("Cache-Control", "no-cache");
            context.Response.Headers.Append("Connection", "keep-alive");

            // Create request context
            var requestContext = new RequestContext
            {
                TaskId = taskId,
                ContextId = contextId,
                Messages = new List<Message> { message },
                Metadata = message.Metadata
            };

            // Create event queue
            var eventQueue = new StreamingEventQueue(context.Response);

            // Create initial task
            var a2aTask = new A2ATask
            {
                Kind = "task",
                Id = taskId,
                ContextId = contextId,
                Status = new TaskStatus
                {
                    State = "submitted",
                    Timestamp = DateTime.UtcNow
                },
                History = new List<Message> { message }
            };

            _a2aTasks[taskId] = a2aTask;

            // Send initial task event
            await eventQueue.EnqueueAsync(a2aTask);

            // Update context history
            if (!_contexts.ContainsKey(contextId))
            {
                _contexts[contextId] = new List<Message>();
            }
            _contexts[contextId].Add(message);

            // Execute and stream events
            await _executor.ExecuteAsync(requestContext, eventQueue);

            await context.Response.CompleteAsync();
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error handling message stream");
        }
    }

    /// <summary>
    /// Get task by ID
    /// </summary>
    public IResult GetTask(string taskId)
    {
        if (_a2aTasks.TryGetValue(taskId, out var a2aTask))
        {
            return Results.Json(a2aTask);
        }

        return Results.NotFound(new { error = "Task not found" });
    }

    /// <summary>
    /// Cancel task
    /// </summary>
    public async System.Threading.Tasks.Task<IResult> CancelTask(string taskId)
    {
        if (!_a2aTasks.TryGetValue(taskId, out var a2aTask))
        {
            return Results.NotFound(new { error = "Task not found" });
        }

        var requestContext = new RequestContext
        {
            TaskId = taskId,
            ContextId = a2aTask.ContextId
        };

        var eventQueue = new InMemoryEventQueue();
        await _executor.CancelAsync(requestContext, eventQueue);

        a2aTask.Status.State = "canceled";
        a2aTask.Status.Timestamp = DateTime.UtcNow;

        return Results.Json(a2aTask);
    }
}

/// <summary>
/// Event queue that streams events via Server-Sent Events
/// </summary>
public class StreamingEventQueue : IEventQueue
{
    private readonly HttpResponse _response;

    public StreamingEventQueue(HttpResponse response)
    {
        _response = response;
    }

    public async System.Threading.Tasks.Task EnqueueAsync(TaskStatusUpdate statusUpdate)
    {
        var json = JsonSerializer.Serialize(statusUpdate, new JsonSerializerOptions
        {
            PropertyNamingPolicy = JsonNamingPolicy.CamelCase
        });

        await _response.WriteAsync($"data: {json}\n\n");
        await _response.Body.FlushAsync();
    }

    public async System.Threading.Tasks.Task EnqueueAsync(A2ATask task)
    {
        var json = JsonSerializer.Serialize(task, new JsonSerializerOptions
        {
            PropertyNamingPolicy = JsonNamingPolicy.CamelCase
        });

        await _response.WriteAsync($"data: {json}\n\n");
        await _response.Body.FlushAsync();
    }
}
