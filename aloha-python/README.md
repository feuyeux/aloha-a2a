<!-- markdownlint-disable MD060 -->

# Aloha A2A - Python Implementation

A Python implementation of the A2A (Agent-to-Agent) protocol with REST transport support.

## Features

- **REST Transport**: HTTP+JSON based communication
- **FastAPI + Uvicorn**: Async HTTP server with automatic OpenAPI docs
- **LLM Integration**: Uses Ollama with qwen2.5 model via native API
- **Tool Support**: Roll dice and check prime numbers
- **A2A SDK**: Uses A2A Python SDK v0.3.10 (compatible with A2A Protocol v0.3.x)

## Port Configuration

| Transport Mode    | Server Port | Agent Card URL                                       |
|:------------------|:------------|:-----------------------------------------------------|
| REST (HTTP+JSON)  | 13002       | `http://localhost:13002/.well-known/agent-card.json` |

## Prerequisites

- Python 3.11+
- uv (recommended) or pip
- Ollama running locally (`http://localhost:11434`) with the `qwen2.5` model

## Build

```bash
cd aloha-python

# Using uv (recommended)
cd server && uv venv && source .venv/bin/activate && uv pip install -e .

# Using pip
cd server && python -m venv .venv && source .venv/bin/activate && pip install -e .
```

## REST Transport

### Server

```bash
cd server && uv run python -m server
# PowerShell
cd server ; uv run python -m server
```

Or with virtual environment:

```bash
cd server && source .venv/bin/activate && python -m server
# PowerShell
cd server ; .\.venv\Scripts\activate ; python -m server
```

**Endpoints**:

- REST: `http://localhost:13002`
- Agent Card: `http://localhost:13002/.well-known/agent-card.json`

### Client

```bash
cd client && uv run python __main__.py --message "Roll a 20-sided dice"
# PowerShell
cd client ; uv run python __main__.py --message "Roll a 20-sided dice"
```

### Client Options

```bash
# With custom port
uv run python __main__.py --port 13002 --message "Roll a 6-sided dice"

# Probe transport capabilities
uv run python __main__.py --probe
```

## Configuration

Server configuration via environment variables. Copy `.env.example` to `.env` in the `server/` directory.

| Variable           | Default                   | Description                     |
|:-------------------|:--------------------------|:--------------------------------|
| `REST_PORT`        | `13002`                   | REST server port                |
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
- [A2A Python SDK](https://github.com/a2aproject/a2a-python)
- [FastAPI Documentation](https://fastapi.tiangolo.com/)
