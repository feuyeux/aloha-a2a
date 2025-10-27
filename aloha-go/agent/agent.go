package main

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"sync"
	"syscall"
	"time"

	"github.com/aloha/a2a-go/pkg/protocol"
	"github.com/gin-gonic/gin"
	"github.com/gorilla/websocket"
	"google.golang.org/grpc"
)

// DiceAgent represents the A2A agent with multi-transport support
type DiceAgent struct {
	jsonrpcPort int
	grpcPort    int
	restPort    int
	host        string
	
	agentCard *protocol.AgentCard
	executor  *DiceAgentExecutor
	
	servers []interface{}
	mu      sync.Mutex
}

// NewDiceAgent creates a new Dice Agent instance
func NewDiceAgent(grpcPort, jsonrpcPort, restPort int, host string) *DiceAgent {
	agent := &DiceAgent{
		grpcPort:    grpcPort,
		jsonrpcPort: jsonrpcPort,
		restPort:    restPort,
		host:        host,
		executor:    NewDiceAgentExecutor(),
	}
	
	agent.agentCard = agent.createAgentCard()
	
	log.Println("DiceAgent initialized")
	return agent
}

// createAgentCard creates the agent card describing capabilities
func (a *DiceAgent) createAgentCard() *protocol.AgentCard {
	return &protocol.AgentCard{
		Name:        "Dice Agent",
		Description: "An agent that can roll arbitrary dice and check prime numbers",
		URL:         fmt.Sprintf("localhost:%d", a.restPort),
		Version:     "1.0.0",
		Capabilities: protocol.Capability{
			Streaming:         true,
			PushNotifications: false,
		},
		DefaultInputModes:  []string{"text"},
		DefaultOutputModes: []string{"text"},
		Skills: []protocol.Skill{
			{
				ID:          "roll-dice",
				Name:        "Roll Dice",
				Description: "Rolls an N-sided dice",
				Tags:        []string{"dice", "random"},
				Examples:    []string{"Roll a 20-sided dice"},
			},
			{
				ID:          "check-prime",
				Name:        "Prime Checker",
				Description: "Checks if numbers are prime",
				Tags:        []string{"math", "prime"},
				Examples:    []string{"Is 17 prime?"},
			},
		},
		PreferredTransport: "grpc",
	}
}

// Start starts all transport servers
func (a *DiceAgent) Start(ctx context.Context) error {
	log.Println("Starting Dice Agent with multi-transport support")
	
	var wg sync.WaitGroup
	errChan := make(chan error, 3)
	
	// Start JSON-RPC transport
	wg.Add(1)
	go func() {
		defer wg.Done()
		if err := a.startJSONRPCTransport(ctx); err != nil {
			errChan <- fmt.Errorf("JSON-RPC transport error: %w", err)
		}
	}()
	
	// Start gRPC transport
	wg.Add(1)
	go func() {
		defer wg.Done()
		if err := a.startGRPCTransport(ctx); err != nil {
			errChan <- fmt.Errorf("gRPC transport error: %w", err)
		}
	}()
	
	// Start REST transport
	wg.Add(1)
	go func() {
		defer wg.Done()
		if err := a.startRESTTransport(ctx); err != nil {
			errChan <- fmt.Errorf("REST transport error: %w", err)
		}
	}()
	
	// Wait a bit for servers to start
	time.Sleep(500 * time.Millisecond)
	
	log.Println("============================================================")
	log.Println("Dice Agent is running with the following transports:")
	log.Printf("  - gRPC:         %s:%d", a.host, a.grpcPort)
	log.Printf("  - JSON-RPC 2.0: %s:%d", a.host, a.jsonrpcPort)
	log.Printf("  - REST:         %s:%d", a.host, a.restPort)
	log.Printf("  - Agent Card:   http://%s:%d/.well-known/agent-card.json", a.host, a.restPort)
	log.Println("============================================================")
	
	// Wait for context cancellation
	<-ctx.Done()
	
	// Check for errors
	select {
	case err := <-errChan:
		return err
	default:
		return nil
	}
}

// startJSONRPCTransport starts the JSON-RPC 2.0 WebSocket transport
func (a *DiceAgent) startJSONRPCTransport(ctx context.Context) error {
	log.Printf("Starting JSON-RPC transport on %s:%d", a.host, a.jsonrpcPort)
	
	upgrader := websocket.Upgrader{
		CheckOrigin: func(r *http.Request) bool {
			return true
		},
	}
	
	mux := http.NewServeMux()
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		conn, err := upgrader.Upgrade(w, r, nil)
		if err != nil {
			log.Printf("WebSocket upgrade error: %v", err)
			return
		}
		defer conn.Close()
		
		a.handleJSONRPCConnection(ctx, conn)
	})
	
	server := &http.Server{
		Addr:    fmt.Sprintf("%s:%d", a.host, a.jsonrpcPort),
		Handler: mux,
	}
	
	a.mu.Lock()
	a.servers = append(a.servers, server)
	a.mu.Unlock()
	
	go func() {
		<-ctx.Done()
		server.Shutdown(context.Background())
	}()
	
	log.Printf("JSON-RPC transport listening on %s:%d", a.host, a.jsonrpcPort)
	return server.ListenAndServe()
}

// handleJSONRPCConnection handles a JSON-RPC WebSocket connection
func (a *DiceAgent) handleJSONRPCConnection(ctx context.Context, conn *websocket.Conn) {
	log.Println("New JSON-RPC connection established")
	
	for {
		select {
		case <-ctx.Done():
			return
		default:
		}
		
		_, message, err := conn.ReadMessage()
		if err != nil {
			if websocket.IsUnexpectedCloseError(err, websocket.CloseGoingAway, websocket.CloseAbnormalClosure) {
				log.Printf("WebSocket error: %v", err)
			}
			return
		}
		
		// Handle JSON-RPC request
		response := a.handleJSONRPCRequest(ctx, message)
		
		if err := conn.WriteMessage(websocket.TextMessage, response); err != nil {
			log.Printf("Write error: %v", err)
			return
		}
	}
}

// handleJSONRPCRequest processes a JSON-RPC request
func (a *DiceAgent) handleJSONRPCRequest(ctx context.Context, data []byte) []byte {
	var request map[string]interface{}
	if err := json.Unmarshal(data, &request); err != nil {
		return a.createJSONRPCError(-32700, "Parse error", nil)
	}
	
	method, ok := request["method"].(string)
	if !ok {
		return a.createJSONRPCError(-32600, "Invalid Request", nil)
	}
	
	switch method {
	case "message/send", "message/stream":
		return a.handleMessageRequest(ctx, request)
	case "agent/getAuthenticatedExtendedCard":
		return a.handleAgentCardRequest(request)
	default:
		return a.createJSONRPCError(-32601, "Method not found", nil)
	}
}

// handleMessageRequest handles message send/stream requests
func (a *DiceAgent) handleMessageRequest(ctx context.Context, request map[string]interface{}) []byte {
	params, ok := request["params"].(map[string]interface{})
	if !ok {
		return a.createJSONRPCError(-32602, "Invalid params", nil)
	}
	
	messageData, ok := params["message"].(map[string]interface{})
	if !ok {
		return a.createJSONRPCError(-32602, "Invalid message", nil)
	}
	
	// Parse message
	messageJSON, _ := json.Marshal(messageData)
	var message protocol.Message
	if err := json.Unmarshal(messageJSON, &message); err != nil {
		return a.createJSONRPCError(-32602, "Invalid message format", nil)
	}
	
	// Create task
	taskID := protocol.NewUUID()
	eventChan := make(chan protocol.Event, 10)
	
	// Execute in background
	go func() {
		defer close(eventChan)
		if err := a.executor.Execute(ctx, &message, taskID, eventChan); err != nil {
			log.Printf("Execution error: %v", err)
		}
	}()
	
	// Collect events
	var events []protocol.Event
	for event := range eventChan {
		events = append(events, event)
	}
	
	// Return last event (completed status)
	if len(events) > 0 {
		lastEvent := events[len(events)-1]
		eventJSON, _ := json.Marshal(lastEvent)
		
		result := map[string]interface{}{
			"jsonrpc": "2.0",
			"result":  json.RawMessage(eventJSON),
			"id":      request["id"],
		}
		
		response, _ := json.Marshal(result)
		return response
	}
	
	return a.createJSONRPCError(-32603, "Internal error", nil)
}

// handleAgentCardRequest handles agent card requests
func (a *DiceAgent) handleAgentCardRequest(request map[string]interface{}) []byte {
	cardJSON, _ := json.Marshal(a.agentCard)
	
	result := map[string]interface{}{
		"jsonrpc": "2.0",
		"result":  json.RawMessage(cardJSON),
		"id":      request["id"],
	}
	
	response, _ := json.Marshal(result)
	return response
}

// createJSONRPCError creates a JSON-RPC error response
func (a *DiceAgent) createJSONRPCError(code int, message string, id interface{}) []byte {
	errorResponse := map[string]interface{}{
		"jsonrpc": "2.0",
		"error": map[string]interface{}{
			"code":    code,
			"message": message,
		},
		"id": id,
	}
	
	response, _ := json.Marshal(errorResponse)
	return response
}

// startGRPCTransport starts the gRPC transport
func (a *DiceAgent) startGRPCTransport(ctx context.Context) error {
	log.Printf("Starting gRPC transport on %s:%d", a.host, a.grpcPort)
	
	// Note: Full gRPC implementation would use the A2A SDK's gRPC server
	// This is a placeholder that would be replaced with actual SDK integration
	grpcServer := grpc.NewServer()
	
	a.mu.Lock()
	a.servers = append(a.servers, grpcServer)
	a.mu.Unlock()
	
	go func() {
		<-ctx.Done()
		grpcServer.GracefulStop()
	}()
	
	log.Printf("gRPC transport configured on %s:%d (SDK integration pending)", a.host, a.grpcPort)
	
	// Keep running
	<-ctx.Done()
	return nil
}

// startRESTTransport starts the REST HTTP transport
func (a *DiceAgent) startRESTTransport(ctx context.Context) error {
	log.Printf("Starting REST transport on %s:%d", a.host, a.restPort)
	
	gin.SetMode(gin.ReleaseMode)
	router := gin.Default()
	
	// Agent card endpoint
	router.GET("/.well-known/agent-card.json", func(c *gin.Context) {
		c.JSON(http.StatusOK, a.agentCard)
	})
	
	// Message send endpoint
	router.POST("/v1/message/send", func(c *gin.Context) {
		var request struct {
			Message protocol.Message `json:"message"`
		}
		
		if err := c.BindJSON(&request); err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid request"})
			return
		}
		
		// Create task and execute
		taskID := protocol.NewUUID()
		eventChan := make(chan protocol.Event, 10)
		
		go func() {
			defer close(eventChan)
			if err := a.executor.Execute(ctx, &request.Message, taskID, eventChan); err != nil {
				log.Printf("Execution error: %v", err)
			}
		}()
		
		// Collect events
		var events []protocol.Event
		for event := range eventChan {
			events = append(events, event)
		}
		
		// Return last event
		if len(events) > 0 {
			c.JSON(http.StatusOK, events[len(events)-1])
		} else {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "No response"})
		}
	})
	
	// Message stream endpoint
	router.POST("/v1/message/stream", func(c *gin.Context) {
		var request struct {
			Message protocol.Message `json:"message"`
		}
		
		if err := c.BindJSON(&request); err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid request"})
			return
		}
		
		// Set headers for streaming
		c.Header("Content-Type", "application/json")
		c.Header("Transfer-Encoding", "chunked")
		
		// Create task and execute
		taskID := protocol.NewUUID()
		eventChan := make(chan protocol.Event, 10)
		
		go func() {
			defer close(eventChan)
			if err := a.executor.Execute(ctx, &request.Message, taskID, eventChan); err != nil {
				log.Printf("Execution error: %v", err)
			}
		}()
		
		// Stream events
		for event := range eventChan {
			eventJSON, _ := json.Marshal(event)
			c.Writer.Write(eventJSON)
			c.Writer.Write([]byte("\n"))
			c.Writer.Flush()
		}
	})
	
	server := &http.Server{
		Addr:    fmt.Sprintf("%s:%d", a.host, a.restPort),
		Handler: router,
	}
	
	a.mu.Lock()
	a.servers = append(a.servers, server)
	a.mu.Unlock()
	
	go func() {
		<-ctx.Done()
		server.Shutdown(context.Background())
	}()
	
	log.Printf("REST transport listening on %s:%d", a.host, a.restPort)
	return server.ListenAndServe()
}

func main() {
	// Load environment variables
	grpcPort := getEnvInt("GRPC_PORT", 12000)
	jsonrpcPort := getEnvInt("JSONRPC_PORT", 12001)
	restPort := getEnvInt("REST_PORT", 12002)
	host := getEnv("HOST", "0.0.0.0")
	
	// Create agent
	agent := NewDiceAgent(grpcPort, jsonrpcPort, restPort, host)
	
	// Setup context with cancellation
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	
	// Handle shutdown signals
	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, os.Interrupt, syscall.SIGTERM)
	
	go func() {
		<-sigChan
		log.Println("Received interrupt signal, shutting down...")
		cancel()
	}()
	
	// Start agent
	if err := agent.Start(ctx); err != nil && err != http.ErrServerClosed {
		log.Fatalf("Agent error: %v", err)
	}
	
	log.Println("Dice Agent stopped")
}

// Helper functions
func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}

func getEnvInt(key string, defaultValue int) int {
	if value := os.Getenv(key); value != "" {
		var intValue int
		if _, err := fmt.Sscanf(value, "%d", &intValue); err == nil {
			return intValue
		}
	}
	return defaultValue
}
