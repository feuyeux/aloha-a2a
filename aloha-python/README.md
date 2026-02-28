<!-- markdownlint-disable MD060 -->

# Aloha A2A - Python Implementation

A Python implementation of the A2A (Agent-to-Agent) protocol with support for three transport modes: gRPC, JSON-RPC, and REST (HTTP+JSON).

## Features

- **Three Transport Modes**: gRPC, JSON-RPC, and REST (HTTP+JSON)
- **FastAPI + Uvicorn**: Async HTTP server with automatic OpenAPI docs
- **LLM Integration**: Uses Ollama with qwen2.5 model via native API
- **Tool Support**: Roll dice and check prime numbers
- **A2A SDK**: Uses A2A Python SDK v0.3.24 (compatible with A2A Protocol v0.3.x)

## Port Configuration

| Transport Mode   | Server Port | Agent Card URL                                       |
|:-----------------|:------------|:-----------------------------------------------------|
| gRPC             | 13000       | `http://localhost:13002/.well-known/agent-card.json` |
| JSON-RPC         | 13001       | `http://localhost:13001/.well-known/agent-card.json` |
| REST (HTTP+JSON)| 13002       | `http://localhost:13002/.well-known/agent-card.json` |

> In gRPC mode, the gRPC service runs on port 13000 while the Agent Card HTTP endpoint runs on REST port (default 13002).

## Prerequisites

- Python 3.11+
- uv (recommended) or pip
- Ollama running locally (`http://localhost:11434`) with the `qwen2.5` model

## Build

```bash
cd aloha-python
cd server && uv venv && uv pip install -e .
cd ../client && uv venv && uv pip install -e .
```

Or use the build script:

```bash
# Bash
cd aloha-python && ./scripts/build

# PowerShell
cd aloha-python && ./scripts/build.bat
```

## Quick Start with Scripts

Convenience scripts are provided for running servers and clients:

### Server Scripts

| Script | Transport | Port |
|:-------|:----------|:-----|
| `server/scripts/grpc_server` | gRPC | 13000 |
| `server/scripts/jsonrpc_server` | JSON-RPC | 13001 |
| `server/scripts/rest_server` | REST | 13002 |

```bash
# Bash - Start gRPC server
./server/scripts/grpc_server

# PowerShell - Start REST server
./server/scripts/rest_server.bat
```

### Client Scripts

| Script | Transport | Port |
|:-------|:----------|:-----|
| `client/scripts/grpc_client` | gRPC | 13000 |
| `client/scripts/jsonrpc_client` | JSON-RPC | 13001 |
| `client/scripts/rest_client` | REST | 13002 |

```bash
# Bash - Test with gRPC client
./client/scripts/grpc_client

# PowerShell - Test with REST client
./client/scripts/rest_client.bat
```

## gRPC Transport

### gRPC Server

```bash
# Bash
cd aloha-python
TRANSPORT_MODE=grpc uv run python -m server

# PowerShell
cd aloha-python
$env:TRANSPORT_MODE="grpc"; uv run python -m server
```

**Endpoints**:

- gRPC: `localhost:13000`
- Agent Card: `http://localhost:13002/.well-known/agent-card.json`

### gRPC Client

```bash
cd aloha-python
uv run python -m client --transport grpc --port 13000 --message "Roll a 20-sided dice"
```

## JSON-RPC Transport

### JSON-RPC Server

```bash
# Bash
cd aloha-python
TRANSPORT_MODE=jsonrpc uv run python -m server

# PowerShell
cd aloha-python
$env:TRANSPORT_MODE="jsonrpc"; uv run python -m server
```

**Endpoints**:

- JSON-RPC: `http://localhost:13001`
- Agent Card: `http://localhost:13001/.well-known/agent-card.json`

### JSON-RPC Client

```bash
cd aloha-python
uv run python -m client --transport jsonrpc --port 13001 --message "Check if 17 is prime"
```

## REST Transport

### REST Server

```bash
cd aloha-python
uv run python -m server
```

**Endpoints**:

- REST (HTTP+JSON): `http://localhost:13002`
- Agent Card: `http://localhost:13002/.well-known/agent-card.json`

### REST Client

```bash
cd aloha-python
uv run python -m client --transport rest --port 13002 --message "Roll a 20-sided dice"
```

### Client Options

```bash
# With custom port
uv run python -m client --port 13002 --message "Roll a 6-sided dice"

# Probe transport capabilities
uv run python -m client --probe
```

## Configuration

Server configuration via environment variables. Copy `.env.example` to `.env` in the `server/` directory.

| Property           | Default                   | Description                            |
|:-------------------|:--------------------------|:--------------------------------------|
| `TRANSPORT_MODE`   | `rest`                    | Transport: `grpc`, `jsonrpc`, `rest` |
| `GRPC_PORT`       | `13000`                   | gRPC server port                      |
| `JSONRPC_PORT`    | `13001`                   | JSON-RPC server port                  |
| `REST_PORT`       | `13002`                   | REST server port                      |
| `HOST`            | `0.0.0.0`                 | Bind address                          |
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
- [A2A Python SDK](https://github.com/a2aproject/a2a-python)
- [FastAPI Documentation](https://fastapi.tiangolo.com/)
