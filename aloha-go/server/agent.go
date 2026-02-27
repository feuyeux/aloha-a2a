package main

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"sync"
	"syscall"

	"github.com/a2aproject/a2a-go/a2a"
	"github.com/a2aproject/a2a-go/a2agrpc"
	"github.com/a2aproject/a2a-go/a2asrv"
	"google.golang.org/grpc"
)

// AlohaServer represents the A2A agent with multi-transport support using the official SDK
type AlohaServer struct {
	jsonrpcPort int
	grpcPort    int
	restPort    int
	host        string
	transportMode string

	executor       *DiceAgentExecutor
	requestHandler a2asrv.RequestHandler
	agentCard      *a2a.AgentCard
}

// NewAlohaServer creates a new Aloha Server instance
func NewAlohaServer(grpcPort, jsonrpcPort, restPort int, host string, transportMode string) *AlohaServer {
	executor := NewDiceAgentExecutor()

	server := &AlohaServer{
		grpcPort:      grpcPort,
		jsonrpcPort:   jsonrpcPort,
		restPort:     restPort,
		host:          host,
		transportMode: transportMode,
		executor:      executor,
	}

	// Create agent card
	server.agentCard = server.createAgentCard()

	// Create transport-agnostic request handler using the SDK
	server.requestHandler = a2asrv.NewHandler(executor)

	log.Println("Dice Agent initialized with A2A SDK")
	return server
}

// createAgentCard creates the agent card describing capabilities
func (a *AlohaServer) createAgentCard() *a2a.AgentCard {
	// Determine URL and preferred transport based on transport mode
	var url string
	var preferredTransport a2a.TransportProtocol

	switch a.transportMode {
	case "grpc":
		url = fmt.Sprintf("localhost:%d", a.grpcPort)
		preferredTransport = a2a.TransportProtocolGRPC
	case "jsonrpc":
		url = fmt.Sprintf("http://localhost:%d", a.jsonrpcPort)
		preferredTransport = a2a.TransportProtocolJSONRPC
	default: // rest
		url = fmt.Sprintf("http://localhost:%d", a.restPort)
		preferredTransport = a2a.TransportProtocolHTTPJSON
	}

	return &a2a.AgentCard{
		Name:        "Dice Agent",
		Description: "An agent that can roll arbitrary dice and check prime numbers",
		URL:         url,
		Version:     "1.0.0",
		Capabilities: a2a.AgentCapabilities{
			Streaming: true,
		},
		DefaultInputModes:  []string{"text"},
		DefaultOutputModes: []string{"text"},
		Skills: []a2a.AgentSkill{
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
		AdditionalInterfaces: []a2a.AgentInterface{
			{
				Transport: a2a.TransportProtocolGRPC,
				URL:       fmt.Sprintf("localhost:%d", a.grpcPort),
			},
			{
				Transport: a2a.TransportProtocolJSONRPC,
				URL:       fmt.Sprintf("http://localhost:%d", a.jsonrpcPort),
			},
			{
				Transport: a2a.TransportProtocolHTTPJSON,
				URL:       fmt.Sprintf("http://localhost:%d", a.restPort),
			},
		},
		PreferredTransport: preferredTransport,
	}
}

// Start starts all transport servers
func (a *AlohaServer) Start(ctx context.Context) error {
	log.Println("============================================================")
	log.Println("=== Dice Agent starting ===")
	log.Println("============================================================")

	var wg sync.WaitGroup
	errChan := make(chan error, 3)

	// Start gRPC transport
	wg.Add(1)
	go func() {
		defer wg.Done()
		if err := a.startGRPCTransport(ctx); err != nil {
			errChan <- fmt.Errorf("gRPC transport error: %w", err)
		}
	}()

	// Start JSON-RPC transport
	wg.Add(1)
	go func() {
		defer wg.Done()
		if err := a.startJSONRPCTransport(ctx); err != nil {
			errChan <- fmt.Errorf("JSON-RPC transport error: %w", err)
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

	log.Println("============================================================")
	log.Println("Dice Agent is running with the following transports:")
	log.Printf("  - Active Mode:  %s", a.transportMode)
	log.Printf("  - gRPC:         %s:%d", a.host, a.grpcPort)
	log.Printf("  - JSON-RPC 2.0: http://%s:%d", a.host, a.jsonrpcPort)
	log.Printf("  - REST:         http://%s:%d", a.host, a.restPort)
	// Agent card URL depends on transport mode
	var agentCardPort int
	switch a.transportMode {
	case "grpc":
		agentCardPort = a.restPort
	case "jsonrpc":
		agentCardPort = a.jsonrpcPort
	default:
		agentCardPort = a.restPort
	}
	log.Printf("  - Agent Card:   http://%s:%d/.well-known/agent-card.json", a.host, agentCardPort)
	log.Println("  - SDK: github.com/a2aproject/a2a-go v0.3.7")
	log.Println("============================================================")

	// Wait for context cancellation
	<-ctx.Done()

	select {
	case err := <-errChan:
		return err
	default:
		return nil
	}
}

// startGRPCTransport starts the gRPC transport using the SDK
func (a *AlohaServer) startGRPCTransport(ctx context.Context) error {
	log.Printf("Starting gRPC transport on %s:%d", a.host, a.grpcPort)

	listener, err := net.Listen("tcp", fmt.Sprintf("%s:%d", a.host, a.grpcPort))
	if err != nil {
		return fmt.Errorf("failed to listen on gRPC port: %w", err)
	}

	grpcServer := grpc.NewServer()

	// Register A2A gRPC handler from the SDK
	grpcHandler := a2agrpc.NewHandler(a.requestHandler)
	grpcHandler.RegisterWith(grpcServer)

	go func() {
		<-ctx.Done()
		grpcServer.GracefulStop()
	}()

	log.Printf("gRPC transport listening on %s:%d", a.host, a.grpcPort)
	return grpcServer.Serve(listener)
}

// startJSONRPCTransport starts the JSON-RPC 2.0 transport using the SDK
func (a *AlohaServer) startJSONRPCTransport(ctx context.Context) error {
	log.Printf("Starting JSON-RPC transport on %s:%d", a.host, a.jsonrpcPort)

	mux := http.NewServeMux()

	// Serve agent card at well-known path
	mux.Handle("/.well-known/agent-card.json", a2asrv.NewStaticAgentCardHandler(a.agentCard))

	// Serve JSON-RPC handler from the SDK at root
	mux.Handle("/", a2asrv.NewJSONRPCHandler(a.requestHandler))

	server := &http.Server{
		Addr:    fmt.Sprintf("%s:%d", a.host, a.jsonrpcPort),
		Handler: mux,
	}

	go func() {
		<-ctx.Done()
		server.Shutdown(context.Background())
	}()

	log.Printf("JSON-RPC transport listening on %s:%d", a.host, a.jsonrpcPort)
	return server.ListenAndServe()
}

// startRESTTransport starts the REST HTTP+JSON transport
// The SDK does not provide a built-in REST handler, so we implement a thin
// adapter that translates REST HTTP requests to SDK RequestHandler calls.
func (a *AlohaServer) startRESTTransport(ctx context.Context) error {
	log.Printf("Starting REST transport on %s:%d", a.host, a.restPort)

	mux := http.NewServeMux()

	// Agent card endpoint
	mux.Handle("/.well-known/agent-card.json", a2asrv.NewStaticAgentCardHandler(a.agentCard))

	// REST: POST /v1/message:send - non-streaming message send
	mux.HandleFunc("/v1/message:send", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
			return
		}
		a.handleRESTMessageSend(ctx, w, r)
	})

	// REST: POST /v1/message:stream - streaming message send (SSE)
	mux.HandleFunc("/v1/message:stream", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
			return
		}
		a.handleRESTMessageStream(ctx, w, r)
	})

	// REST: GET /v1/tasks/{taskId}
	mux.HandleFunc("/v1/tasks/", func(w http.ResponseWriter, r *http.Request) {
		path := r.URL.Path
		if r.Method == http.MethodPost && strings.HasSuffix(path, ":cancel") {
			// POST /v1/tasks/{taskId}:cancel
			taskID := strings.TrimPrefix(path, "/v1/tasks/")
			taskID = strings.TrimSuffix(taskID, ":cancel")
			a.handleRESTCancelTask(ctx, w, taskID)
			return
		}
		if r.Method == http.MethodGet {
			// GET /v1/tasks/{taskId}
			taskID := strings.TrimPrefix(path, "/v1/tasks/")
			a.handleRESTGetTask(ctx, w, taskID)
			return
		}
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
	})

	server := &http.Server{
		Addr:    fmt.Sprintf("%s:%d", a.host, a.restPort),
		Handler: mux,
	}

	go func() {
		<-ctx.Done()
		server.Shutdown(context.Background())
	}()

	log.Printf("REST transport listening on %s:%d", a.host, a.restPort)
	return server.ListenAndServe()
}

// handleRESTMessageSend handles non-streaming message send via REST
func (a *AlohaServer) handleRESTMessageSend(ctx context.Context, w http.ResponseWriter, r *http.Request) {
	body, err := io.ReadAll(r.Body)
	if err != nil {
		http.Error(w, "Failed to read request body", http.StatusBadRequest)
		return
	}
	defer r.Body.Close()

	var params a2a.MessageSendParams
	if err := json.Unmarshal(body, &params); err != nil {
		// Try to parse as a bare Message (without wrapper)
		var msg a2a.Message
		if err2 := json.Unmarshal(body, &msg); err2 != nil {
			http.Error(w, "Invalid request body", http.StatusBadRequest)
			return
		}
		params = a2a.MessageSendParams{Message: &msg}
	}

	result, err := a.requestHandler.OnSendMessage(ctx, &params)
	if err != nil {
		log.Printf("REST SendMessage error: %v", err)
		http.Error(w, fmt.Sprintf("Error: %v", err), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(result)
}

// handleRESTMessageStream handles streaming message send via REST (SSE)
func (a *AlohaServer) handleRESTMessageStream(ctx context.Context, w http.ResponseWriter, r *http.Request) {
	body, err := io.ReadAll(r.Body)
	if err != nil {
		http.Error(w, "Failed to read request body", http.StatusBadRequest)
		return
	}
	defer r.Body.Close()

	var params a2a.MessageSendParams
	if err := json.Unmarshal(body, &params); err != nil {
		var msg a2a.Message
		if err2 := json.Unmarshal(body, &msg); err2 != nil {
			http.Error(w, "Invalid request body", http.StatusBadRequest)
			return
		}
		params = a2a.MessageSendParams{Message: &msg}
	}

	// Set SSE headers
	w.Header().Set("Content-Type", "text/event-stream")
	w.Header().Set("Cache-Control", "no-cache")
	w.Header().Set("Connection", "keep-alive")

	flusher, ok := w.(http.Flusher)
	if !ok {
		http.Error(w, "Streaming not supported", http.StatusInternalServerError)
		return
	}

	// Use the streaming handler from the SDK
	for event, err := range a.requestHandler.OnSendMessageStream(ctx, &params) {
		if err != nil {
			log.Printf("REST stream error: %v", err)
			errorJSON, _ := json.Marshal(map[string]string{"error": err.Error()})
			fmt.Fprintf(w, "data: %s\n\n", errorJSON)
			flusher.Flush()
			return
		}

		eventJSON, err := json.Marshal(event)
		if err != nil {
			log.Printf("Failed to marshal event: %v", err)
			continue
		}

		fmt.Fprintf(w, "data: %s\n\n", eventJSON)
		flusher.Flush()
	}
}

// handleRESTGetTask handles task retrieval via REST
func (a *AlohaServer) handleRESTGetTask(ctx context.Context, w http.ResponseWriter, taskID string) {
	if taskID == "" {
		http.Error(w, "Task ID required", http.StatusBadRequest)
		return
	}

	task, err := a.requestHandler.OnGetTask(ctx, &a2a.TaskQueryParams{ID: a2a.TaskID(taskID)})
	if err != nil {
		log.Printf("REST GetTask error: %v", err)
		http.Error(w, fmt.Sprintf("Error: %v", err), http.StatusNotFound)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(task)
}

// handleRESTCancelTask handles task cancellation via REST
func (a *AlohaServer) handleRESTCancelTask(ctx context.Context, w http.ResponseWriter, taskID string) {
	if taskID == "" {
		http.Error(w, "Task ID required", http.StatusBadRequest)
		return
	}

	task, err := a.requestHandler.OnCancelTask(ctx, &a2a.TaskIDParams{ID: a2a.TaskID(taskID)})
	if err != nil {
		log.Printf("REST CancelTask error: %v", err)
		http.Error(w, fmt.Sprintf("Error: %v", err), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(task)
}

func main() {
	// Load environment variables
	grpcPort := getEnvInt("GRPC_PORT", 12000)
	jsonrpcPort := getEnvInt("JSONRPC_PORT", 12001)
	restPort := getEnvInt("REST_PORT", 12002)
	host := getEnv("HOST", "0.0.0.0")
	transportMode := getEnv("TRANSPORT_MODE", "jsonrpc")

	// Create server
	server := NewAlohaServer(grpcPort, jsonrpcPort, restPort, host, transportMode)

	// Setup context with cancellation
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	// Handle shutdown signals
	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, os.Interrupt, syscall.SIGTERM)

	go func() {
		<-sigChan
		log.Println("Shutdown signal received, stopping Dice Agent...")
		cancel()
	}()

	// Start server
	if err := server.Start(ctx); err != nil && err != http.ErrServerClosed {
		log.Fatalf("Server error: %v", err)
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
