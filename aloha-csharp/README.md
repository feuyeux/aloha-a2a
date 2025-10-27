# Aloha A2A - C# Implementation

C# implementation of the A2A protocol with agent and host components. Currently supports REST transport with plans for JSON-RPC 2.0 and gRPC support.

## Requirements

- .NET 8.0 SDK or higher
- Visual Studio 2022 or Visual Studio Code (optional)
- [Ollama](https://ollama.ai/) - Local LLM runtime for qwen2.5 model

## Project Structure

```
aloha-csharp/
├── Agent/              # Agent (server) implementation
│   ├── A2AModels.cs
│   ├── DiceAgent.cs
│   ├── DiceAgentExecutor.cs
│   ├── RestTransportHandler.cs
│   ├── Tools.cs
│   ├── Program.cs
│   ├── appsettings.json
│   └── Agent.csproj
├── Host/               # Host (client) implementation
│   ├── A2AModels.cs
│   ├── RestClient.cs
│   ├── Program.cs
│   └── Host.csproj
├── Aloha.A2A.sln       # Solution file
└── README.md           # This file
```

## Dependencies

### Agent
- Microsoft.SemanticKernel v1.30.0 - LLM integration
- Grpc.AspNetCore v2.70.0 - gRPC support (future)
- System.Text.Json v8.0.5 - JSON serialization

### Host
- Grpc.Net.Client v2.70.0 - gRPC client (future)
- System.CommandLine v2.0.0-beta4 - CLI interface
- System.Text.Json v8.0.5 - JSON serialization

## Setup

### 1. Install .NET 8.0 SDK

```bash
# macOS with Homebrew
brew install dotnet@8

# Verify installation
dotnet --version
```

### 2. Install Ollama

```bash
# macOS
brew install ollama

# Linux
curl -fsSL https://ollama.ai/install.sh | sh

# Windows
# Download from https://ollama.ai/download
```

### 3. Pull the qwen2.5 Model

```bash
ollama pull qwen2.5
```

### 4. Start Ollama Service

```bash
# Start Ollama (if not auto-started)
ollama serve
```

Verify Ollama is running:
```bash
curl http://localhost:11434/api/tags
```

### 5. Restore Dependencies

```bash
cd aloha-csharp
dotnet restore
```

### 6. Configure Environment Variables (Optional)

Copy the example environment file:
```bash
cd Agent
cp .env.example .env
```

Edit `.env` to customize Ollama settings if needed:
```bash
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=qwen2.5
```

## Running the Agent

The agent currently supports REST transport:

```bash
cd Agent
dotnet run
```

The agent will be available on:
- REST: `http://localhost:15002`
- Agent Card: `http://localhost:15002/.well-known/agent-card.json`

Note: JSON-RPC 2.0 and gRPC transports are planned for future implementation.

## Running the Host

### REST Transport (Default)

```bash
cd Host
dotnet run -- --message "Roll a 6-sided dice"
```

### With Custom Port

```bash
dotnet run -- --port 15002 --message "Roll a 20-sided dice"
```

### With Streaming

```bash
dotnet run -- --stream --message "Check if 7 is prime"
```

### With Context ID (for conversation continuity)

```bash
dotnet run -- --context "my-context-123" --message "Roll a dice"
```

### Full Options

```bash
dotnet run -- --transport rest --host localhost --port 15002 --message "Roll a 12-sided dice and check if it's prime" --stream
```

Note: JSON-RPC and gRPC transports are not yet implemented. Use `--transport rest` (default).

## Configuration

### Agent Configuration

Edit `Agent/appsettings.json`:

```json
{
  "Ports": {
    "Grpc": 15000,
    "JsonRpc": 15001,
    "Rest": 15002
  },
  "SemanticKernel": {
    "ModelId": "gpt-4",
    "ApiKey": "your_api_key_here"
  }
}
```

### Host Configuration

Command-line arguments:
- `--transport <jsonrpc|grpc|rest>`: Transport protocol to use
- `--host <hostname>`: Agent hostname (default: localhost)
- `--port <port>`: Agent port
- `--message <text>`: Message to send to the agent

## LLM Integration

This agent uses **Ollama** with the **qwen2.5** model for natural language understanding and tool invocation. The LLM interprets user requests and calls the appropriate tools (roll_dice, check_prime) to fulfill the request.

### Why Ollama + qwen2.5?

- **Local deployment**: No external API keys or cloud dependencies
- **Privacy**: All data processed locally
- **Multilingual**: qwen2.5 excels at both English and Chinese
- **Cost-effective**: No per-request charges

### Supported Models

While qwen2.5 is the default, you can use other Ollama models by setting the `OLLAMA_MODEL` environment variable:

```bash
export OLLAMA_MODEL=llama3.2
export OLLAMA_MODEL=mistral
export OLLAMA_MODEL=qwen2.5:7b
export OLLAMA_MODEL=qwen2.5:14b
```

### Configuration

The agent reads Ollama configuration from:
1. Environment variables (highest priority)
2. `appsettings.json` configuration file

**Environment Variables:**
- `OLLAMA_BASE_URL`: Ollama server URL (default: `http://localhost:11434`)
- `OLLAMA_MODEL`: Model name (default: `qwen2.5`)

**appsettings.json:**
```json
{
  "Ollama": {
    "BaseUrl": "http://localhost:11434",
    "Model": "qwen2.5",
    "Temperature": 0.7
  }
}
```

## Features

### Agent Tools

1. **roll_dice(N)**: Rolls an N-sided dice
   - Example: "Roll a 20-sided dice"
   
2. **check_prime(nums)**: Checks if numbers are prime
   - Example: "Check if 2, 4, 7, 9, 11 are prime"

### Supported Operations

- Message sending with streaming responses
- Task status tracking
- Task cancellation
- Agent card discovery
- Session management

## Development

### Build Solution

```bash
dotnet build
```

### Run Tests

```bash
dotnet test
```

### Clean Build

```bash
dotnet clean
dotnet build
```

## Cross-Language Testing

The C# agent can communicate with hosts written in any supported language via REST:

```bash
# Python host -> C# agent
cd ../aloha-python/host
python -m host --transport rest --port 15002 --message "Roll a dice"

# JavaScript host -> C# agent
cd ../aloha-js/host
npm start -- --transport rest --port 15002 --message "Roll a dice"

# C# host -> Python agent
cd Host
dotnet run -- --port 13002 --message "Roll a dice"
```

## Troubleshooting

### Ollama Connection Issues

**Error: "Failed to connect to Ollama"**

1. Check if Ollama is running:
   ```bash
   curl http://localhost:11434/api/tags
   ```

2. Start Ollama if not running:
   ```bash
   ollama serve
   ```

3. Verify the model is installed:
   ```bash
   ollama list
   ```

4. Pull the model if missing:
   ```bash
   ollama pull qwen2.5
   ```

**Error: "Model not found"**

Pull the required model:
```bash
ollama pull qwen2.5
```

List available models:
```bash
ollama list
```

**Ollama running on different host/port**

Set the `OLLAMA_BASE_URL` environment variable:
```bash
export OLLAMA_BASE_URL=http://your-host:11434
dotnet run
```

### Port Already in Use

If ports are already in use, modify the ports in `appsettings.json`.

### Build Errors

Clean and rebuild:
```bash
dotnet clean
dotnet restore
dotnet build
```

### NuGet Package Restore Issues

Clear NuGet cache:
```bash
dotnet nuget locals all --clear
dotnet restore
```

### Poor Response Quality

Try a larger model variant:
```bash
ollama pull qwen2.5:14b
export OLLAMA_MODEL=qwen2.5:14b
dotnet run
```

Adjust temperature in `appsettings.json`:
```json
{
  "Ollama": {
    "Temperature": 0.5
  }
}
```

## References

- [A2A Protocol Specification](https://a2a-protocol.org/latest/specification/)
- [A2A C# SDK](https://github.com/a2asdk/a2a-csharp-sdk)
- [Semantic Kernel Documentation](https://learn.microsoft.com/en-us/semantic-kernel/)
- [ASP.NET Core Documentation](https://learn.microsoft.com/en-us/aspnet/core/)
