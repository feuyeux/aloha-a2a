<!-- markdownlint-disable MD060 -->

# Aloha A2A - Go Implementation

A Go implementation of the A2A (Agent-to-Agent) protocol using the [official A2A Go SDK](https://github.com/a2aproject/a2a-go) v0.3.7 with gRPC, JSON-RPC, and REST transport support.

## Features

- **gRPC Transport**: High-performance binary protocol via `a2agrpc`
- **JSON-RPC 2.0 Transport**: Standard JSON-RPC over HTTP via `a2asrv`
- **REST Transport**: HTTP+JSON based communication via custom adapter
- **Official A2A SDK**: Uses `github.com/a2aproject/a2a-go` v0.3.7
- **LLM Integration**: Uses Ollama with qwen2.5 model via native API
- **Tool Support**: Roll dice and check prime numbers
- **Streaming**: SSE streaming support for all transports

## Port Configuration

| Transport Mode    | Server Port | Agent Card URL                                       |
|:------------------|:------------|:-----------------------------------------------------|
| gRPC              | 12000       | N/A (card via JSON-RPC or REST)                      |
| JSON-RPC 2.0      | 12001       | `http://localhost:12001/.well-known/agent-card.json` |
| REST (HTTP+JSON)  | 12002       | `http://localhost:12002/.well-known/agent-card.json` |

## Prerequisites

- Go 1.24+
- Ollama running locally (`http://localhost:11434`) with the `qwen2.5` model

## Build

```bash
cd aloha-go
go mod tidy
go build ./...
```

## Server

```bash
cd server && go run .
# PowerShell
cd server ; go run .
```

All three transports start simultaneously:

- gRPC: `localhost:12000`
- JSON-RPC: `http://localhost:12001`
- REST: `http://localhost:12002`
- Agent Card: `http://localhost:12001/.well-known/agent-card.json`

## Client

The client uses the A2A SDK with JSON-RPC or gRPC transport.

```bash
cd client && go run . --message "Roll a 20-sided dice"
# PowerShell
cd client ; go run . --message "Roll a 20-sided dice"
```

### Client Options

```bash
# JSON-RPC (default)
go run . --message "Roll a 20-sided dice"

# gRPC transport
go run . --transport grpc --message "Check if 17 is prime"

# With streaming
go run . --transport jsonrpc --message "Check if 17 is prime" --stream

# gRPC with streaming
go run . --transport grpc --message "Roll a 6-sided dice" --stream
```

## Configuration

Configuration via environment variables.

| Variable           | Default                   | Description                     |
|:-------------------|:--------------------------|:--------------------------------|
| `GRPC_PORT`        | `12000`                   | gRPC server port                |
| `JSONRPC_PORT`     | `12001`                   | JSON-RPC server port            |
| `REST_PORT`        | `12002`                   | REST server port                |
| `HOST`             | `0.0.0.0`                 | Bind address                    |
| `OLLAMA_BASE_URL`  | `http://localhost:11434`  | Ollama API base URL             |
| `OLLAMA_MODEL`     | `qwen2.5`                 | Ollama model name               |

## Agent Tools

1. **roll_dice(N)**: Rolls an N-sided dice
   - Example: "Roll a 20-sided dice"
   - Example (Chinese): "投掷一个6面骰子"

2. **check_prime(nums)**: Checks if numbers are prime
   - Example: "Check if 2, 4, 7, 9, 11 are prime"
   - Example (Chinese): "检查17是否为质数"

## References

- [A2A Protocol Specification](https://a2a-protocol.org/latest/specification/)
- [A2A Go SDK](https://github.com/a2aproject/a2a-go) v0.3.7
- [A2A Go SDK API Docs](https://pkg.go.dev/github.com/a2aproject/a2a-go)
