using System.Text.Json.Serialization;

namespace Aloha.A2A.Client;

/// <summary>
/// A2A Protocol Models for Host
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

public class AgentCard
{
    [JsonPropertyName("name")]
    public string Name { get; set; } = string.Empty;

    [JsonPropertyName("description")]
    public string Description { get; set; } = string.Empty;

    [JsonPropertyName("url")]
    public string Url { get; set; } = string.Empty;

    [JsonPropertyName("version")]
    public string Version { get; set; } = string.Empty;

    [JsonPropertyName("capabilities")]
    public AgentCapabilities? Capabilities { get; set; }

    [JsonPropertyName("skills")]
    public List<Skill>? Skills { get; set; }
}

public class AgentCapabilities
{
    [JsonPropertyName("streaming")]
    public bool Streaming { get; set; }

    [JsonPropertyName("pushNotifications")]
    public bool PushNotifications { get; set; }
}

public class Skill
{
    [JsonPropertyName("id")]
    public string Id { get; set; } = string.Empty;

    [JsonPropertyName("name")]
    public string Name { get; set; } = string.Empty;

    [JsonPropertyName("description")]
    public string Description { get; set; } = string.Empty;

    [JsonPropertyName("tags")]
    public List<string> Tags { get; set; } = new();

    [JsonPropertyName("examples")]
    public List<string> Examples { get; set; } = new();
}
