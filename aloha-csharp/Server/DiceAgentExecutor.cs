using A2A;
using Microsoft.SemanticKernel;
using Microsoft.SemanticKernel.ChatCompletion;
using Microsoft.SemanticKernel.Connectors.OpenAI;

namespace Aloha.A2A.Server;

/// <summary>
/// Agent executor that processes requests using Semantic Kernel and LLM,
/// integrated with the A2A SDK TaskManager.
/// </summary>
public class DiceAgentExecutor
{
    private readonly ILogger<DiceAgentExecutor> _logger;
    private readonly Kernel _kernel;
    private readonly IChatCompletionService _chatService;

    public DiceAgentExecutor(
        ILogger<DiceAgentExecutor> logger,
        IConfiguration configuration)
    {
        _logger = logger;

        // Build Semantic Kernel with tools
        var builder = Kernel.CreateBuilder();

        // Read Ollama configuration from environment variables with fallback to config
        var ollamaBaseUrl = Environment.GetEnvironmentVariable("OLLAMA_BASE_URL")
            ?? configuration["Ollama:BaseUrl"]
            ?? "http://localhost:11434";

        var ollamaModel = Environment.GetEnvironmentVariable("OLLAMA_MODEL")
            ?? configuration["Ollama:Model"]
            ?? "qwen2.5";

        // Ollama uses OpenAI-compatible API at /v1 endpoint
        var ollamaEndpoint = $"{ollamaBaseUrl.TrimEnd('/')}/v1";

        _logger.LogInformation("Configuring Ollama integration: endpoint={Endpoint}, model={Model}",
            ollamaEndpoint, ollamaModel);

        try
        {
            // Add OpenAI chat completion service configured for Ollama
#pragma warning disable SKEXP0010
            builder.AddOpenAIChatCompletion(
                modelId: ollamaModel,
                apiKey: "ollama",
                endpoint: new Uri(ollamaEndpoint)
            );
#pragma warning restore SKEXP0010

            // Add tools plugin
            builder.Plugins.AddFromObject(new Tools(), "DiceTools");

            _kernel = builder.Build();
            _chatService = _kernel.GetRequiredService<IChatCompletionService>();

            _logger.LogInformation("DiceAgentExecutor initialized successfully with Ollama model {Model}", ollamaModel);

            // Validate Ollama connection on startup
            ValidateOllamaConnection(ollamaBaseUrl, ollamaModel);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to initialize DiceAgentExecutor with Ollama");
            throw new InvalidOperationException(
                $"Failed to connect to Ollama at {ollamaBaseUrl}. " +
                $"Please ensure Ollama is installed, the '{ollamaModel}' model is pulled, and the service is running.",
                ex);
        }
    }

    /// <summary>
    /// Process an incoming message using the A2A SDK TaskManager pattern.
    /// Returns an A2AResponse (either AgentMessage for stateless or AgentTask for task-based).
    /// </summary>
    public async Task<A2AResponse> ProcessMessageAsync(
        TaskManager taskManager,
        MessageSendParams messageSendParams,
        CancellationToken ct)
    {
        var message = messageSendParams.Message;
        var userText = ExtractTextFromMessage(message);

        _logger.LogInformation("Processing message: {Text}", userText);

        if (string.IsNullOrWhiteSpace(userText))
        {
            return CreateAgentMessage("Error: Empty message received. Please provide a message.");
        }

        // Create a task for tracking
        var agentTask = await taskManager.CreateTaskAsync(
            message.ContextId,
            message.TaskId,
            ct);

        var taskId = agentTask.Id;

        try
        {
            // Update status to working
            await taskManager.UpdateStatusAsync(
                taskId,
                TaskState.Working,
                CreateAgentMessage("Processing your request..."),
                final: false,
                ct);

            // Process with LLM
            var responseText = await InvokeLlmAsync(userText, ct);

            // Return artifact with the response
            await taskManager.ReturnArtifactAsync(
                taskId,
                new Artifact
                {
                    ArtifactId = Guid.NewGuid().ToString(),
                    Name = "response",
                    Parts = [new TextPart { Text = responseText }]
                },
                ct);

            // Update status to completed
            await taskManager.UpdateStatusAsync(
                taskId,
                TaskState.Completed,
                CreateAgentMessage(responseText),
                final: true,
                ct);

            // Return the completed task
            var completedTask = await taskManager.GetTaskAsync(
                new TaskQueryParams { Id = taskId },
                ct);

            return completedTask ?? agentTask;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error processing message for task {TaskId}", taskId);

            await taskManager.UpdateStatusAsync(
                taskId,
                TaskState.Failed,
                CreateAgentMessage($"Error: {ex.Message}"),
                final: true,
                ct);

            var failedTask = await taskManager.GetTaskAsync(
                new TaskQueryParams { Id = taskId },
                ct);

            return failedTask ?? agentTask;
        }
    }

    /// <summary>
    /// Invoke the LLM with tools to process the user message
    /// </summary>
    private async Task<string> InvokeLlmAsync(string userMessage, CancellationToken ct)
    {
        var chatHistory = new ChatHistory();
        chatHistory.AddSystemMessage(
            "You are a helpful assistant with access to dice rolling and prime number checking tools. " +
            "When users ask to roll dice or check prime numbers, use the appropriate tools. " +
            "Always provide clear, friendly responses.");
        chatHistory.AddUserMessage(userMessage);

        var executionSettings = new OpenAIPromptExecutionSettings
        {
            ToolCallBehavior = ToolCallBehavior.AutoInvokeKernelFunctions,
            Temperature = 0.7,
            MaxTokens = 500
        };

        _logger.LogInformation("Invoking LLM with tools");
        var response = await _chatService.GetChatMessageContentAsync(
            chatHistory,
            executionSettings,
            _kernel,
            ct);

        var responseText = response.Content ?? "I processed your request but have no response.";
        _logger.LogInformation("LLM returned response length={Length}", responseText.Length);

        return responseText;
    }

    /// <summary>
    /// Extract text content from an A2A SDK AgentMessage
    /// </summary>
    private static string ExtractTextFromMessage(AgentMessage message)
    {
        if (message.Parts == null || message.Parts.Count == 0)
            return string.Empty;

        var textParts = message.Parts
            .OfType<TextPart>()
            .Select(p => p.Text)
            .Where(t => !string.IsNullOrWhiteSpace(t));

        return string.Join(" ", textParts);
    }

    /// <summary>
    /// Create an AgentMessage with text content
    /// </summary>
    private static AgentMessage CreateAgentMessage(string text)
    {
        return new AgentMessage
        {
            Role = MessageRole.Agent,
            Parts = [new TextPart { Text = text }],
            MessageId = Guid.NewGuid().ToString()
        };
    }

    /// <summary>
    /// Validates that Ollama is accessible and the model is available
    /// </summary>
    private void ValidateOllamaConnection(string baseUrl, string modelName)
    {
        try
        {
            using var httpClient = new HttpClient { Timeout = TimeSpan.FromSeconds(5) };
            var response = httpClient.GetAsync($"{baseUrl.TrimEnd('/')}/api/tags").Result;

            if (response.IsSuccessStatusCode)
            {
                _logger.LogInformation("Successfully connected to Ollama at {BaseUrl}", baseUrl);

                var content = response.Content.ReadAsStringAsync().Result;
                if (!content.Contains(modelName))
                {
                    _logger.LogWarning(
                        "Model '{Model}' may not be available in Ollama. " +
                        "Please run: ollama pull {Model}",
                        modelName, modelName);
                }
            }
            else
            {
                _logger.LogWarning(
                    "Ollama responded with status {StatusCode}. The service may not be fully ready.",
                    response.StatusCode);
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex,
                "Failed to validate Ollama connection at {BaseUrl}. " +
                "Please ensure Ollama is running: ollama serve",
                baseUrl);
            throw new InvalidOperationException(
                $"Cannot connect to Ollama at {baseUrl}. " +
                "Please ensure Ollama is installed and running.",
                ex);
        }
    }
}
