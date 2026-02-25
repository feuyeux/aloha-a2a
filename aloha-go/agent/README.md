# Go Dice Agent

A multi-transport A2A agent implementation in Go that can roll dice and check prime numbers.

## Features

- **Multi-Transport Support**: JSON-RPC 2.0, gRPC, and REST
- **Streaming Responses**: Real-time event streaming
- **Agent Card**: Discoverable capabilities at `/.well-known/agent-card.json`
- **Tools**:
  - `roll_dice`: Roll an N-sided dice
  - `check_prime`: Check if numbers are prime

## Prerequisites

- Go 1.21 or higher
- [Ollama](https://ollama.ai/) - Local LLM runtime
- Dependencies managed via `go.mod`

## Installation

### 1. Install Ollama

```bash
# macOS
brew install ollama

# Linux
curl -fsSL https://ollama.ai/install.sh | sh

# Windows
# Download from https://ollama.ai/download
```

### 2. Pull the qwen2.5 model

```bash
ollama pull qwen2.5
```

### 3. Start Ollama service (if not auto-started)

```bash
ollama serve
```

### 4. Install Go dependencies

```bash
cd aloha-a2a/aloha-go
go mod download
```

## Configuration

Configure the agent using environment variables:

```bash
# Server Ports
export JSONRPC_PORT=11000  # JSON-RPC WebSocket port
export GRPC_PORT=11001     # gRPC port
export REST_PORT=11002     # REST HTTP port
export HOST=0.0.0.0        # Bind address

# Ollama Configuration
export OLLAMA_BASE_URL=http://localhost:11434
export OLLAMA_MODEL=qwen2.5
```

Or create a `.env` file (see `.env.example`).

## Running the Agent

```bash
cd agent
go run .
```

The agent will start and listen on all three transports:

- JSON-RPC 2.0: `ws://localhost:11000`
- gRPC: `localhost:11001`
- REST: `http://localhost:11002`

## Agent Card

Fetch the agent card to discover capabilities:

```bash
curl http://localhost:11002/.well-known/agent-card.json
```

## Example Requests

### REST API

```bash
# Send a message
curl -X POST http://localhost:11002/v1/message:send \
  -H "Content-Type: application/json" \
  -d '{
    "kind": "message",
    "role": "user",
    "parts": [{"kind": "text", "text": "Roll a 20-sided dice"}]
  }'

# Probe transport capabilities
curl http://localhost:11002/v1/transports
```

### JSON-RPC 2.0

Connect via WebSocket and send:

```json
{
  "jsonrpc": "2.0",
  "method": "message/send",
  "params": {
    "message": {
      "kind": "message",
      "role": "user",
      "parts": [{"kind": "text", "text": "Check if 17 is prime"}]
    }
  },
  "id": 1
}
```

## Building

```bash
go build -o dice-agent
./dice-agent
```

## LLM Integration

This agent uses Ollama with the qwen2.5 model for natural language understanding and tool invocation. The LLM interprets user requests and calls the appropriate tools (roll_dice, check_prime) to fulfill the request.

### Supported Models

While qwen2.5 is the default, you can use other Ollama models by setting the OLLAMA_MODEL environment variable:

```bash
export OLLAMA_MODEL=llama3.2
export OLLAMA_MODEL=mistral
export OLLAMA_MODEL=qwen2.5:7b
```

### Fallback Behavior

If Ollama is not available, the agent will automatically fall back to simple pattern matching for basic dice rolling and prime checking requests. However, for best results and full natural language understanding, Ollama should be running.

## Troubleshooting

### Ollama not responding

- Check if Ollama is running: `ollama list`
- Restart Ollama: `ollama serve`
- Check logs for errors

### Model not found

- Pull the model: `ollama pull qwen2.5`
- List available models: `ollama list`

### Poor response quality

- Try a larger model variant: `ollama pull qwen2.5:14b`
- Ensure Ollama has sufficient resources (RAM/GPU)

### Connection refused

- Verify Ollama is running on the correct port (default: 11434)
- Check OLLAMA_BASE_URL environment variable
- Test connection: `curl http://localhost:11434/api/tags`

## Architecture

- `agent.go`: Main agent server with multi-transport support
- `executor.go`: Request processing, LLM integration, and business logic
- `tools.go`: Dice rolling and prime checking tools
