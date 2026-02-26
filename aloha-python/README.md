# Python A2A Implementation

This directory contains Python implementations of both A2A server and client with REST-first support.

## Features

- **Transport Support**: REST (implemented); JSON-RPC/gRPC are not yet fully wired in this host
- **Streaming Responses**: Real-time event streaming
- **LLM Integration**: Ollama with qwen2.5 model
- **Tool Support**: Dice rolling and prime number checking

## Prerequisites

- Python 3.11 or higher
- A2A SDK v0.3.10
- [Ollama](https://ollama.ai/) - Local LLM runtime

## Ollama Setup

Before running the server, you need to install and configure Ollama:

### 1. Install Ollama

```bash
# macOS
brew install ollama

# Linux
curl -fsSL https://ollama.ai/install.sh | sh

# Windows
# Download from https://ollama.ai/download
```

### 2. Pull the qwen2.5 model

```bash
ollama pull qwen2.5
```

### 3. Start Ollama service (if not auto-started)

```bash
ollama serve
```

### 4. Verify Ollama is running

```bash
# List available models
ollama list

# Test the connection
curl http://localhost:11434/api/tags
```

## Installation

### Using uv (recommended)

```bash
# Install server dependencies
cd server
uv venv
source .venv/bin/activate
uv pip install -e .

# Install client dependencies (optional)
cd ../client
uv venv
source .venv/bin/activate
uv pip install -e .
```

### Using pip

```bash
# Install server dependencies
cd server
python -m venv .venv
source .venv/bin/activate
pip install -e .

# Install client dependencies (optional)
cd ../client
python -m venv .venv
source .venv/bin/activate
pip install -e .
```

## Running the Server

The server runs on port 13002 with REST transport (default).

### Start the server

```bash
cd server
uv run python -m server
```

Or with virtual environment:

```bash
cd server
source .venv/bin/activate
python -m server
```

### With custom port

```bash
export REST_PORT=12002
uv run python -m server
```

### Agent Card

The agent card is available at:

```
http://localhost:13002/.well-known/agent-card.json
```

### Quick Test

```bash
# From project root
./test_python_e2e.sh
```

## Running the Client

The client is a command-line client that connects to agents using REST transport.

### Basic usage

```bash
cd client
uv run python __main__.py --message "Roll a 20-sided dice"
```

Or with virtual environment:

```bash
cd client
source .venv/bin/activate
python __main__.py --message "Roll a 20-sided dice"
```

### All options

```bash
uv run python __main__.py --help
```

Options:

- `--host`: Agent hostname (default: localhost)
- `--port`: Agent port (default: 13002 for REST, 13001 for JSON-RPC, 13000 for gRPC)
- `--transport`: Transport protocol (default: rest)
- `--message`: Message to send (default: "Roll a 6-sided dice")
- `--probe`: Query `GET /v1/transports` and print capability matrix

### Transport Capability Probe

```bash
curl http://localhost:13002/v1/transports
uv run python __main__.py --transport rest --port 13002 --probe
```

**Note**: The client currently has some compatibility issues with the A2A SDK. For testing, use the provided test script or direct API calls.

## Examples

### Roll a dice

```bash
cd server
uv run python -c "
import asyncio
import os
os.environ['OLLAMA_BASE_URL'] = 'http://localhost:11434'
os.environ['OLLAMA_MODEL'] = 'qwen2.5'
from agent_executor import DiceAgentExecutor
async def test():
    executor = DiceAgentExecutor()
    response = await executor._process_with_llm('Roll a 20-sided dice')
    print(response)
asyncio.run(test())
"
```

### Check prime numbers

```bash
cd server
uv run python -c "
import asyncio
import os
os.environ['OLLAMA_BASE_URL'] = 'http://localhost:11434'
os.environ['OLLAMA_MODEL'] = 'qwen2.5'
from agent_executor import DiceAgentExecutor
async def test():
    executor = DiceAgentExecutor()
    response = await executor._process_with_llm('Check if 2, 4, 7, 9, 11 are prime')
    print(response)
asyncio.run(test())
"
```

### Roll and check if prime (Chinese)

```bash
cd server
uv run python -c "
import asyncio
import os
os.environ['OLLAMA_BASE_URL'] = 'http://localhost:11434'
os.environ['OLLAMA_MODEL'] = 'qwen2.5'
from agent_executor import DiceAgentExecutor
async def test():
    executor = DiceAgentExecutor()
    response = await executor._process_with_llm('投掷一个12面骰子并检查结果是否为质数')
    print(response)
asyncio.run(test())
"
```

## Architecture

### Server Structure

```
server/
├── agent.py              # Main agent with multi-transport setup
├── agent_executor.py     # Agent executor with LLM integration
├── tools.py              # Tool implementations (roll_dice, check_prime)
├── __main__.py           # Entry point
└── pyproject.toml        # Dependencies
```

### Client Structure

```
client/
├── client.py             # A2A client with multi-transport support
├── __main__.py           # CLI entry point
└── pyproject.toml        # Dependencies
```

## LLM Integration

This agent uses Ollama with the qwen2.5 model for natural language understanding and tool invocation. The LLM interprets user requests and calls the appropriate tools (roll_dice, check_prime) to fulfill the request.

### Configuration

The server reads Ollama configuration from environment variables:

```bash
# Copy the example configuration
cp .env.example .env

# Edit .env to customize if needed
# OLLAMA_BASE_URL=http://localhost:11434
# OLLAMA_MODEL=qwen2.5
```

### Supported Models

While qwen2.5 is the default, you can use other Ollama models by setting the OLLAMA_MODEL environment variable:

```bash
export OLLAMA_MODEL=llama3.2
export OLLAMA_MODEL=mistral
export OLLAMA_MODEL=qwen2.5:7b
export OLLAMA_MODEL=qwen2.5:14b
```

Note: Make sure to pull the model first with `ollama pull <model-name>`

## Development

### Running tests

```bash
# Install dev dependencies
pip install -e ".[dev]"

# Run tests
pytest
```

### Code formatting

```bash
# Format code
ruff format .

# Lint code
ruff check .
```

## Troubleshooting

### Server won't start

1. Check if ports are available:

   ```bash
   lsof -i :13000
   lsof -i :11001
   lsof -i :11002
   ```

2. Check dependencies:

   ```bash
   pip list | grep a2a
   pip list | grep ollama
   ```

3. Verify Ollama is installed and running:

   ```bash
   ollama list
   ```

### Client can't connect

1. Verify server is running:

   ```bash
   curl http://localhost:11002/.well-known/agent-card.json
   ```

2. Check transport and port match:
   - JSON-RPC: port 11000
   - gRPC: port 11001
   - REST: port 11002

### Ollama Connection Issues

**Error: "Failed to connect to Ollama"**

1. Check if Ollama is running:

   ```bash
   ollama list
   ```

2. If not running, start Ollama:

   ```bash
   ollama serve
   ```

3. Test Ollama connection:

   ```bash
   curl http://localhost:11434/api/tags
   ```

4. Check Ollama logs for errors:

   ```bash
   # macOS/Linux
   journalctl -u ollama -f
   ```

**Error: "Model 'qwen2.5' not found"**

1. Pull the model:

   ```bash
   ollama pull qwen2.5
   ```

2. Verify the model is available:

   ```bash
   ollama list
   ```

**Poor Response Quality**

1. Try a larger model variant:

   ```bash
   ollama pull qwen2.5:14b
   export OLLAMA_MODEL=qwen2.5:14b
   ```

2. Adjust temperature in .env file:

   ```bash
   OLLAMA_TEMPERATURE=0.5  # Lower for more deterministic responses
   ```

**Slow Response Times**

1. First request is slower (model loading) - subsequent requests are faster
2. Consider using a smaller model for faster responses:

   ```bash
   ollama pull qwen2.5:7b
   export OLLAMA_MODEL=qwen2.5:7b
   ```

3. Enable GPU acceleration if available (Ollama does this automatically)

## Cross-Language Testing

This implementation is designed to work with agents and hosts in other languages (Java, JavaScript, C#, Go). See the main project README for cross-language testing instructions.
