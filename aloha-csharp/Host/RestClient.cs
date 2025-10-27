using System.Text;
using System.Text.Json;

namespace Aloha.A2A.Host;

/// <summary>
/// REST client for A2A protocol
/// </summary>
public class RestClient
{
    private readonly HttpClient _httpClient;
    private readonly string _baseUrl;

    public RestClient(string host, int port)
    {
        _httpClient = new HttpClient();
        _baseUrl = $"http://{host}:{port}";
    }

    /// <summary>
    /// Send a message to the agent
    /// </summary>
    public async Task<A2ATask?> SendMessageAsync(string messageText, string? contextId = null)
    {
        var message = new Message
        {
            Kind = "message",
            MessageId = Guid.NewGuid().ToString(),
            Role = "user",
            Parts = new List<MessagePart>
            {
                new MessagePart { Kind = "text", Text = messageText }
            },
            ContextId = contextId
        };

        var json = JsonSerializer.Serialize(message, new JsonSerializerOptions
        {
            PropertyNamingPolicy = JsonNamingPolicy.CamelCase
        });

        var content = new StringContent(json, Encoding.UTF8, "application/json");
        var response = await _httpClient.PostAsync($"{_baseUrl}/v1/message:send", content);

        if (!response.IsSuccessStatusCode)
        {
            throw new Exception($"Failed to send message: {response.StatusCode}");
        }

        var responseJson = await response.Content.ReadAsStringAsync();
        return JsonSerializer.Deserialize<A2ATask>(responseJson, new JsonSerializerOptions
        {
            PropertyNamingPolicy = JsonNamingPolicy.CamelCase
        });
    }

    /// <summary>
    /// Send a message and stream responses
    /// </summary>
    public async Task SendMessageStreamAsync(
        string messageText,
        Action<object> onEvent,
        string? contextId = null)
    {
        var message = new Message
        {
            Kind = "message",
            MessageId = Guid.NewGuid().ToString(),
            Role = "user",
            Parts = new List<MessagePart>
            {
                new MessagePart { Kind = "text", Text = messageText }
            },
            ContextId = contextId
        };

        var json = JsonSerializer.Serialize(message, new JsonSerializerOptions
        {
            PropertyNamingPolicy = JsonNamingPolicy.CamelCase
        });

        var content = new StringContent(json, Encoding.UTF8, "application/json");
        var request = new HttpRequestMessage(HttpMethod.Post, $"{_baseUrl}/v1/message:stream")
        {
            Content = content
        };

        var response = await _httpClient.SendAsync(request, HttpCompletionOption.ResponseHeadersRead);

        if (!response.IsSuccessStatusCode)
        {
            throw new Exception($"Failed to stream message: {response.StatusCode}");
        }

        using var stream = await response.Content.ReadAsStreamAsync();
        using var reader = new StreamReader(stream);

        while (!reader.EndOfStream)
        {
            var line = await reader.ReadLineAsync();
            if (string.IsNullOrWhiteSpace(line) || !line.StartsWith("data: "))
            {
                continue;
            }

            var eventData = line.Substring(6); // Remove "data: " prefix
            
            try
            {
                // Try to deserialize as TaskStatusUpdate first
                var statusUpdate = JsonSerializer.Deserialize<TaskStatusUpdate>(eventData, new JsonSerializerOptions
                {
                    PropertyNamingPolicy = JsonNamingPolicy.CamelCase
                });

                if (statusUpdate != null && statusUpdate.Kind == "status-update")
                {
                    onEvent(statusUpdate);
                    continue;
                }
            }
            catch { }

            try
            {
                // Try to deserialize as Task
                var task = JsonSerializer.Deserialize<A2ATask>(eventData, new JsonSerializerOptions
                {
                    PropertyNamingPolicy = JsonNamingPolicy.CamelCase
                });

                if (task != null && task.Kind == "task")
                {
                    onEvent(task);
                }
            }
            catch { }
        }
    }

    /// <summary>
    /// Get task by ID
    /// </summary>
    public async Task<A2ATask?> GetTaskAsync(string taskId)
    {
        var response = await _httpClient.GetAsync($"{_baseUrl}/v1/tasks/{taskId}");

        if (!response.IsSuccessStatusCode)
        {
            throw new Exception($"Failed to get task: {response.StatusCode}");
        }

        var responseJson = await response.Content.ReadAsStringAsync();
        return JsonSerializer.Deserialize<A2ATask>(responseJson, new JsonSerializerOptions
        {
            PropertyNamingPolicy = JsonNamingPolicy.CamelCase
        });
    }

    /// <summary>
    /// Cancel a task
    /// </summary>
    public async Task<A2ATask?> CancelTaskAsync(string taskId)
    {
        var response = await _httpClient.PostAsync($"{_baseUrl}/v1/tasks/{taskId}:cancel", null);

        if (!response.IsSuccessStatusCode)
        {
            throw new Exception($"Failed to cancel task: {response.StatusCode}");
        }

        var responseJson = await response.Content.ReadAsStringAsync();
        return JsonSerializer.Deserialize<A2ATask>(responseJson, new JsonSerializerOptions
        {
            PropertyNamingPolicy = JsonNamingPolicy.CamelCase
        });
    }

    /// <summary>
    /// Get agent card
    /// </summary>
    public async Task<AgentCard?> GetAgentCardAsync()
    {
        var response = await _httpClient.GetAsync($"{_baseUrl}/.well-known/agent-card.json");

        if (!response.IsSuccessStatusCode)
        {
            throw new Exception($"Failed to get agent card: {response.StatusCode}");
        }

        var responseJson = await response.Content.ReadAsStringAsync();
        return JsonSerializer.Deserialize<AgentCard>(responseJson, new JsonSerializerOptions
        {
            PropertyNamingPolicy = JsonNamingPolicy.CamelCase
        });
    }
}
