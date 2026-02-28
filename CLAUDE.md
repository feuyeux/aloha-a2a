# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Aloha A2A is a multi-language implementation of the [A2A protocol](https://a2a-protocol.org) that standardizes communication between AI agents. The project provides implementations in 5 languages (Python, JavaScript/TypeScript, Java, Go, C#/.NET) with support for three transport modes: gRPC, JSON-RPC, and REST.

## Build and Test Commands

### Python (`aloha-python/`)
```bash
# Install server
cd aloha-python/server && pip install -e .

# Install client
cd aloha-python/client && pip install -e .

# Run server
python -m server

# Run client
python -m client --message "Roll a 6-sided dice"

# Run tests
python -m pytest

# Lint
ruff check .
```

### JavaScript/TypeScript (`aloha-js/`)
```bash
# Install dependencies
cd aloha-js/server && npm ci
cd aloha-js/client && npm ci

# Build
npm run build

# Run server (production)
npm start

# Run server (development with auto-reload)
npm run dev

# Run tests
npm test

# Lint
npm run lint
```

### Java (`aloha-java/`)
```bash
# Build and test
cd aloha-java && mvn test

# Run server
cd aloha-java/server && mvn exec:java

# Run client
cd aloha-java/client && mvn exec:java
```

### Go (`aloha-go/`)
```bash
# Build
cd aloha-go && go build ./...

# Run tests
go test ./...

# Run server
cd aloha-go/server && go run .

# Run client
cd aloha-go/client && go run . --message "Roll a dice"

# Comprehensive integration test
cd aloha-go && ./test.sh
```

### C#/.NET (`aloha-csharp/`)
```bash
# Build
cd aloha-csharp && dotnet build

# Run tests
dotnet test

# Run server
cd aloha-csharp/Server && dotnet run

# Run client
cd aloha-csharp/Client && dotnet run -- --message "Roll a dice"
```

## Architecture

### Multi-Transport Support
Each implementation supports three transport modes configured via environment variables:
- **REST** (HTTP+JSON): Standard RESTful API
- **JSON-RPC**: JSON-RPC 2.0 protocol
- **gRPC**: High-performance binary RPC (not available in C#)

Set `TRANSPORT_MODE=rest|jsonrpc|grpc` to select the transport.

### Port Assignments by Language
| Language  | gRPC | JSON-RPC | REST |
|-----------|------|----------|------|
| Java      | 11000| 11001    | 11002|
| Go        | 12000| 12001    | 12002|
| Python    | 13000| 13001    | 13002|
| JS/TS     | 14000| 14001    | 14002|
| C#        | N/A  | 15001    | 15002|

### Agent Components
Each language implementation follows a similar pattern:
1. **Agent/Server**: Exposes the A2A protocol endpoints
2. **Client/Host**: Connects to agents and sends messages
3. **Agent Card**: Metadata endpoint at `/.well-known/agent-card.json`
4. **Tools**: Two core tools - `roll_dice(sides)` and `check_prime(numbers)`

### LLM Integration
All implementations use **Ollama** with the **qwen2.5** model for LLM capabilities. Prerequisites:
1. Install Ollama from https://ollama.ai
2. Pull the model: `ollama pull qwen2.5`
3. Start service: `ollama serve`

Configure via environment variables:
- `OLLAMA_BASE_URL=http://localhost:11434`
- `OLLAMA_MODEL=qwen2.5`

### Configuration
Each server directory contains a `.env.example` file with configuration options:
- Port settings (`GRPC_PORT`, `JSONRPC_PORT`, `REST_PORT`)
- Ollama settings (`OLLAMA_BASE_URL`, `OLLAMA_MODEL`)
- Agent metadata (`AGENT_NAME`, `AGENT_DESCRIPTION`, `AGENT_VERSION`)

## Cross-Language Interoperability
Clients from one language can communicate with servers from another. The protocol is transport-agnostic - a Python client using REST can talk to a Java server using REST on port 11002.

## SDK Dependencies
Each language uses its respective A2A SDK:
- Python: `a2a-sdk>=0.3.24`
- JavaScript: `@a2a-js/sdk:^0.3.10`
- Java: `io.github.a2asdk:a2a-java-sdk-*:0.3.3.Final`
- Go: `github.com/a2aproject/a2a-go:v0.3.7`
- C#: `A2A:0.3.3-preview`
