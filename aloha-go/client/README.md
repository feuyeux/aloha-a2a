# A2A Go Client

A Go implementation of an A2A (Agent-to-Agent) client with multi-transport support.

## Features

- **Multi-Transport Support**: JSON-RPC 2.0, gRPC, and REST
- **Streaming Responses**: Real-time event streaming from agents
- **Session Management**: Maintains conversation context across multiple messages
- **Agent Card Discovery**: Automatically fetches agent capabilities
- **Command-Line Interface**: Easy-to-use CLI for testing agents

## Prerequisites

- Go 1.21 or higher
- Running A2A agent (any language implementation)

## Installation

```bash
cd aloha-a2a/aloha-go/client
go build -o client
```

## Usage

### Basic Usage

Send a message to an agent using the default REST transport:

```bash
./client --message "Roll a 20-sided dice"
```

### Transport Selection

#### JSON-RPC 2.0 (WebSocket)

```bash
./client --transport jsonrpc --port 11000 --message "Roll a 6-sided dice"
```

#### gRPC

```bash
./client --transport grpc --port 11001 --message "Is 17 prime?"
```

#### REST (HTTP+JSON)

```bash
./client --transport rest --port 11002 --message "Check if 2, 7, 11 are prime"
```

### Streaming Mode

Enable streaming to receive real-time updates:

```bash
./client --transport rest --message "Roll a 20-sided dice" --stream
```

### Custom Host and Port

Connect to a remote agent:

```bash
./client --host agent.example.com --port 8080 --message "Roll a 12-sided dice"
```

## Command-Line Options

| Option | Description | Default |
|--------|-------------|---------|
| `--transport` | Transport protocol (jsonrpc, grpc, rest) | `rest` |
| `--host` | Agent hostname | `localhost` |
| `--port` | Agent port | Auto-selected based on transport |
| `--message` | Message to send to the agent | Required |
| `--stream` | Enable streaming response | `false` |

## Default Ports

- JSON-RPC 2.0: `11000`
- gRPC: `11001`
- REST: `11002`

## Examples

### Example 1: Roll a Dice

```bash
./client --message "Roll a 20-sided dice"
```

Output:

```
============================================================
A2A Client
  Server: http://localhost:11002
  Transport: rest
  Streaming: false
  Message: Roll a 20-sided dice
============================================================
Connecting to agent at: http://localhost:11002
Successfully fetched agent card:
  Name: Dice Agent
  Description: An agent that can roll arbitrary dice and check prime numbers
  Version: 1.0.0
Using REST transport
Client initialized successfully
Sending message: Roll a 20-sided dice
Context ID: 123e4567-e89b-12d3-a456-426614174000

============================================================
Agent Response:
============================================================
I rolled a 20-sided dice and got: 17
============================================================
```

### Example 2: Check Prime Numbers

```bash
./client --transport jsonrpc --port 11000 --message "Check if 2, 7, 11, 15 are prime"
```

### Example 3: Streaming Response

```bash
./client --message "Roll a 6-sided dice and check if it's prime" --stream
```

## Session Management

The client automatically maintains a session context (contextID) across the lifetime of the client instance. This allows agents to maintain conversation history if they support it.

Each message sent by the client includes:

- A unique `messageId`
- A shared `contextId` for the session
- The user's message text

## Agent Card Discovery

On initialization, the client automatically fetches the agent card from:

```
http://<host>:<port>/.well-known/agent-card.json
```

The agent card provides:

- Agent name and description
- Supported capabilities
- Available skills
- Preferred transport protocol

## Error Handling

The client handles various error scenarios:

- Connection failures
- Invalid responses
- Timeout errors
- Transport-specific errors

All errors are logged with descriptive messages.

## Architecture

```
┌─────────────────────────────────────┐
│         Client                       │
├─────────────────────────────────────┤
│                                      │
│  ┌────────────────────────────┐    │
│  │  Command-Line Interface     │    │
│  └────────────┬───────────────┘    │
│               │                     │
│  ┌────────────▼───────────────┐    │
│  │  Transport Selector         │    │
│  └────────────┬───────────────┘    │
│               │                     │
│       ┌───────┴───────┐            │
│       │       │       │             │
│  ┌────▼──┐ ┌─▼────┐ ┌▼─────┐      │
│  │JSON-  │ │gRPC  │ │REST  │      │
│  │RPC    │ │Client│ │Client│      │
│  │Client │ └──────┘ └──────┘      │
│  └───────┘                         │
│                                     │
└─────────────────────────────────────┘
```

## Development

### Building

```bash
go build -o client
```

### Running Tests

```bash
go test ./...
```

### Code Structure

- `main.go`: Entry point and CLI handling
- `client.go`: Client implementation with multi-transport support

## Cross-Language Compatibility

This Go client can communicate with servers implemented in any language:

- Java
- Python
- JavaScript/TypeScript
- C#
- Go

All implementations follow the A2A protocol specification for interoperability.

## Troubleshooting

### Connection Refused

Ensure the agent is running on the specified host and port:

```bash
# Check if agent is listening
curl http://localhost:11002/.well-known/agent-card.json
```

### WebSocket Connection Failed

For JSON-RPC transport, ensure the agent supports WebSocket connections on the specified port.

### gRPC Connection Failed

Verify the agent's gRPC server is running and accessible on the specified port.

## License

See the main project LICENSE file.
