package main

import (
	"context"
	"encoding/json"
	"flag"
	"fmt"
	"log"
	"net/http"
	"os"
	"time"

	"github.com/a2aproject/a2a-go/a2a"
	"github.com/a2aproject/a2a-go/a2aclient"
	"github.com/a2aproject/a2a-go/a2aclient/agentcard"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
)

func main() {
	// Parse command-line flags
	transport := flag.String("transport", "jsonrpc", "Transport protocol to use (jsonrpc, grpc, rest)")
	host := flag.String("host", "localhost", "Agent hostname")
	port := flag.Int("port", 0, "Agent port (default: 12000 for gRPC, 12001 for JSON-RPC, 12002 for REST)")
	message := flag.String("message", "", "Message to send to the agent")
	stream := flag.Bool("stream", false, "Enable streaming response")
	cardURL := flag.String("card-url", "", "Agent card URL (auto-resolved if empty)")

	flag.Parse()

	// Validate message
	if *message == "" {
		fmt.Println("Usage: client --transport <jsonrpc|grpc|rest> --host <hostname> --port <port> --message <text> [--stream]")
		fmt.Println("\nOptions:")
		fmt.Println("  --transport  Transport protocol (jsonrpc, grpc, rest) [default: jsonrpc]")
		fmt.Println("  --host       Agent hostname [default: localhost]")
		fmt.Println("  --port       Agent port [default: 12000 for gRPC, 12001 for JSON-RPC, 12002 for REST]")
		fmt.Println("  --message    Message to send to the agent [required]")
		fmt.Println("  --stream     Enable streaming response [default: false]")
		fmt.Println("  --card-url   Agent card URL (auto-resolved from host:port if empty)")
		fmt.Println("\nExamples:")
		fmt.Println("  # Send message using JSON-RPC (default)")
		fmt.Println("  client --message \"Roll a 20-sided dice\"")
		fmt.Println("")
		fmt.Println("  # Send message using REST")
		fmt.Println("  client --transport rest --port 12002 --message \"Roll a 20-sided dice\"")
		fmt.Println("")
		fmt.Println("  # Send message using gRPC with streaming")
		fmt.Println("  client --transport grpc --port 12000 --message \"Check if 2, 7, 11 are prime\" --stream")
		os.Exit(1)
	}

	// Set default port based on transport if not specified
	if *port == 0 {
		switch *transport {
		case "grpc":
			*port = 12000
		case "jsonrpc":
			*port = 12001
		case "rest":
			*port = 12002
		default:
			log.Fatalf("Unsupported transport: %s (use jsonrpc, grpc, or rest)", *transport)
		}
	}

	log.Println("============================================================")
	log.Println("A2A Host Client (SDK)")
	log.Printf("  Transport: %s", *transport)
	log.Printf("  Host: %s:%d", *host, *port)
	log.Printf("  Streaming: %v", *stream)
	log.Printf("  Message: %s", *message)
	log.Println("============================================================")

	// Create context with timeout
	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()

	// Determine server URL based on transport
	var serverURL string
	if *transport == "grpc" {
		serverURL = fmt.Sprintf("%s:%d", *host, *port)
	} else {
		serverURL = fmt.Sprintf("http://%s:%d", *host, *port)
	}

	var client *a2aclient.Client
	var restClient *RESTClient
	var err error

	switch *transport {
	case "grpc":
		client, err = createGRPCClient(ctx, *host, *port, *cardURL)
	case "jsonrpc":
		client, err = createJSONRPCClient(ctx, *host, *port, *cardURL)
	case "rest":
		restClient, err = createRESTClient(ctx, serverURL, *cardURL)
		if err == nil {
			log.Printf("Connected to agent: %s (v%s)", restClient.agentCard.Name, restClient.agentCard.Version)
			log.Printf("  Skills: %d", len(restClient.agentCard.Skills))
			for _, skill := range restClient.agentCard.Skills {
				log.Printf("    - %s: %s", skill.Name, skill.Description)
			}
		}
	default:
		log.Fatalf("Unsupported transport: %s", *transport)
	}

	if err != nil {
		log.Fatalf("Failed to create client: %v", err)
	}

	if client != nil {
		defer client.Destroy()
		// Fetch and display agent card
		card, err := client.GetAgentCard(ctx)
		if err != nil {
			log.Printf("Warning: could not fetch agent card: %v", err)
		} else {
			log.Printf("Connected to agent: %s (v%s)", card.Name, card.Version)
			log.Printf("  Skills: %d", len(card.Skills))
			for _, skill := range card.Skills {
				log.Printf("    - %s: %s", skill.Name, skill.Description)
			}
		}
	}

	// Build the message
	msg := a2a.NewMessage(a2a.MessageRoleUser, a2a.TextPart{Text: *message})
	params := &a2a.MessageSendParams{Message: msg}

	if *transport == "rest" {
		if *stream {
			sendRESTStreamingMessage(ctx, restClient, params)
		} else {
			sendRESTMessage(ctx, restClient, params)
		}
	} else {
		if *stream {
			sendStreamingMessage(ctx, client, params)
		} else {
			sendMessage(ctx, client, params)
		}
	}
}

// createGRPCClient creates a client using gRPC transport
func createGRPCClient(ctx context.Context, host string, port int, cardURL string) (*a2aclient.Client, error) {
	card, err := resolveAgentCard(ctx, host, port, cardURL)
	if err != nil {
		return nil, fmt.Errorf("failed to resolve agent card: %w", err)
	}

	return a2aclient.NewFromCard(ctx, card,
		a2aclient.WithGRPCTransport(
			grpc.WithTransportCredentials(insecure.NewCredentials()),
		),
	)
}

// createJSONRPCClient creates a client using JSON-RPC transport
func createJSONRPCClient(ctx context.Context, host string, port int, cardURL string) (*a2aclient.Client, error) {
	card, err := resolveAgentCard(ctx, host, port, cardURL)
	if err != nil {
		return nil, fmt.Errorf("failed to resolve agent card: %w", err)
	}

	return a2aclient.NewFromCard(ctx, card,
		a2aclient.WithJSONRPCTransport(http.DefaultClient),
	)
}

// createRESTClient creates a client using REST transport
func createRESTClient(ctx context.Context, serverURL, cardURL string) (*RESTClient, error) {
	log.Printf("Resolving agent card from: %s", cardURL)
	return NewRESTClient(ctx, serverURL, cardURL)
}

// sendRESTMessage sends a non-streaming message using REST transport
func sendRESTMessage(ctx context.Context, client *RESTClient, params *a2a.MessageSendParams) {
	log.Println("Sending message (non-streaming)...")

	result, err := client.SendMessage(ctx, params)
	if err != nil {
		log.Fatalf("Failed to send message: %v", err)
	}

	fmt.Println("\n============================================================")
	fmt.Println("Agent Response:")
	fmt.Println("============================================================")

	if result != nil {
		fmt.Printf("Task ID: %s\n", result.ID)
		fmt.Printf("State: %s\n", result.Status.State)
		if result.Status.Message != nil {
			printMessageParts(result.Status.Message)
		}
		for _, artifact := range result.Artifacts {
			fmt.Println("--- Artifact ---")
			for _, part := range artifact.Parts {
				printPart(part)
			}
		}
	}

	fmt.Println("============================================================")
}

// sendRESTStreamingMessage sends a streaming message using REST transport
func sendRESTStreamingMessage(ctx context.Context, client *RESTClient, params *a2a.MessageSendParams) {
	log.Println("Sending message (streaming)...")

	fmt.Println("\n============================================================")
	fmt.Println("Agent Response (Streaming):")
	fmt.Println("============================================================")

	for event := range client.SendStreamingMessage(ctx, params) {
		switch e := event.(type) {
		case *a2a.TaskStatusUpdateEvent:
			fmt.Printf("[Status] State: %s", e.Status.State)
			if e.Status.Message != nil {
				fmt.Print(" | ")
				printMessagePartsInline(e.Status.Message)
			}
			fmt.Println()
			if e.Final {
				fmt.Println("[Final event]")
			}
		case error:
			log.Fatalf("Stream error: %v", e)
		default:
			fmt.Printf("[Event] %v\n", event)
		}
	}

	fmt.Println("============================================================")
}

// resolveAgentCard resolves the agent card from URL or default well-known path
func resolveAgentCard(ctx context.Context, host string, port int, cardURL string) (*a2a.AgentCard, error) {
	if cardURL == "" {
		cardURL = fmt.Sprintf("http://%s:%d", host, port)
	}

	log.Printf("Resolving agent card from: %s", cardURL)

	card, err := agentcard.DefaultResolver.Resolve(ctx, cardURL)
	if err != nil {
		return nil, fmt.Errorf("failed to resolve agent card from %s: %w", cardURL, err)
	}

	return card, nil
}

// sendMessage sends a non-streaming message and displays the result
func sendMessage(ctx context.Context, client *a2aclient.Client, params *a2a.MessageSendParams) {
	log.Println("Sending message (non-streaming)...")

	result, err := client.SendMessage(ctx, params)
	if err != nil {
		log.Fatalf("Failed to send message: %v", err)
	}

	fmt.Println("\n============================================================")
	fmt.Println("Agent Response:")
	fmt.Println("============================================================")

	switch r := result.(type) {
	case *a2a.Task:
		fmt.Printf("Task ID: %s\n", r.ID)
		fmt.Printf("State: %s\n", r.Status.State)
		if r.Status.Message != nil {
			printMessageParts(r.Status.Message)
		}
		for _, artifact := range r.Artifacts {
			fmt.Println("--- Artifact ---")
			for _, part := range artifact.Parts {
				printPart(part)
			}
		}
	case *a2a.Message:
		printMessageParts(r)
	default:
		data, _ := json.MarshalIndent(result, "", "  ")
		fmt.Println(string(data))
	}

	fmt.Println("============================================================")
}

// sendStreamingMessage sends a streaming message and displays events as they arrive
func sendStreamingMessage(ctx context.Context, client *a2aclient.Client, params *a2a.MessageSendParams) {
	log.Println("Sending message (streaming)...")

	fmt.Println("\n============================================================")
	fmt.Println("Agent Response (Streaming):")
	fmt.Println("============================================================")

	for event, err := range client.SendStreamingMessage(ctx, params) {
		if err != nil {
			log.Fatalf("Stream error: %v", err)
		}

		switch e := event.(type) {
		case *a2a.TaskStatusUpdateEvent:
			fmt.Printf("[Status] State: %s", e.Status.State)
			if e.Status.Message != nil {
				fmt.Print(" | ")
				printMessagePartsInline(e.Status.Message)
			}
			fmt.Println()
			if e.Final {
				fmt.Println("[Final event]")
			}
		case *a2a.TaskArtifactUpdateEvent:
			fmt.Print("[Artifact] ")
			for _, part := range e.Artifact.Parts {
				printPart(part)
			}
		case *a2a.Message:
			fmt.Print("[Message] ")
			printMessageParts(e)
		default:
			data, _ := json.Marshal(event)
			fmt.Printf("[Event] %s\n", string(data))
		}
	}

	fmt.Println("============================================================")
}

// printMessageParts prints all parts of a message
func printMessageParts(msg *a2a.Message) {
	for _, part := range msg.Parts {
		printPart(part)
	}
}

// printMessagePartsInline prints message parts inline
func printMessagePartsInline(msg *a2a.Message) {
	for _, part := range msg.Parts {
		switch p := part.(type) {
		case a2a.TextPart:
			fmt.Print(p.Text)
		default:
			fmt.Printf("[%T]", p)
		}
	}
}

// printPart prints a single message part
func printPart(part a2a.Part) {
	switch p := part.(type) {
	case a2a.TextPart:
		fmt.Println(p.Text)
	case a2a.FilePart:
		fmt.Printf("[File part]\n")
	case a2a.DataPart:
		data, _ := json.MarshalIndent(p.Data, "", "  ")
		fmt.Printf("[Data: %s]\n", string(data))
	default:
		fmt.Printf("[Unknown part type: %T]\n", p)
	}
}
