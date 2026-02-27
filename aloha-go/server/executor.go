package main

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"os"
	"regexp"
	"strconv"
	"strings"

	"github.com/a2aproject/a2a-go/a2a"
	"github.com/a2aproject/a2a-go/a2asrv"
	"github.com/a2aproject/a2a-go/a2asrv/eventqueue"
	"github.com/ollama/ollama/api"
)

// System prompt for the LLM
const systemPrompt = `You are a dice rolling agent that can roll arbitrary N-sided dice and check if numbers are prime.

When asked to roll a dice, call the roll_dice tool with the number of sides as an integer parameter.

When asked to check if numbers are prime, call the check_prime tool with a list of integers.

When asked to roll a dice and check if the result is prime:
1. First call roll_dice to get the result
2. Then call check_prime with the result from step 1
3. Include both the dice result and prime check in your response

Always use the tools - never try to roll dice or check primes yourself.
Be conversational and friendly in your responses.

你是一个骰子代理，可以投掷任意面数的骰子并检查数字是否为质数。
当被要求投掷骰子时，使用 roll_dice 工具。
当被要求检查质数时，使用 check_prime 工具。
始终使用工具，不要自己计算。`

// ValidationError represents a request validation error
type ValidationError struct {
	Message string
}

func (e *ValidationError) Error() string {
	return e.Message
}

// Ensure DiceAgentExecutor implements a2asrv.AgentExecutor
var _ a2asrv.AgentExecutor = (*DiceAgentExecutor)(nil)

// DiceAgentExecutor implements the a2asrv.AgentExecutor interface
type DiceAgentExecutor struct {
	ollamaClient *api.Client
	ollamaModel  string
	baseURL      string
	useLLM       bool
}

// NewDiceAgentExecutor creates a new executor instance
func NewDiceAgentExecutor() *DiceAgentExecutor {
	baseURL := os.Getenv("OLLAMA_BASE_URL")
	if baseURL == "" {
		baseURL = "http://localhost:11434"
	}

	model := os.Getenv("OLLAMA_MODEL")
	if model == "" {
		model = "qwen2.5"
	}

	executor := &DiceAgentExecutor{
		baseURL:     baseURL,
		ollamaModel: model,
		useLLM:      true,
	}

	// Try to create Ollama client
	client, err := api.ClientFromEnvironment()
	if err != nil {
		log.Printf("Warning: Failed to create Ollama client: %v", err)
		log.Printf("Will use fallback pattern matching instead")
		executor.useLLM = false
		return executor
	}

	executor.ollamaClient = client

	// Validate Ollama connection
	if err := executor.validateOllamaConnection(); err != nil {
		log.Printf("Warning: Ollama connection validation failed: %v", err)
		log.Printf("Please ensure Ollama is installed and running:")
		log.Printf("  1. Install Ollama: https://ollama.ai/download")
		log.Printf("  2. Pull %s model: ollama pull %s", model, model)
		log.Printf("  3. Start Ollama service: ollama serve")
		log.Printf("Will use fallback pattern matching instead")
		executor.useLLM = false
		return executor
	}

	log.Printf("Ollama client initialized successfully")
	log.Printf("  Base URL: %s", baseURL)
	log.Printf("  Model: %s", model)

	return executor
}

// validateOllamaConnection validates that Ollama is accessible
func (e *DiceAgentExecutor) validateOllamaConnection() error {
	if e.ollamaClient == nil {
		return fmt.Errorf("Ollama client is nil")
	}

	ctx := context.Background()
	_, err := e.ollamaClient.List(ctx)
	if err != nil {
		return fmt.Errorf("failed to connect to Ollama at %s: %w", e.baseURL, err)
	}

	return nil
}

// getTools returns the tool definitions for Ollama
func (e *DiceAgentExecutor) getTools() []api.Tool {
	rollDiceProperties := api.NewToolPropertiesMap()
	rollDiceProperties.Set("sides", api.ToolProperty{
		Type:        api.PropertyType{"integer"},
		Description: "The number of sides on the dice (must be positive)",
	})

	checkPrimeProperties := api.NewToolPropertiesMap()
	checkPrimeProperties.Set("numbers", api.ToolProperty{
		Type:        api.PropertyType{"array"},
		Description: "List of integers to check for primality",
		Items: map[string]interface{}{
			"type": "integer",
		},
	})

	return []api.Tool{
		{
			Type: "function",
			Function: api.ToolFunction{
				Name:        "roll_dice",
				Description: "Rolls an N-sided dice and returns a random number between 1 and N",
				Parameters: api.ToolFunctionParameters{
					Type:       "object",
					Properties: rollDiceProperties,
					Required:   []string{"sides"},
				},
			},
		},
		{
			Type: "function",
			Function: api.ToolFunction{
				Name:        "check_prime",
				Description: "Checks if the given numbers are prime and returns which ones are prime",
				Parameters: api.ToolFunctionParameters{
					Type:       "object",
					Properties: checkPrimeProperties,
					Required:   []string{"numbers"},
				},
			},
		},
	}
}

// processWithLLM processes the message using Ollama LLM
func (e *DiceAgentExecutor) processWithLLM(ctx context.Context, messageText string) (string, error) {
	if e.ollamaClient == nil {
		return "", fmt.Errorf("Ollama client not initialized")
	}

	messages := []api.Message{
		{Role: "system", Content: systemPrompt},
		{Role: "user", Content: messageText},
	}

	req := &api.ChatRequest{
		Model:    e.ollamaModel,
		Messages: messages,
		Tools:    e.getTools(),
		Stream:   new(bool),
	}

	var response string
	var toolCalls []api.ToolCall

	respFunc := func(resp api.ChatResponse) error {
		if len(resp.Message.ToolCalls) > 0 {
			toolCalls = resp.Message.ToolCalls
		}
		if resp.Message.Content != "" {
			response = resp.Message.Content
		}
		return nil
	}

	err := e.ollamaClient.Chat(ctx, req, respFunc)
	if err != nil {
		return "", fmt.Errorf("Ollama chat error: %w", err)
	}

	if len(toolCalls) > 0 {
		log.Printf("LLM requested %d tool call(s)", len(toolCalls))

		for _, toolCall := range toolCalls {
			log.Printf("Executing tool: %s", toolCall.Function.Name)

			toolResult, err := e.executeTool(toolCall.Function.Name, toolCall.Function.Arguments.ToMap())
			if err != nil {
				log.Printf("Tool execution error: %v", err)
				return "", fmt.Errorf("tool execution failed: %w", err)
			}

			messages = append(messages, api.Message{
				Role:      "assistant",
				Content:   "",
				ToolCalls: []api.ToolCall{toolCall},
			})
			messages = append(messages, api.Message{
				Role:    "tool",
				Content: toolResult,
			})
		}

		req.Messages = messages
		req.Tools = nil

		var finalResponse string
		finalRespFunc := func(resp api.ChatResponse) error {
			if resp.Message.Content != "" {
				finalResponse = resp.Message.Content
			}
			return nil
		}

		err = e.ollamaClient.Chat(ctx, req, finalRespFunc)
		if err != nil {
			return "", fmt.Errorf("Ollama follow-up chat error: %w", err)
		}

		return finalResponse, nil
	}

	return response, nil
}

// executeTool executes a tool and returns the result as a string
func (e *DiceAgentExecutor) executeTool(toolName string, argsJSON map[string]interface{}) (string, error) {
	switch toolName {
	case "roll_dice":
		sides, ok := argsJSON["sides"].(float64)
		if !ok {
			return "", fmt.Errorf("invalid 'sides' parameter")
		}
		sidesInt := int(sides)
		if sidesInt <= 0 {
			return "", &ValidationError{Message: fmt.Sprintf("'sides' must be positive, got %d", sidesInt)}
		}
		if sidesInt > 1000000 {
			return "", &ValidationError{Message: fmt.Sprintf("'sides' must be <= 1000000, got %d", sidesInt)}
		}
		result, err := RollDice(sidesInt)
		if err != nil {
			return "", err
		}
		return fmt.Sprintf(`{"result": %d}`, result), nil

	case "check_prime":
		numbersRaw, ok := argsJSON["numbers"].([]interface{})
		if !ok {
			return "", fmt.Errorf("invalid 'numbers' parameter")
		}
		numbers := make([]int, len(numbersRaw))
		for i, n := range numbersRaw {
			numFloat, ok := n.(float64)
			if !ok {
				return "", fmt.Errorf("invalid number at index %d", i)
			}
			numbers[i] = int(numFloat)
		}
		if len(numbers) > 1000 {
			return "", &ValidationError{Message: fmt.Sprintf("'numbers' list too large (max 1000), got %d", len(numbers))}
		}
		for _, num := range numbers {
			if num < 0 {
				return "", &ValidationError{Message: fmt.Sprintf("All numbers must be non-negative, got %d", num)}
			}
		}
		result := CheckPrime(numbers)
		resultJSON, _ := json.Marshal(map[string]string{"result": result})
		return string(resultJSON), nil

	default:
		return "", fmt.Errorf("unknown tool: %s", toolName)
	}
}

// Execute implements a2asrv.AgentExecutor - processes request and writes A2A events to queue.
func (e *DiceAgentExecutor) Execute(ctx context.Context, reqCtx *a2asrv.RequestContext, queue eventqueue.Queue) error {
	taskID := reqCtx.TaskID
	log.Printf("Received new request. taskId=%s", taskID)

	// Extract text from the incoming message
	messageText := extractTextFromA2AMessage(reqCtx.Message)
	log.Printf("Extracted message text: %s", messageText)

	if strings.TrimSpace(messageText) == "" {
		log.Printf("Empty message text received")
		return e.writeFailedStatus(ctx, reqCtx, queue, "Error: Empty message received. Please provide a message.")
	}

	// Write submitted status for new tasks
	if reqCtx.StoredTask == nil {
		event := a2a.NewStatusUpdateEvent(reqCtx, a2a.TaskStateSubmitted, nil)
		if err := queue.Write(ctx, event); err != nil {
			return fmt.Errorf("failed to write state submitted: %w", err)
		}
	}

	// Write working status
	event := a2a.NewStatusUpdateEvent(reqCtx, a2a.TaskStateWorking, nil)
	if err := queue.Write(ctx, event); err != nil {
		return fmt.Errorf("failed to write state working: %w", err)
	}
	log.Printf("Task started working: %s", taskID)

	// Process the message
	response, err := e.processMessage(ctx, messageText)
	if err != nil {
		log.Printf("Error processing message: %v", err)
		return e.writeFailedStatus(ctx, reqCtx, queue, fmt.Sprintf("Error processing your request: %s", err.Error()))
	}

	log.Printf("Generated response length=%d", len(response))
	log.Printf("Response content: %s", response)

	// Write artifact with the response
	artifactEvent := a2a.NewArtifactEvent(reqCtx, a2a.TextPart{Text: response})
	if err := queue.Write(ctx, artifactEvent); err != nil {
		return fmt.Errorf("failed to write artifact: %w", err)
	}

	// Write completed status (final event)
	completedEvent := a2a.NewStatusUpdateEvent(reqCtx, a2a.TaskStateCompleted, nil)
	completedEvent.Final = true
	if err := queue.Write(ctx, completedEvent); err != nil {
		return fmt.Errorf("failed to write state completed: %w", err)
	}

	log.Printf("Task completed successfully: %s", taskID)
	return nil
}

// Cancel implements a2asrv.AgentExecutor - cancels an ongoing task.
func (e *DiceAgentExecutor) Cancel(ctx context.Context, reqCtx *a2asrv.RequestContext, queue eventqueue.Queue) error {
	log.Printf("Cancel requested for task: %s", reqCtx.TaskID)

	cancelEvent := a2a.NewStatusUpdateEvent(reqCtx, a2a.TaskStateCanceled, nil)
	cancelEvent.Final = true
	if err := queue.Write(ctx, cancelEvent); err != nil {
		return fmt.Errorf("failed to write cancel event: %w", err)
	}

	log.Printf("Task cancelled successfully: %s", reqCtx.TaskID)
	return nil
}

// writeFailedStatus writes a failed status event
func (e *DiceAgentExecutor) writeFailedStatus(ctx context.Context, reqCtx *a2asrv.RequestContext, queue eventqueue.Queue, errorMessage string) error {
	msg := a2a.NewMessage(a2a.MessageRoleAgent, a2a.TextPart{Text: errorMessage})
	event := a2a.NewStatusUpdateEvent(reqCtx, a2a.TaskStateFailed, msg)
	event.Final = true
	if err := queue.Write(ctx, event); err != nil {
		return fmt.Errorf("failed to write failed status: %w", err)
	}
	return nil
}

// processMessage processes the user message and generates a response
func (e *DiceAgentExecutor) processMessage(ctx context.Context, messageText string) (string, error) {
	if e.useLLM && e.ollamaClient != nil {
		log.Printf("Processing message with Ollama LLM")
		response, err := e.processWithLLM(ctx, messageText)
		if err != nil {
			log.Printf("LLM processing failed: %v, falling back to pattern matching", err)
		} else {
			return response, nil
		}
	}

	// Fallback to pattern matching
	log.Printf("Processing message with pattern matching (fallback)")
	messageLower := strings.ToLower(messageText)

	if strings.Contains(messageLower, "roll") && strings.Contains(messageLower, "dice") {
		sides := extractDiceSides(messageText)
		if sides <= 0 {
			return "", &ValidationError{Message: fmt.Sprintf("'sides' must be positive, got %d", sides)}
		}
		if sides > 1000000 {
			return "", &ValidationError{Message: fmt.Sprintf("'sides' must be <= 1000000, got %d", sides)}
		}
		result, err := RollDice(sides)
		if err != nil {
			return "", fmt.Errorf("error rolling dice: %w", err)
		}
		if strings.Contains(messageLower, "prime") {
			primeResult := CheckPrime([]int{result})
			return fmt.Sprintf("I rolled a %d-sided dice and got: %d. %s", sides, result, primeResult), nil
		}
		return fmt.Sprintf("I rolled a %d-sided dice and got: %d", sides, result), nil
	}

	if strings.Contains(messageLower, "prime") {
		numbers := extractNumbers(messageText)
		if len(numbers) > 0 {
			if len(numbers) > 1000 {
				return "", &ValidationError{Message: fmt.Sprintf("'numbers' list too large (max 1000), got %d", len(numbers))}
			}
			for _, num := range numbers {
				if num < 0 {
					return "", &ValidationError{Message: fmt.Sprintf("All numbers must be non-negative, got %d", num)}
				}
			}
			return CheckPrime(numbers), nil
		}
		return "Please provide numbers to check for primality.", nil
	}

	return "I can roll dice and check if numbers are prime. What would you like me to do?", nil
}

// extractTextFromA2AMessage extracts text content from an a2a.Message
func extractTextFromA2AMessage(message *a2a.Message) string {
	if message == nil {
		return ""
	}
	var textParts []string
	for _, part := range message.Parts {
		if tp, ok := part.(a2a.TextPart); ok && tp.Text != "" {
			textParts = append(textParts, tp.Text)
		}
	}
	return strings.Join(textParts, "")
}

// extractDiceSides extracts the number of dice sides from the message
func extractDiceSides(message string) int {
	patterns := []string{
		`(\d+)[-\s]?sided`,
		`d(\d+)`,
		`(\d+)\s+side`,
	}
	for _, pattern := range patterns {
		re := regexp.MustCompile(pattern)
		matches := re.FindStringSubmatch(message)
		if len(matches) > 1 {
			sides, err := strconv.Atoi(matches[1])
			if err == nil && sides > 0 {
				return sides
			}
		}
	}
	return 6
}

// extractNumbers extracts all numbers from the message
func extractNumbers(message string) []int {
	re := regexp.MustCompile(`\b(\d+)\b`)
	matches := re.FindAllStringSubmatch(message, -1)
	var numbers []int
	for _, match := range matches {
		if len(match) > 1 {
			num, err := strconv.Atoi(match[1])
			if err == nil {
				numbers = append(numbers, num)
			}
		}
	}
	return numbers
}
