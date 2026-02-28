<!-- markdownlint-disable MD060 -->

# Aloha A2A - Go Implementation

A Go implementation of the A2A (Agent-to-Agent) protocol using the [official A2A Go SDK](https://github.com/a2aproject/a2a-go) v0.3.7 with gRPC, JSON-RPC, and REST (HTTP+JSON) transport support.

## Features

- **Three Transport Modes**: gRPC, JSON-RPC, and REST (HTTP+JSON)
- **Official A2A SDK**: Uses `github.com/a2aproject/a2a-go` v0.3.7
- **LLM Integration**: Uses Ollama with qwen2.5 model via native API
- **Tool Support**: Roll dice and check prime numbers
- **Streaming**: SSE streaming support for all transports

## Port Configuration

| Transport Mode   | Server Port | Agent Card URL                                       |
|:-----------------|:------------|:-----------------------------------------------------|
| gRPC             | 12000       | `http://localhost:12002/.well-known/agent-card.json` |
| JSON-RPC         | 12001       | `http://localhost:12001/.well-known/agent-card.json` |
| REST (HTTP+JSON)| 12002       | `http://localhost:12002/.well-known/agent-card.json` |

> In gRPC mode, the gRPC service runs on port 12000 while the Agent Card HTTP endpoint runs on REST port (default 12002).

## Prerequisites

- Go 1.24+
- Ollama running locally (`http://localhost:11434`) with the `qwen2.5` model

## Build

```bash
cd aloha-go
go mod tidy
go build ./...
```

Or use the build script:

```bash
# Bash
cd aloha-go && ./scripts/build

# PowerShell
cd aloha-go && ./scripts/build.bat
```

## Quick Start with Scripts

Convenience scripts are provided for running servers and clients:

### Server Scripts

| Script | Transport | Port |
|:-------|:----------|:-----|
| `server/scripts/grpc_server` | gRPC | 12000 |
| `server/scripts/jsonrpc_server` | JSON-RPC | 12001 |
| `server/scripts/rest_server` | REST | 12002 |

```bash
# Bash - Start gRPC server
./server/scripts/grpc_server

# PowerShell - Start REST server
./server/scripts/rest_server.bat
```

### Client Scripts

| Script | Transport | Port |
|:-------|:----------|:-----|
| `client/scripts/grpc_client` | gRPC | 12000 |
| `client/scripts/jsonrpc_client` | JSON-RPC | 12001 |
| `client/scripts/rest_client` | REST | 12002 |

```bash
# Bash - Test with gRPC client
./client/scripts/grpc_client

# PowerShell - Test with REST client
./client/scripts/rest_client.bat
```

> **Note**: For gRPC transport, clients must specify `--card-url` to point to the HTTP endpoint serving the agent card.

## gRPC Transport

### gRPC Server

```bash
# Bash
cd aloha-go/server
TRANSPORT_MODE=grpc go run .

# PowerShell
cd aloha-go/server
$env:TRANSPORT_MODE="grpc"; go run .
```

**Endpoints**:

- gRPC: `localhost:12000`
- Agent Card: `http://localhost:12002/.well-known/agent-card.json`

### gRPC Client

```bash
cd aloha-go/client
go run . --transport grpc --port 12000 --card-url http://localhost:12002 --message "Roll a 20-sided dice"
```

> **Note**: For gRPC transport, you must specify `--card-url` to point to the HTTP endpoint serving the agent card.

## JSON-RPC Transport

### JSON-RPC Server

```bash
# Bash
cd aloha-go/server
TRANSPORT_MODE=jsonrpc go run .

# PowerShell
cd aloha-go/server
$env:TRANSPORT_MODE="jsonrpc"; go run .
```

**Endpoints**:

- JSON-RPC: `http://localhost:12001`
- Agent Card: `http://localhost:12001/.well-known/agent-card.json`

### JSON-RPC Client

```bash
cd aloha-go/client
go run . --transport jsonrpc --port 12001 --message "Check if 17 is prime"
```

## REST Transport

### REST Server

```bash
cd aloha-go/server
go run .
```

All three transports can also start simultaneously (default behavior without TRANSPORT_MODE):

- gRPC: `localhost:12000`
- JSON-RPC: `http://localhost:12001`
- REST: `http://localhost:12002`
- Agent Card: `http://localhost:12001/.well-known/agent-card.json`

### REST Client

```bash
cd aloha-go/client
go run . --transport rest --port 12002 --message "Roll a 20-sided dice"
```

### Client Options

```bash
# JSON-RPC (default)
go run . --message "Roll a 20-sided dice"

# REST transport
go run . --transport rest --port 12002 --message "Roll a 20-sided dice"

# gRPC transport (requires --card-url)
go run . --transport grpc --port 12000 --card-url http://localhost:12002 --message "Check if 17 is prime"

# With streaming
go run . --transport rest --message "Check if 17 is prime" --stream
```

## Configuration

Configuration via environment variables.

| Property           | Default                   | Description                            |
|:-------------------|:--------------------------|:--------------------------------------|
| `TRANSPORT_MODE`   | `jsonrpc`                  | Transport: `grpc`, `jsonrpc`, `rest` |
| `GRPC_PORT`       | `12000`                   | gRPC server port                      |
| `JSONRPC_PORT`    | `12001`                   | JSON-RPC server port                  |
| `REST_PORT`       | `12002`                   | REST server port                      |
| `HOST`            | `0.0.0.0`                | Bind address                          |
| `OLLAMA_BASE_URL` | `http://localhost:11434`  | Ollama API base URL                   |
| `OLLAMA_MODEL`    | `qwen2.5`                 | Ollama model name                     |

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
