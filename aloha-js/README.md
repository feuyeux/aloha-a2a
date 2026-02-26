# Aloha A2A - JavaScript/TypeScript Implementation

This directory contains the JavaScript/TypeScript implementation of the Aloha A2A (Agent-to-Agent) communication framework with REST transport support.

## Overview

The implementation provides:

- **Server**: A dice rolling agent that can roll arbitrary N-sided dice and check if numbers are prime
- **Client**: A client that communicates with agents via REST transport
- **Transport Support**: REST (implemented), with JSON-RPC and gRPC planned

## Prerequisites

- Node.js >= 18.0.0
- npm or pnpm
- [Ollama](https://ollama.ai/) - Local LLM runtime

## Project Structure

```
aloha-js/
├── server/                # Server implementation
│   ├── src/
│   │   ├── tools.ts      # Dice rolling and prime checking tools
│   │   ├── executor.ts   # Agent executor with LLM integration (Genkit)
│   │   ├── agent.ts      # Main agent with REST transport support
│   │   └── index.ts      # Entry point
│   ├── package.json
│   ├── tsconfig.json
│   └── .env.example
├── client/                # Client implementation
│   ├── src/
│   │   ├── client.ts     # A2A client with REST transport support
│   │   └── index.ts      # CLI entry point
│   ├── package.json
│   └── tsconfig.json
└── README.md
```

## Server Setup

### 1. Install Ollama

Install Ollama for your platform:

```bash
# macOS
brew install ollama

# Linux
curl -fsSL https://ollama.ai/install.sh | sh

# Windows
# Download from https://ollama.ai/download
```

### 2. Pull the qwen2.5 Model

```bash
ollama pull qwen2.5
```

### 3. Start Ollama Service

Ollama typically starts automatically. If not, start it manually:

```bash
ollama serve
```

Verify Ollama is running:

```bash
ollama list
```

### 4. Install Dependencies

```bash
cd server
npm install
```

### 5. Configure Environment

Copy `.env.example` to `.env` and configure:

```bash
cp .env.example .env
```

Edit `.env`:

```env
# Server Ports
GRPC_PORT=14000
JSONRPC_PORT=14001
REST_PORT=14002

# Ollama Configuration
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=qwen2.5

# Agent Metadata
AGENT_NAME=Dice Agent
AGENT_DESCRIPTION=An agent that can roll dice and check prime numbers
AGENT_VERSION=1.0.0
```

### 6. Build

```bash
npm run build
```

### 7. Run the Server

```bash
npm start
```

Or for development with auto-reload:

```bash
npm run dev
```

The server will start and display:

```
============================================================
Dice Agent is running with the following transports:
  - REST:         http://0.0.0.0:14002
  - Agent Card:   http://0.0.0.0:14002/.well-known/agent-card.json

Note: JSON-RPC and gRPC transports require additional SDK support
  - gRPC would be on port: 14000
  - JSON-RPC would be on port: 14001
============================================================
```

## Client Setup

### 1. Install Dependencies

```bash
cd client
npm install
```

### 2. Build

```bash
npm run build
```

### 3. Run the Client

Send a message to the agent:

```bash
npm start -- --transport rest --host localhost --port 14002 --message "Roll a 6-sided dice"
```

Or using the built version:

```bash
node dist/index.js --transport rest --host localhost --port 14002 --message "Roll a 20-sided dice"
```

### Command-Line Options

```
Options:
  -t, --transport <type>  Transport protocol to use (rest) (default: "rest")
  -h, --host <hostname>   Agent hostname (default: "localhost")
  -p, --port <port>       Agent port (default: 14002 for REST)
  -m, --message <text>    Message to send
  --probe                 Probe transport capabilities and exit
  -c, --context <id>      Context ID for conversation continuity
```

## Example Usage

### Start the Server

```bash
cd server
npm install
npm run build
npm start
```

### Send Messages from Client

In another terminal:

```bash
cd client
npm install
npm run build

# Roll a dice
node dist/index.js --message "Roll a 6-sided dice"

# Check prime numbers
node dist/index.js --message "Check if 2, 4, 7, 9, 11 are prime"

# Combined operation
node dist/index.js --message "Roll a 12-sided dice and check if the result is prime"
```

## Transport Protocols

### REST (HTTP+JSON)

The primary transport protocol currently supported by the @a2a-js/sdk.

- **Port**: 14002 (default)
- **Agent Card**: `http://localhost:14002/.well-known/agent-card.json`
- **Endpoints**:
  - `POST /v1/message:send` - Send a message
  - `POST /v1/message:stream` - Send a message with streaming
  - `GET /v1/tasks/{id}` - Get task status
  - `POST /v1/tasks/{id}:cancel` - Cancel a task
  - `GET /v1/card` - Get agent card
  - `GET /v1/transports` - Get transport capability matrix

### Transport Capability Probe

```bash
curl http://localhost:14002/v1/transports
node dist/index.js --transport rest --port 14002 --probe
```

### JSON-RPC 2.0

Support planned for future SDK versions.

- **Port**: 14001 (default)
- **Transport**: WebSocket or HTTP POST

### gRPC

Support planned for future SDK versions.

- **Port**: 14000 (default)
- **Transport**: gRPC

## Agent Capabilities

The Dice Agent provides two main tools:

### 1. Roll Dice

Rolls an N-sided dice and returns a random number between 1 and N.

**Examples:**

- "Roll a 6-sided dice"
- "Roll a 20-sided dice"
- "Roll a d12"

### 2. Check Prime

Checks if the given numbers are prime.

**Examples:**

- "Is 17 prime?"
- "Check if 2, 4, 7, 9, 11 are prime"
- "Are these numbers prime: 13, 15, 19"

### Combined Operations

The agent can perform multiple operations in sequence:

**Example:**

- "Roll a 12-sided dice and check if the result is prime"

## LLM Integration

The agent uses Ollama with the qwen2.5 model for natural language understanding and tool invocation. The LLM interprets user requests and calls the appropriate tools (rollDice, checkPrime) to fulfill the request.

### Supported Models

While qwen2.5 is the default, you can use other Ollama models by setting the OLLAMA_MODEL environment variable:

```bash
export OLLAMA_MODEL=llama3.2
export OLLAMA_MODEL=mistral
export OLLAMA_MODEL=qwen2.5:7b
```

### Fallback Mode

If Ollama is not available or not configured, the agent falls back to simple pattern matching for basic functionality.

## Troubleshooting

### Ollama not responding

- Check if Ollama is running: `ollama list`
- Restart Ollama: `ollama serve`
- Check the base URL in your `.env` file

### Model not found

- Pull the model: `ollama pull qwen2.5`
- List available models: `ollama list`
- Verify the model name in your `.env` file matches an installed model

### Poor response quality

- Try a larger model variant: `ollama pull qwen2.5:14b`
- Adjust the model in your `.env` file: `OLLAMA_MODEL=qwen2.5:14b`

### Connection errors

- Verify Ollama is running on the correct port (default: 11434)
- Check firewall settings if running Ollama on a different machine
- Ensure `OLLAMA_BASE_URL` in `.env` points to the correct address

## Development

### Run Tests

```bash
npm test
```

### Lint Code

```bash
npm run lint
```

### Format Code

```bash
npm run format
```

## Architecture

The implementation follows the A2A Protocol specification and uses the official @a2a-js/sdk.

### Server Architecture

```
┌─────────────────────────────────────┐
│         AlohaServer                  │
├─────────────────────────────────────┤
│  - Agent Card                        │
│  - Task Store                        │
│  - Request Handler                   │
│  - A2A Express App (REST)            │
└─────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────┐
│      DiceA2AExecutor                 │
├─────────────────────────────────────┤
│  - Event Publishing                  │
│  - Task Management                   │
│  - Context Tracking                  │
└─────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────┐
│     DiceAgentExecutor                │
├─────────────────────────────────────┤
│  - LLM Integration (Ollama)          │
│  - Tool Invocation                   │
│  - Fallback Processing               │
└─────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────┐
│          Tools                       │
├─────────────────────────────────────┤
│  - rollDice(sides)                   │
│  - checkPrime(numbers)               │
└─────────────────────────────────────┘
```

### Client Architecture

```
┌─────────────────────────────────────┐
│         CLI Interface                │
├─────────────────────────────────────┤
│  - Command-line parsing              │
│  - Message composition               │
│  - Response formatting               │
└─────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────┐
│          Client                      │
├─────────────────────────────────────┤
│  - A2AClient wrapper                 │
│  - Transport selection               │
│  - Stream handling                   │
│  - Response processing               │
└─────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────┐
│        @a2a-js/sdk                   │
├─────────────────────────────────────┤
│  - A2AClient                         │
│  - Transport implementations         │
│  - Protocol handling                 │
└─────────────────────────────────────┘
```

## Troubleshooting

### Server won't start

1. Check that the ports are not already in use
2. Verify that dependencies are installed: `npm install`
3. Check that the code is built: `npm run build`
4. Review the console output for error messages
5. Ensure Ollama is running: `ollama list`

### Client can't connect to server

1. Verify the server is running
2. Check the host/port configuration
3. Ensure the transport protocol matches (use `rest` for now)
4. Test the agent card endpoint: `curl http://localhost:14002/.well-known/agent-card.json`

## Cross-Language Interoperability

This JavaScript implementation is designed to work with servers and clients implemented in other languages (Java, Python, C#, Go) as long as they follow the A2A protocol specification.

### Testing with Other Languages

```bash
# JavaScript client -> Python server
node dist/index.js --host localhost --port 13002 --message "Roll a dice"

# JavaScript client -> Java server
node dist/index.js --host localhost --port 11002 --message "Roll a dice"
```

## License

Apache-2.0

## Contributing

Contributions are welcome! Please follow the existing code style and add tests for new features.
