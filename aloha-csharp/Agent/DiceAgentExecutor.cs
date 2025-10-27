using Microsoft.SemanticKernel;
using Microsoft.SemanticKernel.ChatCompletion;
using Microsoft.SemanticKernel.Connectors.OpenAI;

namespace Aloha.A2A.Agent;

/// <summary>
/// A2A Protocol Error Codes
/// </summary>
public static class A2AErrorCode
{
    public const string InvalidRequest = "invalid_request";
    public const string UnsupportedOperation = "unsupported_operation";
    public const string TaskNotFound = "task_not_found";
    public const string InternalError = "internal_error";
    public const string Timeout = "timeout";
    public const string Canceled = "canceled";
    public const string ValidationError = "validation_error";
}

/// <summary>
/// Agent executor that processes requests using Semantic Kernel and LLM
/// </summary>
public class DiceAgentExecutor : IAgentExecutor
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
            // Ollama doesn't require a real API key, but Semantic Kernel requires one
#pragma warning disable SKEXP0010 // Suppress experimental API warning
            builder.AddOpenAIChatCompletion(
                modelId: ollamaModel,
                apiKey: "ollama", // Placeholder - Ollama doesn't validate this
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
            _logger.LogError(
                "Please ensure Ollama is installed and running:\n" +
                "1. Install Ollama: https://ollama.ai/download\n" +
                "2. Pull the model: ollama pull {Model}\n" +
                "3. Start Ollama service: ollama serve\n" +
                "4. Verify Ollama is running: curl {BaseUrl}/api/tags",
                ollamaModel, ollamaBaseUrl);
            throw new InvalidOperationException(
                $"Failed to connect to Ollama at {ollamaBaseUrl}. " +
                $"Please ensure Ollama is installed, the '{ollamaModel}' model is pulled, and the service is running.", 
                ex);
        }
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
                
                // Check if the model is available
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
                "Please ensure Ollama is installed and running. " +
                "Visit https://ollama.ai/download for installation instructions.", 
                ex);
        }
    }

    public async System.Threading.Tasks.Task ExecuteAsync(RequestContext context, IEventQueue eventQueue)
    {
        var taskId = context.TaskId ?? "<none>";
        
        try
        {
            _logger.LogInformation("Received new request. taskId={TaskId}", taskId);

            // Validate incoming request
            try
            {
                ValidateRequest(context);
                _logger.LogDebug("Request validation passed");
            }
            catch (ArgumentException ex)
            {
                _logger.LogError(ex, "Request validation failed: {Message}", ex.Message);
                await EmitErrorAsync(eventQueue, context, $"Validation error: {ex.Message}");
                return;
            }

            // Extract user message
            var userMessage = ExtractUserMessage(context);
            if (string.IsNullOrWhiteSpace(userMessage))
            {
                _logger.LogWarning("Empty message text received");
                await EmitErrorAsync(eventQueue, context, "Error: Empty message received. Please provide a message.");
                return;
            }

            _logger.LogDebug("Extracted message text: {Message}", userMessage);

            // Emit working status
            await EmitWorkingStatusAsync(eventQueue, context, "Processing your request...");
            _logger.LogInformation("Task started working: {TaskId}", taskId);

            // Create chat history
            var chatHistory = new ChatHistory();
            chatHistory.AddSystemMessage(
                "You are a helpful assistant with access to dice rolling and prime number checking tools. " +
                "When users ask to roll dice or check prime numbers, use the appropriate tools. " +
                "Always provide clear, friendly responses.");
            chatHistory.AddUserMessage(userMessage);

            // Configure execution settings for tool calling
            var executionSettings = new OpenAIPromptExecutionSettings
            {
                ToolCallBehavior = ToolCallBehavior.AutoInvokeKernelFunctions,
                Temperature = 0.7,
                MaxTokens = 500
            };

            // Get response from LLM with automatic tool invocation
            _logger.LogInformation("Invoking LLM with tools");
            ChatMessageContent response;
            try
            {
                response = await _chatService.GetChatMessageContentAsync(
                    chatHistory,
                    executionSettings,
                    _kernel);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "LLM processing error: {Message}", ex.Message);
                await EmitErrorAsync(eventQueue, context, $"Error processing your request: {ex.Message}");
                return;
            }

            var responseText = response.Content ?? "I processed your request but have no response.";

            _logger.LogInformation("LLM returned response length={Length}", responseText.Length);
            _logger.LogDebug("LLM response content: {Response}", responseText);

            // Emit completed status with response
            await EmitCompletedStatusAsync(eventQueue, context, responseText);

            _logger.LogInformation("Task completed successfully: {TaskId}", taskId);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Unexpected error during agent execution for task {TaskId}: {Message}", 
                taskId, ex.Message);
            try
            {
                await EmitErrorAsync(eventQueue, context, $"Internal server error: {ex.Message}");
                _logger.LogInformation("Marked task as failed after unexpected error: {TaskId}", taskId);
            }
            catch (Exception inner)
            {
                _logger.LogWarning(inner, "Failed to update task after error: {Message}", inner.Message);
            }
        }
    }

    public async System.Threading.Tasks.Task CancelAsync(RequestContext context, IEventQueue eventQueue)
    {
        var taskId = context.TaskId ?? "<none>";
        _logger.LogInformation("Cancel requested for task: {TaskId}", taskId);
        
        // Validate that task can be canceled
        // Note: In a real implementation, you would check task state here
        // For now, we'll just emit the canceled status
        
        try
        {
            await EmitCanceledStatusAsync(eventQueue, context, "Task was canceled");
            _logger.LogInformation("Task cancelled successfully: {TaskId}", taskId);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error canceling task {TaskId}: {Message}", taskId, ex.Message);
            throw;
        }
    }

    /// <summary>
    /// Validates the incoming request
    /// </summary>
    private void ValidateRequest(RequestContext context)
    {
        if (context == null)
        {
            throw new ArgumentNullException(nameof(context), "Request context cannot be null");
        }

        var message = context.Messages?.FirstOrDefault();
        if (message == null)
        {
            throw new ArgumentException("Invalid message: message is null", nameof(context));
        }

        if (message.Parts == null || message.Parts.Count == 0)
        {
            throw new ArgumentException("Invalid message: no message parts provided", nameof(context));
        }

        // Check for at least one text part
        var hasText = message.Parts.Any(p => p.Kind == "text");
        if (!hasText)
        {
            throw new ArgumentException("Invalid message: no text content found in message parts", nameof(context));
        }
    }

    private string ExtractUserMessage(RequestContext context)
    {
        // Extract text from the first message part
        var message = context.Messages?.FirstOrDefault();
        if (message?.Parts == null || message.Parts.Count == 0)
        {
            return string.Empty;
        }

        var textPart = message.Parts.FirstOrDefault(p => p.Kind == "text");
        return textPart?.Text ?? string.Empty;
    }

    private async System.Threading.Tasks.Task EmitWorkingStatusAsync(IEventQueue eventQueue, RequestContext context, string message)
    {
        var statusUpdate = new TaskStatusUpdate
        {
            Kind = "status-update",
            TaskId = context.TaskId,
            ContextId = context.ContextId,
            Status = new TaskStatus
            {
                State = "working",
                Message = new Message
                {
                    Kind = "message",
                    MessageId = Guid.NewGuid().ToString(),
                    Role = "agent",
                    Parts = new List<MessagePart>
                    {
                        new MessagePart { Kind = "text", Text = message }
                    }
                },
                Timestamp = DateTime.UtcNow
            },
            Final = false
        };

        await eventQueue.EnqueueAsync(statusUpdate);
    }

    private async System.Threading.Tasks.Task EmitCompletedStatusAsync(IEventQueue eventQueue, RequestContext context, string message)
    {
        var statusUpdate = new TaskStatusUpdate
        {
            Kind = "status-update",
            TaskId = context.TaskId,
            ContextId = context.ContextId,
            Status = new TaskStatus
            {
                State = "completed",
                Message = new Message
                {
                    Kind = "message",
                    MessageId = Guid.NewGuid().ToString(),
                    Role = "agent",
                    Parts = new List<MessagePart>
                    {
                        new MessagePart { Kind = "text", Text = message }
                    }
                },
                Timestamp = DateTime.UtcNow
            },
            Final = true
        };

        await eventQueue.EnqueueAsync(statusUpdate);
    }

    private async System.Threading.Tasks.Task EmitCanceledStatusAsync(IEventQueue eventQueue, RequestContext context, string message)
    {
        var statusUpdate = new TaskStatusUpdate
        {
            Kind = "status-update",
            TaskId = context.TaskId,
            ContextId = context.ContextId,
            Status = new TaskStatus
            {
                State = "canceled",
                Message = new Message
                {
                    Kind = "message",
                    MessageId = Guid.NewGuid().ToString(),
                    Role = "agent",
                    Parts = new List<MessagePart>
                    {
                        new MessagePart { Kind = "text", Text = message }
                    }
                },
                Timestamp = DateTime.UtcNow
            },
            Final = true
        };

        await eventQueue.EnqueueAsync(statusUpdate);
    }

    private async System.Threading.Tasks.Task EmitErrorAsync(IEventQueue eventQueue, RequestContext context, string errorMessage)
    {
        var statusUpdate = new TaskStatusUpdate
        {
            Kind = "status-update",
            TaskId = context.TaskId,
            ContextId = context.ContextId,
            Status = new TaskStatus
            {
                State = "failed",
                Message = new Message
                {
                    Kind = "message",
                    MessageId = Guid.NewGuid().ToString(),
                    Role = "agent",
                    Parts = new List<MessagePart>
                    {
                        new MessagePart { Kind = "text", Text = $"Error: {errorMessage}" }
                    }
                },
                Timestamp = DateTime.UtcNow
            },
            Final = true
        };

        await eventQueue.EnqueueAsync(statusUpdate);
    }
}
