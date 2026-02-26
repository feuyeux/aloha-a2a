using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace Aloha.A2A.Client;

public class JsonRpcClient
{
    private readonly HttpClient _httpClient;
    private readonly string _jsonRpcUrl;

    public JsonRpcClient(string host, int port)
    {
        _httpClient = new HttpClient();
        _jsonRpcUrl = $"http://{host}:{port}/jsonrpc";
    }

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

        var request = new JsonRpcRequest
        {
            Jsonrpc = "2.0",
            Method = "message/send",
            Params = new JsonRpcParams { Message = message },
            Id = Guid.NewGuid().ToString()
        };

        var response = await _httpClient.PostAsJsonAsync(_jsonRpcUrl, request);
        if (!response.IsSuccessStatusCode)
        {
            throw new Exception($"JSON-RPC HTTP error: {response.StatusCode}");
        }

        var responseBody = await response.Content.ReadAsStringAsync();
        var rpcResponse = JsonSerializer.Deserialize<JsonRpcResponse>(responseBody, JsonOptions());
        if (rpcResponse == null)
        {
            throw new Exception("Invalid JSON-RPC response");
        }

        if (rpcResponse.Error != null)
        {
            throw new Exception($"JSON-RPC error {rpcResponse.Error.Code}: {rpcResponse.Error.Message}");
        }

        if (rpcResponse.Result == null)
        {
            throw new Exception("JSON-RPC response missing result");
        }

        return rpcResponse.Result;
    }

    private static JsonSerializerOptions JsonOptions()
    {
        return new JsonSerializerOptions
        {
            PropertyNamingPolicy = JsonNamingPolicy.CamelCase
        };
    }

    private sealed class JsonRpcRequest
    {
        [JsonPropertyName("jsonrpc")]
        public string Jsonrpc { get; set; } = "2.0";

        [JsonPropertyName("method")]
        public string Method { get; set; } = string.Empty;

        [JsonPropertyName("params")]
        public JsonRpcParams Params { get; set; } = new();

        [JsonPropertyName("id")]
        public string Id { get; set; } = string.Empty;
    }

    private sealed class JsonRpcParams
    {
        [JsonPropertyName("message")]
        public Message Message { get; set; } = new();
    }

    private sealed class JsonRpcResponse
    {
        [JsonPropertyName("jsonrpc")]
        public string Jsonrpc { get; set; } = string.Empty;

        [JsonPropertyName("result")]
        public A2ATask? Result { get; set; }

        [JsonPropertyName("error")]
        public JsonRpcError? Error { get; set; }

        [JsonPropertyName("id")]
        public JsonElement Id { get; set; }
    }

    private sealed class JsonRpcError
    {
        [JsonPropertyName("code")]
        public int Code { get; set; }

        [JsonPropertyName("message")]
        public string Message { get; set; } = string.Empty;
    }
}
