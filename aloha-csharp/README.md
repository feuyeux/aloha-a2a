<!-- markdownlint-disable MD060 -->

# Aloha A2A - C# Implementation

A C# implementation of the A2A (Agent-to-Agent) protocol using the [official A2A .NET SDK](https://github.com/a2aproject/a2a-dotnet) v0.3.3-preview with JSON-RPC and REST transport support.

## Features

- **JSON-RPC 2.0 Transport**: Standard JSON-RPC over HTTP via SDK's `MapA2A()`
- **REST Transport**: HTTP+JSON based communication via SDK's `MapHttpA2A()`
- **Official A2A SDK**: Uses `A2A` + `A2A.AspNetCore` 0.3.3-preview NuGet packages
- **ASP.NET Core**: Minimal API
- **LLM Integration**: Uses Ollama with qwen2.5 model via Semantic Kernel
- **Tool Support**: Roll dice and check prime numbers
- **Streaming**: SSE streaming support for both transports

> **Note**: gRPC is not supported in the C# A2A SDK v0.3.3-preview.

## Port Configuration

| Transport Mode    | Server Port | Agent Card URL                                       |
|:------------------|:------------|:-----------------------------------------------------|
| JSON-RPC 2.0      | 15001       | `http://localhost:15001/.well-known/agent-card.json` |
| REST (HTTP+JSON)  | 15002       | `http://localhost:15002/.well-known/agent-card.json` |

## Prerequisites

- .NET 9.0 SDK
- Ollama running locally (`http://localhost:11434`) with the `qwen2.5` model

## Build

```bash
cd aloha-csharp
dotnet restore
dotnet build
```

## REST Transport

### Server

```bash
cd Server && dotnet run
# PowerShell
cd Server ; dotnet run
```

Both transports start simultaneously:

- JSON-RPC: `http://localhost:15001`
- REST: `http://localhost:15002`
- Agent Card: `http://localhost:15001/.well-known/agent-card.json`

### Client

The client uses the A2A SDK with JSON-RPC transport.

```bash
cd Client && dotnet run -- --message "Roll a 20-sided dice"
# PowerShell
cd Client ; dotnet run -- --message "Roll a 20-sided dice"
```

### Client Options

```bash
# Non-streaming (default)
dotnet run -- --message "Roll a 20-sided dice"

# With streaming
dotnet run -- --stream --message "Check if 17 is prime"

# With custom port
dotnet run -- --port 15001 --message "Roll a 6-sided dice"
```

## Configuration

All settings are in `Server/appsettings.json`. Override with environment variables.

| Property              | Default                   | Description                     |
|:----------------------|:--------------------------|:--------------------------------|
| `Ports:JsonRpc`       | `15001`                   | JSON-RPC server port            |
| `Ports:Rest`          | `15002`                   | REST server port                |
| `Ollama:BaseUrl`      | `http://localhost:11434`  | Ollama API base URL             |
| `Ollama:Model`        | `qwen2.5`                 | Ollama model name               |
| `Ollama:Temperature`  | `0.7`                     | LLM temperature                 |

Environment variables (`OLLAMA_BASE_URL`, `OLLAMA_MODEL`) take precedence over config file.

## Agent Tools

1. **roll_dice(N)**: Rolls an N-sided dice
   - Example: "Roll a 20-sided dice"
   - Example (Chinese): "投掷一个6面骰子"

2. **check_prime(nums)**: Checks if numbers are prime
   - Example: "Check if 2, 4, 7, 9, 11 are prime"
   - Example (Chinese): "检查17是否为质数"

## References

- [A2A Protocol Specification](https://a2a-protocol.org/latest/specification/)
- [A2A .NET SDK](https://github.com/a2aproject/a2a-dotnet) v0.3.3-preview
- [Semantic Kernel Documentation](https://learn.microsoft.com/en-us/semantic-kernel/)
