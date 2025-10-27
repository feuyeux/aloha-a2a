package main

import (
	"context"
	"flag"
	"fmt"
	"log"
	"os"
	"time"
)

func main() {
	// Parse command-line flags
	transport := flag.String("transport", "rest", "Transport protocol to use (jsonrpc, grpc, rest)")
	host := flag.String("host", "localhost", "Agent hostname")
	port := flag.Int("port", 0, "Agent port (default: 11000 for JSON-RPC, 11001 for gRPC, 11002 for REST)")
	message := flag.String("message", "", "Message to send to the agent")
	stream := flag.Bool("stream", false, "Enable streaming response")
	
	flag.Parse()
	
	// Validate message
	if *message == "" {
		fmt.Println("Usage: host --transport <jsonrpc|grpc|rest> --host <hostname> --port <port> --message <text> [--stream]")
		fmt.Println("\nOptions:")
		fmt.Println("  --transport  Transport protocol (jsonrpc, grpc, rest) [default: rest]")
		fmt.Println("  --host       Agent hostname [default: localhost]")
		fmt.Println("  --port       Agent port [default: 12000 for gRPC, 12001 for JSON-RPC, 12002 for REST]")
		fmt.Println("  --message    Message to send to the agent [required]")
		fmt.Println("  --stream     Enable streaming response [default: false]")
		fmt.Println("\nExamples:")
		fmt.Println("  # Send message using REST (default)")
		fmt.Println("  host --message \"Roll a 20-sided dice\"")
		fmt.Println("")
		fmt.Println("  # Send message using JSON-RPC")
		fmt.Println("  host --transport jsonrpc --port 12001 --message \"Is 17 prime?\"")
		fmt.Println("")
		fmt.Println("  # Send message using gRPC with streaming")
		fmt.Println("  host --transport grpc --port 12000 --message \"Check if 2, 7, 11 are prime\" --stream")
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
			log.Fatalf("Unsupported transport: %s", *transport)
		}
	}
	
	// Construct server URL
	serverURL := fmt.Sprintf("http://%s:%d", *host, *port)
	
	log.Println("============================================================")
	log.Println("A2A Host Client")
	log.Printf("  Server: %s", serverURL)
	log.Printf("  Transport: %s", *transport)
	log.Printf("  Streaming: %v", *stream)
	log.Printf("  Message: %s", *message)
	log.Println("============================================================")
	
	// Create client
	client := NewClient(serverURL, *transport)
	
	// Create context with timeout
	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()
	
	// Initialize client
	if err := client.Initialize(ctx); err != nil {
		log.Fatalf("Failed to initialize client: %v", err)
	}
	
	// Send message
	if *stream {
		// Streaming mode
		responseChan, errorChan := client.SendMessageStream(ctx, *message)
		
		fmt.Println("\n============================================================")
		fmt.Println("Agent Response (Streaming):")
		fmt.Println("============================================================")
		
		for {
			select {
			case response, ok := <-responseChan:
				if !ok {
					// Channel closed, streaming complete
					fmt.Println("============================================================")
					goto cleanup
				}
				fmt.Println(response)
			case err := <-errorChan:
				if err != nil {
					log.Fatalf("Failed to receive streaming response: %v", err)
				}
			case <-ctx.Done():
				log.Fatalf("Context timeout: %v", ctx.Err())
			}
		}
	} else {
		// Non-streaming mode
		response, err := client.SendMessage(ctx, *message)
		if err != nil {
			log.Fatalf("Failed to send message: %v", err)
		}
		
		// Display response
		fmt.Println("\n============================================================")
		fmt.Println("Agent Response:")
		fmt.Println("============================================================")
		fmt.Println(response)
		fmt.Println("============================================================")
	}
	
cleanup:
	// Clean up
	if err := client.Close(); err != nil {
		log.Printf("Error during cleanup: %v", err)
	}
}
