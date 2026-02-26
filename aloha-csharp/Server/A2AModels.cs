using System.Text.Json.Serialization;

namespace Aloha.A2A.Server;

/// <summary>
/// A2A Protocol Models
/// </summary>

public class Message
{
    [JsonPropertyName("kind")]
    public string Kind { get; set; } = "message";

    [JsonPropertyName("messageId")]
    public string MessageId { get; set; } = string.Empty;

    [JsonPropertyName("role")]
    public string Role { get; set; } = string.Empty;

    [JsonPropertyName("parts")]
    public List<MessagePart> Parts { get; set; } = new();

    [JsonPropertyName("contextId")]
    public string? ContextId { get; set; }

    [JsonPropertyName("taskId")]
    public string? TaskId { get; set; }

    [JsonPropertyName("metadata")]
    public Dictionary<string, object>? Metadata { get; set; }
}

public class MessagePart
{
    [JsonPropertyName("kind")]
    public string Kind { get; set; } = string.Empty;

    [JsonPropertyName("text")]
    public string? Text { get; set; }
}

public class TaskStatus
{
    [JsonPropertyName("state")]
    public string State { get; set; } = string.Empty;

    [JsonPropertyName("message")]
    public Message? Message { get; set; }

    [JsonPropertyName("timestamp")]
    public DateTime Timestamp { get; set; }
}

public class TaskStatusUpdate
{
    [JsonPropertyName("kind")]
    public string Kind { get; set; } = "status-update";

    [JsonPropertyName("taskId")]
    public string TaskId { get; set; } = string.Empty;

    [JsonPropertyName("contextId")]
    public string ContextId { get; set; } = string.Empty;

    [JsonPropertyName("status")]
    public TaskStatus Status { get; set; } = new();

    [JsonPropertyName("final")]
    public bool Final { get; set; }
}

public class A2ATask
{
    [JsonPropertyName("kind")]
    public string Kind { get; set; } = "task";

    [JsonPropertyName("id")]
    public string Id { get; set; } = string.Empty;

    [JsonPropertyName("contextId")]
    public string ContextId { get; set; } = string.Empty;

    [JsonPropertyName("status")]
    public TaskStatus Status { get; set; } = new();

    [JsonPropertyName("history")]
    public List<Message> History { get; set; } = new();

    [JsonPropertyName("metadata")]
    public Dictionary<string, object>? Metadata { get; set; }
}

public class RequestContext
{
    public string TaskId { get; set; } = string.Empty;
    public string ContextId { get; set; } = string.Empty;
    public List<Message> Messages { get; set; } = new();
    public Dictionary<string, object>? Metadata { get; set; }
}

/// <summary>
/// Interface for event queue to emit status updates
/// </summary>
public interface IEventQueue
{
    System.Threading.Tasks.Task EnqueueAsync(TaskStatusUpdate statusUpdate);
    System.Threading.Tasks.Task EnqueueAsync(A2ATask task);
}

/// <summary>
/// Interface for agent executor
/// </summary>
public interface IAgentExecutor
{
    System.Threading.Tasks.Task ExecuteAsync(RequestContext context, IEventQueue eventQueue);
    System.Threading.Tasks.Task CancelAsync(RequestContext context, IEventQueue eventQueue);
}

/// <summary>
/// In-memory event queue implementation
/// </summary>
public class InMemoryEventQueue : IEventQueue
{
    private readonly List<object> _events = new();
    private readonly object _lock = new();

    public System.Threading.Tasks.Task EnqueueAsync(TaskStatusUpdate statusUpdate)
    {
        lock (_lock)
        {
            _events.Add(statusUpdate);
        }
        return System.Threading.Tasks.Task.CompletedTask;
    }

    public System.Threading.Tasks.Task EnqueueAsync(A2ATask task)
    {
        lock (_lock)
        {
            _events.Add(task);
        }
        return System.Threading.Tasks.Task.CompletedTask;
    }

    public List<object> GetEvents()
    {
        lock (_lock)
        {
            return new List<object>(_events);
        }
    }

    public void Clear()
    {
        lock (_lock)
        {
            _events.Clear();
        }
    }
}
