package main

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"net/url"
	"time"

	"github.com/aloha/a2a-go/pkg/protocol"
	"github.com/gorilla/websocket"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
)

// Client represents an A2A host client with multi-transport support
type Client struct {
	serverURL string
	transport string
	agentCard *protocol.AgentCard
	contextID string // Session context ID for conversation continuity
	
	// Transport-specific clients
	wsConn     *websocket.Conn
	grpcConn   *grpc.ClientConn
	httpClient *http.Client
}

// NewClient creates a new A2A client
func NewClient(serverURL, transport string) *Client {
	return &Client{
		serverURL: serverURL,
		transport: transport,
		contextID: protocol.NewUUID(), // Initialize session context
		httpClient: &http.Client{
			Timeout: 30 * time.Second,
		},
	}
}

// Initialize fetches the agent card and sets up the transport
func (c *Client) Initialize(ctx context.Context) error {
	log.Printf("Connecting to agent at: %s", c.serverURL)
	
	// Fetch agent card
	if err := c.fetchAgentCard(ctx); err != nil {
		return fmt.Errorf("failed to fetch agent card: %w", err)
	}
	
	log.Println("Successfully fetched agent card:")
	log.Printf("  Name: %s", c.agentCard.Name)
	log.Printf("  Description: %s", c.agentCard.Description)
	log.Printf("  Version: %s", c.agentCard.Version)
	
	// Initialize transport
	if err := c.initializeTransport(ctx); err != nil {
		return fmt.Errorf("failed to initialize transport: %w", err)
	}
	
	log.Println("Client initialized successfully")
	return nil
}

// fetchAgentCard retrieves the agent card from the server
func (c *Client) fetchAgentCard(ctx context.Context) error {
	// Parse server URL to get the REST endpoint
	parsedURL, err := url.Parse(c.serverURL)
	if err != nil {
		return err
	}
	
	// Construct agent card URL
	cardURL := fmt.Sprintf("http://%s/.well-known/agent-card.json", parsedURL.Host)
	
	req, err := http.NewRequestWithContext(ctx, "GET", cardURL, nil)
	if err != nil {
		return err
	}
	
	resp, err := c.httpClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("unexpected status code: %d", resp.StatusCode)
	}
	
	var agentCard protocol.AgentCard
	if err := json.NewDecoder(resp.Body).Decode(&agentCard); err != nil {
		return err
	}
	
	c.agentCard = &agentCard
	return nil
}

// initializeTransport sets up the appropriate transport connection
func (c *Client) initializeTransport(ctx context.Context) error {
	switch c.transport {
	case "jsonrpc":
		return c.initializeJSONRPC(ctx)
	case "grpc":
		return c.initializeGRPC(ctx)
	case "rest":
		// REST uses HTTP client which is already initialized
		log.Println("Using REST transport")
		return nil
	default:
		return fmt.Errorf("unsupported transport: %s", c.transport)
	}
}

// initializeJSONRPC sets up WebSocket connection for JSON-RPC
func (c *Client) initializeJSONRPC(ctx context.Context) error {
	log.Println("Initializing JSON-RPC transport")
	
	// Parse URL and convert to WebSocket URL
	parsedURL, err := url.Parse(c.serverURL)
	if err != nil {
		return err
	}
	
	wsURL := fmt.Sprintf("ws://%s/", parsedURL.Host)
	
	dialer := websocket.Dialer{
		HandshakeTimeout: 10 * time.Second,
	}
	
	conn, _, err := dialer.DialContext(ctx, wsURL, nil)
	if err != nil {
		return fmt.Errorf("failed to connect WebSocket: %w", err)
	}
	
	c.wsConn = conn
	log.Println("JSON-RPC WebSocket connection established")
	return nil
}

// initializeGRPC sets up gRPC connection
func (c *Client) initializeGRPC(ctx context.Context) error {
	log.Println("Initializing gRPC transport")
	
	parsedURL, err := url.Parse(c.serverURL)
	if err != nil {
		return err
	}
	
	conn, err := grpc.DialContext(
		ctx,
		parsedURL.Host,
		grpc.WithTransportCredentials(insecure.NewCredentials()),
		grpc.WithBlock(),
	)
	if err != nil {
		return fmt.Errorf("failed to connect gRPC: %w", err)
	}
	
	c.grpcConn = conn
	log.Println("gRPC connection established")
	return nil
}

// SendMessage sends a message to the agent and returns the response
func (c *Client) SendMessage(ctx context.Context, messageText string) (string, error) {
	// Create message with session context
	message := protocol.Message{
		Kind:      "message",
		MessageID: protocol.NewUUID(),
		Role:      "user",
		Parts: []protocol.Part{
			{
				Kind: "text",
				Text: messageText,
			},
		},
		ContextID: c.contextID, // Use session context for continuity
	}
	
	log.Printf("Sending message: %s", messageText)
	log.Printf("Context ID: %s", c.contextID)
	
	// Send based on transport
	switch c.transport {
	case "jsonrpc":
		return c.sendMessageJSONRPC(ctx, message)
	case "grpc":
		return c.sendMessageGRPC(ctx, message)
	case "rest":
		return c.sendMessageREST(ctx, message)
	default:
		return "", fmt.Errorf("unsupported transport: %s", c.transport)
	}
}

// SendMessageStream sends a message and streams the response events
func (c *Client) SendMessageStream(ctx context.Context, messageText string) (<-chan string, <-chan error) {
	responseChan := make(chan string, 10)
	errorChan := make(chan error, 1)
	
	go func() {
		defer close(responseChan)
		defer close(errorChan)
		
		// Create message with session context
		message := protocol.Message{
			Kind:      "message",
			MessageID: protocol.NewUUID(),
			Role:      "user",
			Parts: []protocol.Part{
				{
					Kind: "text",
					Text: messageText,
				},
			},
			ContextID: c.contextID,
		}
		
		log.Printf("Sending streaming message: %s", messageText)
		log.Printf("Context ID: %s", c.contextID)
		
		// Send based on transport
		var err error
		switch c.transport {
		case "jsonrpc":
			err = c.sendMessageStreamJSONRPC(ctx, message, responseChan)
		case "grpc":
			err = c.sendMessageStreamGRPC(ctx, message, responseChan)
		case "rest":
			err = c.sendMessageStreamREST(ctx, message, responseChan)
		default:
			err = fmt.Errorf("unsupported transport: %s", c.transport)
		}
		
		if err != nil {
			errorChan <- err
		}
	}()
	
	return responseChan, errorChan
}

// sendMessageJSONRPC sends a message via JSON-RPC WebSocket
func (c *Client) sendMessageJSONRPC(ctx context.Context, message protocol.Message) (string, error) {
	if c.wsConn == nil {
		return "", fmt.Errorf("WebSocket connection not initialized")
	}
	
	// Create JSON-RPC request
	request := map[string]interface{}{
		"jsonrpc": "2.0",
		"method":  "message/send",
		"params": map[string]interface{}{
			"message": message,
		},
		"id": 1,
	}
	
	// Send request
	if err := c.wsConn.WriteJSON(request); err != nil {
		return "", fmt.Errorf("failed to send message: %w", err)
	}
	
	// Read response
	var response map[string]interface{}
	if err := c.wsConn.ReadJSON(&response); err != nil {
		return "", fmt.Errorf("failed to read response: %w", err)
	}
	
	// Extract result
	result, ok := response["result"].(map[string]interface{})
	if !ok {
		return "", fmt.Errorf("invalid response format")
	}
	
	return c.extractTextFromEvent(result)
}

// sendMessageGRPC sends a message via gRPC
func (c *Client) sendMessageGRPC(ctx context.Context, message protocol.Message) (string, error) {
	if c.grpcConn == nil {
		return "", fmt.Errorf("gRPC connection not initialized")
	}
	
	// Note: Full gRPC implementation would use the A2A SDK's gRPC client
	// This is a placeholder
	log.Println("gRPC message sending (SDK integration pending)")
	
	return "gRPC response placeholder", nil
}

// sendMessageREST sends a message via REST HTTP
func (c *Client) sendMessageREST(ctx context.Context, message protocol.Message) (string, error) {
	parsedURL, err := url.Parse(c.serverURL)
	if err != nil {
		return "", err
	}
	
	endpoint := fmt.Sprintf("http://%s/v1/message/send", parsedURL.Host)
	
	requestBody := map[string]interface{}{
		"message": message,
	}
	
	bodyJSON, err := json.Marshal(requestBody)
	if err != nil {
		return "", err
	}
	
	req, err := http.NewRequestWithContext(ctx, "POST", endpoint, bytes.NewBuffer(bodyJSON))
	if err != nil {
		return "", err
	}
	
	req.Header.Set("Content-Type", "application/json")
	
	resp, err := c.httpClient.Do(req)
	if err != nil {
		return "", fmt.Errorf("failed to send request: %w", err)
	}
	defer resp.Body.Close()
	
	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return "", fmt.Errorf("unexpected status code %d: %s", resp.StatusCode, string(body))
	}
	
	var event map[string]interface{}
	if err := json.NewDecoder(resp.Body).Decode(&event); err != nil {
		return "", err
	}
	
	return c.extractTextFromEvent(event)
}

// sendMessageStreamJSONRPC sends a streaming message via JSON-RPC WebSocket
func (c *Client) sendMessageStreamJSONRPC(ctx context.Context, message protocol.Message, responseChan chan<- string) error {
	if c.wsConn == nil {
		return fmt.Errorf("WebSocket connection not initialized")
	}
	
	// Create JSON-RPC request for streaming
	request := map[string]interface{}{
		"jsonrpc": "2.0",
		"method":  "message/stream",
		"params": map[string]interface{}{
			"message": message,
		},
		"id": 1,
	}
	
	// Send request
	if err := c.wsConn.WriteJSON(request); err != nil {
		return fmt.Errorf("failed to send message: %w", err)
	}
	
	// Read streaming responses
	for {
		select {
		case <-ctx.Done():
			return ctx.Err()
		default:
		}
		
		var response map[string]interface{}
		if err := c.wsConn.ReadJSON(&response); err != nil {
			return fmt.Errorf("failed to read response: %w", err)
		}
		
		// Extract result
		result, ok := response["result"].(map[string]interface{})
		if !ok {
			continue
		}
		
		// Extract text and send to channel
		if text, err := c.extractTextFromEvent(result); err == nil {
			responseChan <- text
		}
		
		// Check if this is the final event
		if final, ok := result["final"].(bool); ok && final {
			break
		}
	}
	
	return nil
}

// sendMessageStreamGRPC sends a streaming message via gRPC
func (c *Client) sendMessageStreamGRPC(ctx context.Context, message protocol.Message, responseChan chan<- string) error {
	if c.grpcConn == nil {
		return fmt.Errorf("gRPC connection not initialized")
	}
	
	// Note: Full gRPC streaming implementation would use the A2A SDK's gRPC client
	log.Println("gRPC streaming (SDK integration pending)")
	responseChan <- "gRPC streaming response placeholder"
	
	return nil
}

// sendMessageStreamREST sends a streaming message via REST HTTP
func (c *Client) sendMessageStreamREST(ctx context.Context, message protocol.Message, responseChan chan<- string) error {
	parsedURL, err := url.Parse(c.serverURL)
	if err != nil {
		return err
	}
	
	endpoint := fmt.Sprintf("http://%s/v1/message/stream", parsedURL.Host)
	
	requestBody := map[string]interface{}{
		"message": message,
	}
	
	bodyJSON, err := json.Marshal(requestBody)
	if err != nil {
		return err
	}
	
	req, err := http.NewRequestWithContext(ctx, "POST", endpoint, bytes.NewBuffer(bodyJSON))
	if err != nil {
		return err
	}
	
	req.Header.Set("Content-Type", "application/json")
	
	resp, err := c.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("failed to send request: %w", err)
	}
	defer resp.Body.Close()
	
	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("unexpected status code %d: %s", resp.StatusCode, string(body))
	}
	
	// Read streaming response line by line
	decoder := json.NewDecoder(resp.Body)
	for {
		select {
		case <-ctx.Done():
			return ctx.Err()
		default:
		}
		
		var event map[string]interface{}
		if err := decoder.Decode(&event); err != nil {
			if err == io.EOF {
				break
			}
			return err
		}
		
		// Extract text and send to channel
		if text, err := c.extractTextFromEvent(event); err == nil {
			responseChan <- text
		}
		
		// Check if this is the final event
		if final, ok := event["final"].(bool); ok && final {
			break
		}
	}
	
	return nil
}

// extractTextFromEvent extracts text from a task status update event
func (c *Client) extractTextFromEvent(event map[string]interface{}) (string, error) {
	// Check for status.message.parts
	status, ok := event["status"].(map[string]interface{})
	if !ok {
		return "", fmt.Errorf("no status in event")
	}
	
	message, ok := status["message"].(map[string]interface{})
	if !ok {
		return "", fmt.Errorf("no message in status")
	}
	
	parts, ok := message["parts"].([]interface{})
	if !ok {
		return "", fmt.Errorf("no parts in message")
	}
	
	var textParts []string
	for _, part := range parts {
		partMap, ok := part.(map[string]interface{})
		if !ok {
			continue
		}
		
		if kind, ok := partMap["kind"].(string); ok && kind == "text" {
			if text, ok := partMap["text"].(string); ok {
				textParts = append(textParts, text)
			}
		}
	}
	
	if len(textParts) == 0 {
		return "", fmt.Errorf("no text found in response")
	}
	
	result := ""
	for _, text := range textParts {
		result += text
	}
	
	log.Printf("Received response: %s", result)
	return result, nil
}

// Close cleans up client resources
func (c *Client) Close() error {
	log.Println("Cleaning up resources...")
	
	if c.wsConn != nil {
		c.wsConn.Close()
	}
	
	if c.grpcConn != nil {
		c.grpcConn.Close()
	}
	
	log.Println("Resource cleanup completed")
	return nil
}
