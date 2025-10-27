# Go Implementation - A2A Multi-Language Framework

## Overview

This directory contains the Go implementation of the A2A (Agent-to-Agent) communication framework, including both agent (server) and host (client) components with multi-transport support.

## Architecture

### Module Structure

```
aloha-a2a/aloha-go/
├── pkg/
│   └── protocol/          # A2A protocol types and utilities
│       └── types.go       # Message, Task, Event, AgentCard definitions
├── agent/                 # Agent (server) implementation
│   ├── agent.go          # Multi-transport server
│   ├── executor.go       # Business logic executor
│   ├── tools.go          # Dice rolling and prime checking tools
│   └── README.md         # Agent documentation
├── host/                  # Host (client) implementation
│   ├── client.go         # Multi-transport client
│   ├── main.go           # CLI interface
│   └── README.md         # Host documentation
├── go.mod                # Go module definition
└── README.md             # Main documentation
```

### Key Design Decisions

1. **Self-Contained Protocol Package**: Instead of depending on an external A2A SDK, we implemented a local `pkg/protocol` package with all necessary types. This makes the implementation:
   - Independent and maintainable
   - Easy to customize
   - Free from external SDK version constraints

2. **No Relative Paths**: The `go.mod` uses absolute module paths (`github.com/aloha/a2a-go`) to ensure the project can be maintained independently without relative path dependencies.

3. **Unified Module**: Both agent and host share the same Go module, allowing them to share the protocol package efficiently.

## Protocol Package

### Core Types

The `pkg/protocol/types.go` file defines all A2A protocol types:

- **Message**: User and agent messages with parts
- **Task**: Asynchronous task representation
- **TaskStatus**: Task state and metadata
- **Event**: Status update events
- **TaskStatusUpdateEvent**: Specific event type for status updates
- **AgentCard**: Agent capability description
- **Capability**: Agent feature flags
- **Skill**: Agent skill definitions

### Constants

```go
const (
    TaskStateSubmitted = "submitted"
    TaskStateWorking   = "working"
    TaskStateCompleted = "completed"
    TaskStateFailed    = "failed"
    TaskStateCanceled  = "canceled"
)
```

### Utility Functions

- `NewUUID()`: Generates RFC 4122 compliant UUIDs
- `Now()`: Returns ISO8601 formatted timestamps

## Agent Implementation

### Features

- **Multi-Transport Support**: JSON-RPC 2.0 (WebSocket), gRPC, and REST (HTTP+JSON)
- **Concurrent Handling**: Each transport runs in its own goroutine
- **Agent Card Service**: Serves capabilities at `/.well-known/agent-card.json`
- **Streaming Responses**: Real-time event streaming to clients
- **Graceful Shutdown**: Handles SIGINT and SIGTERM signals

### Transport Ports

- JSON-RPC 2.0: `11000` (default)
- gRPC: `11001` (default)
- REST: `11002` (default)

### Tools

1. **roll_dice(N)**: Rolls an N-sided dice
2. **check_prime(numbers)**: Checks if numbers are prime

### Running the Agent

```bash
cd aloha-a2a/aloha-go/agent
go build -o agent
./agent
```

Environment variables:
- `JSONRPC_PORT`: JSON-RPC port (default: 11000)
- `GRPC_PORT`: gRPC port (default: 11001)
- `REST_PORT`: REST port (default: 11002)
- `HOST`: Bind address (default: 0.0.0.0)

## Host Implementation

### Features

- **Multi-Transport Support**: JSON-RPC 2.0, gRPC, and REST
- **Streaming Mode**: Real-time event streaming from agents
- **Session Management**: Maintains conversation context (contextID)
- **Agent Card Discovery**: Automatic capability fetching
- **Command-Line Interface**: Easy-to-use CLI

### Running the Host

```bash
cd aloha-a2a/aloha-go/host
go build -o host

# Basic usage (REST)
./host --message "Roll a 20-sided dice"

# JSON-RPC
./host --transport jsonrpc --port 11000 --message "Is 17 prime?"

# gRPC
./host --transport grpc --port 11001 --message "Check if 2, 7, 11 are prime"

# Streaming mode
./host --message "Roll a 6-sided dice" --stream
```

### Command-Line Options

| Option | Description | Default |
|--------|-------------|---------|
| `--transport` | Transport protocol (jsonrpc, grpc, rest) | `rest` |
| `--host` | Agent hostname | `localhost` |
| `--port` | Agent port | Auto-selected |
| `--message` | Message to send | Required |
| `--stream` | Enable streaming | `false` |

## Dependencies

### Core Dependencies

```go
require (
    github.com/gin-gonic/gin v1.10.0        // HTTP server framework
    github.com/google/uuid v1.6.0           // UUID generation
    github.com/gorilla/websocket v1.5.3     // WebSocket support
    github.com/joho/godotenv v1.5.1         // Environment variables
    google.golang.org/grpc v1.68.1          // gRPC support
    google.golang.org/protobuf v1.35.2      // Protocol buffers
)
```

### Why These Dependencies?

- **gin**: Lightweight, fast HTTP framework for REST endpoints
- **uuid**: Standard UUID generation for message and task IDs
- **websocket**: WebSocket support for JSON-RPC 2.0 transport
- **godotenv**: Easy environment variable management
- **grpc**: Official gRPC implementation for Go
- **protobuf**: Required by gRPC

## Building

### Build Both Components

```bash
# From the aloha-go directory
cd aloha-a2a/aloha-go

# Build agent
cd agent && go build -o agent && cd ..

# Build host
cd host && go build -o host && cd ..
```

### Update Dependencies

```bash
cd aloha-a2a/aloha-go
go mod tidy
```

## Testing

### Manual Testing

1. Start the agent:
```bash
cd aloha-a2a/aloha-go/agent
./agent
```

2. In another terminal, run the host:
```bash
cd aloha-a2a/aloha-go/host
./host --message "Roll a 20-sided dice"
```

### Cross-Language Testing

The Go implementation can communicate with agents written in other languages:

```bash
# Go host -> Python agent
./host --transport rest --port 11002 --message "Roll a dice"

# Go host -> Java agent
./host --transport grpc --port 11001 --message "Is 17 prime?"

# Go host -> JavaScript agent
./host --transport jsonrpc --port 11000 --message "Check if 2, 7, 11 are prime"
```

## Implementation Notes

### gRPC Support

The current implementation includes gRPC infrastructure but uses placeholder logic. Full gRPC support would require:
1. Implementing the A2A gRPC service definition
2. Generating Go code from `.proto` files
3. Implementing server and client stubs

For now, the gRPC transport is configured but returns placeholder responses.

### JSON-RPC 2.0

The JSON-RPC implementation uses WebSocket for bidirectional communication:
- Client connects via `ws://host:port/`
- Messages follow JSON-RPC 2.0 specification
- Supports both `message/send` and `message/stream` methods

### REST Transport

The REST implementation uses standard HTTP:
- POST `/v1/message:send` - Send a message
- POST `/v1/message:stream` - Stream a message
- GET `/.well-known/agent-card.json` - Get agent card

## Error Handling

### Agent Errors

- Invalid JSON: Returns JSON-RPC error -32700 (Parse error)
- Invalid request: Returns JSON-RPC error -32600 (Invalid Request)
- Method not found: Returns JSON-RPC error -32601 (Method not found)
- Invalid params: Returns JSON-RPC error -32602 (Invalid params)
- Internal error: Returns JSON-RPC error -32603 (Internal error)

### Host Errors

- Connection failures: Logged with descriptive messages
- Timeout errors: Context deadline exceeded
- Invalid responses: Parsing errors logged
- Transport errors: Transport-specific error messages

## Performance Considerations

1. **Concurrent Transports**: All three transports run concurrently without blocking
2. **Goroutine-Based**: Each connection handled in a separate goroutine
3. **Channel-Based Events**: Event streaming uses Go channels for efficiency
4. **Connection Pooling**: HTTP client reuses connections

## Security Considerations

### Current Implementation

- No authentication (suitable for development/testing)
- No TLS/SSL (HTTP only)
- No rate limiting
- No input validation beyond basic parsing

### Production Recommendations

1. Add TLS support for all transports
2. Implement authentication (bearer tokens, API keys)
3. Add rate limiting per client
4. Validate and sanitize all inputs
5. Add request logging and monitoring
6. Implement proper error handling without leaking internals

## Future Enhancements

1. **Full gRPC Implementation**: Complete gRPC service with proper proto definitions
2. **LLM Integration**: Add real LLM support (OpenAI, Anthropic, local models)
3. **Persistent Sessions**: Store conversation history in database
4. **Metrics and Monitoring**: Add Prometheus metrics
5. **Health Checks**: Implement health check endpoints
6. **Configuration Management**: Support for config files (YAML/JSON)
7. **Docker Support**: Add Dockerfile and docker-compose
8. **Unit Tests**: Comprehensive test coverage
9. **Integration Tests**: Automated cross-language testing

## Troubleshooting

### Build Errors

**Problem**: `cannot find package`
```bash
# Solution: Update dependencies
go mod tidy
```

**Problem**: `undefined: protocol.X`
```bash
# Solution: Ensure protocol package is up to date
cd aloha-a2a/aloha-go
go build ./...
```

### Runtime Errors

**Problem**: `bind: address already in use`
```bash
# Solution: Change port or kill existing process
lsof -ti:11000 | xargs kill -9
```

**Problem**: `connection refused`
```bash
# Solution: Ensure agent is running
curl http://localhost:11002/.well-known/agent-card.json
```

## Contributing

When contributing to the Go implementation:

1. Follow Go conventions and idioms
2. Run `go fmt` before committing
3. Add tests for new functionality
4. Update documentation
5. Ensure cross-language compatibility

## License

See the main project LICENSE file.
