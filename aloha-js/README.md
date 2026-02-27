<!-- markdownlint-disable MD060 -->

# Aloha A2A - JavaScript/TypeScript Implementation

A TypeScript implementation of the A2A (Agent-to-Agent) protocol with REST transport support.

## Features

- **REST Transport**: HTTP+JSON based communication
- **TypeScript + Express**: Type-safe server with Express.js
- **LLM Integration**: Uses Ollama with qwen2.5 model via Genkit
- **Tool Support**: Roll dice and check prime numbers
- **A2A SDK**: Uses @anthropic/a2a-js SDK (compatible with A2A Protocol v0.3.x)

## Port Configuration

| Transport Mode    | Server Port | Agent Card URL                                       |
|:------------------|:------------|:-----------------------------------------------------|
| REST (HTTP+JSON)  | 14002       | `http://localhost:14002/.well-known/agent-card.json` |

## Prerequisites

- Node.js 18+
- npm or pnpm
- Ollama running locally (`http://localhost:11434`) with the `qwen2.5` model

## Build

```bash
cd aloha-js

# Server
cd server && npm install && npm run build

# Client
cd ../client && npm install && npm run build
```

## REST Transport

### Server

```bash
cd server && npm start
# PowerShell
cd server ; npm start
```

For development with auto-reload:

```bash
npm run dev
```

**Endpoints**:

- REST: `http://localhost:14002`
- Agent Card: `http://localhost:14002/.well-known/agent-card.json`

### Client

```bash
cd client && npm start -- --message "Roll a 20-sided dice"
# PowerShell
cd client ; npm start -- --message "Roll a 20-sided dice"
```

### Client Options

```bash
# With custom port
npm start -- --port 14002 --message "Roll a 6-sided dice"

# With context ID
npm start -- --context "my-context" --message "Roll a dice"

# Probe transport capabilities
npm start -- --probe
```

## Configuration

Server configuration via environment variables. Copy `.env.example` to `.env` in the `server/` directory.

| Variable           | Default                   | Description                     |
|:-------------------|:--------------------------|:--------------------------------|
| `REST_PORT`        | `14002`                   | REST server port                |
| `HOST`             | `0.0.0.0`                 | Bind address                    |
| `OLLAMA_BASE_URL`  | `http://localhost:11434`  | Ollama API base URL             |
| `OLLAMA_MODEL`     | `qwen2.5`                 | Ollama model name               |
| `AGENT_NAME`       | `Dice Agent`              | Agent display name              |
| `AGENT_VERSION`    | `1.0.0`                   | Agent version                   |

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
