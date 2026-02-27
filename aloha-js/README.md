<!-- markdownlint-disable MD060 -->

# Aloha A2A - JavaScript/TypeScript Implementation

A TypeScript implementation of the A2A (Agent-to-Agent) protocol with support for three transport modes: gRPC, JSON-RPC, and REST (HTTP+JSON).

## Features

- **Three Transport Modes**: gRPC, JSON-RPC, and REST (HTTP+JSON)
- **TypeScript + Express**: Type-safe server with Express.js
- **LLM Integration**: Uses Ollama with qwen2.5 model via Genkit
- **Tool Support**: Roll dice and check prime numbers
- **A2A SDK**: Uses @anthropic/a2a-js SDK (compatible with A2A Protocol v0.3.x)

## Port Configuration

| Transport Mode   | Server Port | Agent Card URL                                       |
|:-----------------|:------------|:-----------------------------------------------------|
| gRPC             | 14000       | `http://localhost:14002/.well-known/agent-card.json` |
| JSON-RPC         | 14001       | `http://localhost:14001/.well-known/agent-card.json` |
| REST (HTTP+JSON)| 14002       | `http://localhost:14002/.well-known/agent-card.json` |

> In gRPC mode, the gRPC service runs on port 14000 while the Agent Card HTTP endpoint runs on REST port (default 14002).

## Prerequisites

- Node.js 18+
- npm or pnpm
- Ollama running locally (`http://localhost:11434`) with the `qwen2.5` model

## Build

```bash
cd aloha-js
cd server && npm install && npm run build
cd ../client && npm install && npm run build
```

## gRPC Transport

### gRPC Server

```bash
# Bash
cd aloha-js/server
TRANSPORT_MODE=grpc npm start

# PowerShell
cd aloha-js/server
$env:TRANSPORT_MODE="grpc"; npm start
```

**Endpoints**:

- gRPC: `localhost:14000`
- Agent Card: `http://localhost:14002/.well-known/agent-card.json`

### gRPC Client

```bash
cd aloha-js/client
node dist/index.js --transport grpc --port 14000 --message "Roll a 20-sided dice"
```

## JSON-RPC Transport

### JSON-RPC Server

```bash
# Bash
cd aloha-js/server
TRANSPORT_MODE=jsonrpc npm start

# PowerShell
cd aloha-js/server
$env:TRANSPORT_MODE="jsonrpc"; npm start
```

**Endpoints**:

- JSON-RPC: `http://localhost:14001`
- Agent Card: `http://localhost:14001/.well-known/agent-card.json`

### JSON-RPC Client

```bash
cd aloha-js/client
node dist/index.js --transport jsonrpc --port 14001 --message "Check if 17 is prime"
```

## REST Transport

### REST Server

```bash
cd aloha-js/server
npm start
```

For development with auto-reload:

```bash
cd aloha-js/server
npm run dev
```

**Endpoints**:

- REST (HTTP+JSON): `http://localhost:14002`
- Agent Card: `http://localhost:14002/.well-known/agent-card.json`

### REST Client

```bash
cd aloha-js/client
node dist/index.js --transport rest --port 14002 --message "Roll a 20-sided dice"
```

### Client Options

```bash
# With custom port
cd aloha-js/client
node dist/index.js --port 14002 --message "Roll a 6-sided dice"

# With context ID
cd aloha-js/client
node dist/index.js --context "my-context" --message "Roll a dice"

# Probe transport capabilities
cd aloha-js/client
node dist/index.js --probe
```

## Configuration

Server configuration via environment variables. Copy `.env.example` to `.env` in the `server/` directory.

| Property           | Default                   | Description                            |
|:-------------------|:--------------------------|:--------------------------------------|
| `TRANSPORT_MODE`   | `rest`                    | Transport: `grpc`, `jsonrpc`, `rest` |
| `GRPC_PORT`       | `14000`                   | gRPC server port                      |
| `JSONRPC_PORT`    | `14001`                   | JSON-RPC server port                  |
| `REST_PORT`       | `14002`                   | REST server port                      |
| `HOST`            | `0.0.0.0`                | Bind address                          |
| `OLLAMA_BASE_URL` | `http://localhost:11434`  | Ollama API base URL                   |
| `OLLAMA_MODEL`    | `qwen2.5`                 | Ollama model name                     |
| `AGENT_NAME`      | `Dice Agent`              | Agent display name                     |
| `AGENT_VERSION`   | `1.0.0`                   | Agent version                         |

## Agent Tools

1. **roll_dice(N)**: Rolls an N-sided dice
   - Example: "Roll a 20-sided dice"
   - Example (Chinese): "投掷一个6面骰子"

2. **check_prime(nums)**: Checks if numbers are prime
   - Example: "Check if 2, 4, 7, 9, 11 are prime"
   - Example (Chinese): "检查17是否为质数"

## References

- [A2A Protocol Specification](https://a2a-protocol.org/latest/specification/)
- [A2A JavaScript SDK](https://github.com/anthropics/anthropic-sdk-typescript)
- [Genkit Documentation](https://firebase.google.com/docs/genkit)
