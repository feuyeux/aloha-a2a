# Aloha A2A - Go Implementation

Go implementation of the A2A protocol with agent and host components supporting JSON-RPC 2.0, gRPC, and REST transports.

## Requirements

- Go 1.21 or higher
- Go modules enabled

## Project Structure

```
aloha-go/
├── pkg/
│   └── protocol/       # A2A protocol types and utilities
│       └── types.go
├── agent/              # Agent (server) implementation
│   ├── agent.go        # Multi-transport server
│   ├── executor.go     # Business logic executor
│   ├── tools.go        # Dice and prime tools
│   └── README.md
├── host/               # Host (client) implementation
│   ├── client.go       # Multi-transport client
│   ├── main.go         # CLI interface
│   └── README.md
├── go.mod              # Go module file
├── go.sum              # Go dependencies checksum
├── IMPLEMENTATION.md   # Detailed implementation notes
└── README.md           # This file
```

## Dependencies

- github.com/gin-gonic/gin - HTTP server framework
- github.com/google/uuid - UUID generation
- github.com/gorilla/websocket - WebSocket support
- github.com/joho/godotenv - Environment variables
- google.golang.org/grpc - gRPC support
- google.golang.org/protobuf - Protocol buffers

**Note**: This implementation uses a self-contained protocol package instead of depending on an external A2A SDK, making it independent and easy to maintain.

## Setup

### 1. Install Go 1.21+

```bash
# macOS with Homebrew
brew install go@1.21

# Verify installation
go version
```

### 2. Download Dependencies

```bash
cd aloha-a2a/aloha-go
go mod tidy
go mod download
```

### 3. Build Components

```bash
# Build agent
cd agent
go build -o agent

# Build host
cd ../host
go build -o host
```

### 4. (Optional) Setup Environment Variables

Create a `.env` file in the `aloha-go/` directory for custom configuration:

```env
# Server ports (optional, defaults provided)
GRPC_PORT=12000
JSONRPC_PORT=12001
REST_PORT=12002
HOST=0.0.0.0
```

## Running the Agent

The agent starts three transport servers simultaneously:

```bash
cd agent
./agent
# or
go run .
```

The agent will be available on:
- gRPC: `localhost:12000`
- JSON-RPC 2.0: `ws://localhost:12001`
- REST: `http://localhost:12002`
- Agent Card: `http://localhost:12002/.well-known/agent-card.json`

## Running the Host

### Basic Usage (REST - Default)

```bash
cd host
./host --message "Roll a 20-sided dice"
```

### JSON-RPC 2.0 Transport

```bash
./host --transport jsonrpc --port 12001 --message "Roll a 6-sided dice"
```

### gRPC Transport

```bash
./host --transport grpc --port 12000 --message "Is 17 prime?"
```

### REST Transport (Explicit)

```bash
./host --transport rest --port 12002 --message "Check if 2, 7, 11 are prime"
```

### Streaming Mode

```bash
./host --message "Roll a 12-sided dice and check if it's prime" --stream
```

## Configuration

### Agent Configuration

Environment variables (optional):
- `GRPC_PORT`: gRPC port (default: 12000)
- `JSONRPC_PORT`: JSON-RPC port (default: 12001)
- `REST_PORT`: REST port (default: 12002)
- `HOST`: Bind address (default: 0.0.0.0)

### Host Configuration

Command-line arguments:
- `--transport <jsonrpc|grpc|rest>`: Transport protocol (default: rest)
- `--host <hostname>`: Agent hostname (default: localhost)
- `--port <port>`: Agent port (auto-selected based on transport)
- `--message <text>`: Message to send to the agent (required)
- `--stream`: Enable streaming response (default: false)

## Features

### Agent Tools

1. **roll_dice(N)**: Rolls an N-sided dice
   - Example: "Roll a 20-sided dice"
   
2. **check_prime(nums)**: Checks if numbers are prime
   - Example: "Check if 2, 4, 7, 9, 11 are prime"

### Supported Operations

- ✅ Message sending with streaming responses
- ✅ Task status tracking
- ✅ Agent card discovery
- ✅ Session management (contextID)
- ✅ Multi-transport support (JSON-RPC, gRPC, REST)
- ⚠️ Task cancellation (infrastructure ready, not fully implemented)
- ⚠️ gRPC transport (infrastructure ready, uses placeholder logic)

## Development

### Build

```bash
go build ./...
```

### Run Tests

```bash
go test ./...
```

### Format Code

```bash
go fmt ./...
```

### Lint Code

```bash
# Install golangci-lint
brew install golangci-lint

# Run linter
golangci-lint run
```

## Cross-Language Testing

The Go agent can communicate with hosts written in any supported language:

```bash
# Java host -> Go agent
cd ../aloha-java/host
mvn compile exec:java -Dexec.args="--transport grpc --port 12000 --message 'Roll a dice'"

# Python host -> Go agent
cd ../aloha-python/host
uv run python -m host --transport rest --port 12002 --message "Roll a dice"
```

## Troubleshooting

### Port Already in Use

If ports are already in use, set different ports via environment variables:
```bash
JSONRPC_PORT=12000 GRPC_PORT=12001 REST_PORT=12002 ./agent
```

Or kill the process using the port:
```bash
lsof -ti:12000 | xargs kill -9
```

### Module Download Issues

Clean module cache and re-download:
```bash
go clean -modcache
go mod download
```

### Build Errors

Ensure all dependencies are up to date:
```bash
go mod tidy
go mod download
```

## Documentation

- [IMPLEMENTATION.md](./IMPLEMENTATION.md) - Detailed implementation notes
- [agent/README.md](./agent/README.md) - Agent documentation
- [host/README.md](./host/README.md) - Host documentation

## References

- [A2A Protocol Specification](https://a2a-protocol.org/latest/specification/)
- [Go gRPC Documentation](https://grpc.io/docs/languages/go/)
- [Gin Web Framework](https://gin-gonic.com/)
- [Gorilla WebSocket](https://github.com/gorilla/websocket)
