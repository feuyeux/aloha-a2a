# Hello Agent2Agent (A2A) Protocol

The [A2A protocol](https://a2a-protocol.org) standardizes communication between AI agents, enabling seamless interaction regardless of the underlying technology stack.

## 🚀 Features

- **Multi-Language Support**: Full implementations in 5 major languages.
- **Interoperability**: Any host (client) can communicate with any agent (server) across languages.
- **Streaming**: Real-time bidirectional data flow support.
- **Standardized**: Adheres to the A2A specification for message formats and transport.

## 📂 Language Implementations

Each directory contains a complete Agent and Host implementation with its own detailed documentation.

| Language | Agent | Host | SDK Version | SDK URL |
|----------|-------|------|-------------|---------|
| Java | ✓ | ✓ | v0.3.3.Final | <https://github.com/a2asdk/a2a-java-sdk> |
| Python | ✓ | ✓ | v0.3.10 | <https://github.com/a2aproject/a2a-python> |
| JavaScript/TypeScript | ✓ | ✓ | v0.3.4 | <https://github.com/a2aproject/a2a-js> |
| C# | ✓ | ✓ | | <https://github.com/a2asdk/a2a-csharp-sdk> |
| Go | ✓ | ✓ | | <https://github.com/a2aproject/a2a-go> |

## 🛠️ Prerequisites

To run the agents, you need **[Ollama](https://ollama.ai/)** installed and running locally to provide LLM capabilities.

1. **Install Ollama**: Follow instructions at [ollama.ai](https://ollama.ai).
2. **Pull Model**: `ollama pull qwen2.5`
3. **Start Service**: `ollama serve`

You will also need the specific runtime for your chosen language (JDK 21+, Python 3.11+, Node.js 18+, .NET 9.0, or Go 1.24+).

## 🔌 Configuration & Ports

To prevent conflicts when running multiple agents simultaneously, each language uses a dedicated port range.

| Language | gRPC | JSON-RPC | REST |
|:---|:---|:---|:---|
| **Java** | 11000 | 11001 | 11002 |
| **Go** | 12000 | 12001 | 12002 |
| **Python** | 13000 | 13001 | 13002 |
| **JS/TS** | 14000 | 14001 | 14002 |
| **C#** | 15000 | 15001 | 15002 |

Configure ports via environment variables:

| Variable | Description | Default |
|----------|-------------|--------|
| `GRPC_PORT` | gRPC transport port | language default |
| `JSONRPC_PORT` | JSON-RPC transport port | language default |
| `REST_PORT` | REST transport port | language default |
| `HOST` | Bind address | `0.0.0.0` |

## 🏃 Quick Start

1. **Choose a language** from the table above.
2. Navigate to its directory (e.g., `cd aloha-python`).
3. Follow the **README** in that folder to install dependencies and start the Agent.
4. Run the corresponding Host (or a Host from another language) to exchange messages.

**Example (Python):**

```bash
# Terminal 1: Start Agent
cd aloha-python/agent
pip install -e .
python -m agent

# Terminal 2: Run Host
cd aloha-python/host
pip install -e .
python -m host --message "Hello A2A!"
```

## 📄 License

See individual project directories or LICENSE file for details.
