# Aloha A2A - Multi-Language A2A Protocol Implementation

The [A2A protocol](https://a2a-protocol.org) standardizes communication between AI agents, enabling seamless interaction regardless of the underlying technology stack.

| Language | SDK Version | SDK URL | Dependency Definition | gRPC | JSON-RPC | REST |
|:---------|:------------|:--------|:----------------------|:-----|:---------|:-----|
| Java | v0.3.3.Final | [a2a-java](https://github.com/a2aproject/a2a-java) | [pom.xml:24](aloha-java/pom.xml#L24) | 11000 ✅ | 11001 ✅ | 11002 ✅ |
| Go | v0.3.7 | [a2a-go](https://github.com/a2aproject/a2a-go) | [go.mod:6](aloha-go/go.mod#L6) | 12000 ✅ | 12001 ✅ | 12002 ✅ |
| Python | v0.3.24 | [a2a-python](https://github.com/a2aproject/a2a-python) | [pyproject.toml:7](aloha-python/server/pyproject.toml#L7) | 13000 ✅ | 13001 ✅ | 13002 ✅ |
| JS/TS | v0.3.10 | [a2a-js](https://github.com/a2aproject/a2a-js) | [package.json:23](aloha-js/server/package.json#L23) | 14000 ✅ | 14001 ✅ | 14002 ✅ |
| C# | 0.3.3-preview | [a2a-dotnet](https://github.com/a2aproject/a2a-dotnet) | [Server.csproj:11](aloha-csharp/Server/Server.csproj#L11) | 15000 ❌ | 15001 ✅ | 15002 ✅ |

<https://github.com/a2aproject>

## 🛠️ Prerequisites

To run the agents, you need **[Ollama](https://ollama.ai/)** installed and running locally to provide LLM capabilities.

1. **Install Ollama**: Follow instructions at [ollama.ai](https://ollama.ai).
2. **Pull Model**: `ollama pull qwen2.5`
3. **Start Service**: `ollama serve`

You will also need the specific runtime for your chosen language (JDK 21+, Python 3.11+, Node.js 18+, .NET 9.0, or Go 1.24+).

## 🏃 Quick Start

1. **Choose a language** from the table above.
2. Navigate to its directory (e.g., `cd aloha-python`).
3. Follow the **README** in that folder to install dependencies and start the Server.
4. Run the corresponding Client (or a Client from another language) to exchange messages.

### Using Scripts (Recommended)

Each language module includes scripts in the `scripts/` directory for convenient server/client startup:

| Language | Server Scripts | Client Scripts |
|----------|---------------|----------------|
| Java | `aloha-java/server/scripts/{grpc,jsonrpc,rest}_server.bat` | `aloha-java/client/scripts/{grpc,jsonrpc,rest}_client.bat` |
| Go | `aloha-go/server/scripts/{grpc,jsonrpc,rest}_server.bat` | `aloha-go/client/scripts/{grpc,jsonrpc,rest}_client.bat` |
| Python | `aloha-python/server/scripts/{grpc,jsonrpc,rest}_server.bat` | `aloha-python/client/scripts/{grpc,jsonrpc,rest}_client.bat` |
| JavaScript/TypeScript | `aloha-js/server/scripts/{grpc,jsonrpc,rest}_server.bat` | `aloha-js/client/scripts/{grpc,jsonrpc,rest}_client.bat` |
| C# | `aloha-csharp/Server/scripts/{jsonrpc,rest}_server.bat` | `aloha-csharp/Client/scripts/{jsonrpc,rest}_client.bat` |

**Example (Python):**

```bash
# Terminal 1: Start REST Server
aloha-python\server\scripts\rest_server.bat

# Terminal 2: Run REST Client
aloha-python\client\scripts\rest_client.bat
```
