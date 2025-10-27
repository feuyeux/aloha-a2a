# Aloha A2A - Java Implementation

Java implementation of the A2A protocol with agent and host components supporting JSON-RPC 2.0, gRPC, and REST transports.

## Requirements

- Java 21 or higher
- Maven 3.8+
- [Ollama](https://ollama.ai/) - Local LLM runtime for natural language understanding

## Project Structure

```
aloha-java/
├── agent/              # Agent (server) implementation
│   ├── src/
│   │   └── main/
│   │       ├── java/
│   │       │   └── com/aloha/a2a/agent/
│   │       │       ├── DiceAgent.java
│   │       │       ├── DiceAgentExecutor.java
│   │       │       ├── Tools.java
│   │       │       └── Main.java
│   │       └── resources/
│   │           └── application.properties
│   └── pom.xml
├── host/               # Host (client) implementation
│   ├── src/
│   │   └── main/
│   │       └── java/
│   │           └── com/aloha/a2a/host/
│   │               ├── Client.java
│   │               └── Main.java
│   └── pom.xml
├── pom.xml             # Parent POM
└── README.md           # This file
```

## Dependencies

- A2A Java SDK v0.3.0.Beta2
- Quarkus framework
- Langchain4j with Ollama
- gRPC and JSON-RPC transport libraries

## Setup

### 1. Install Java 21

```bash
# macOS with Homebrew
brew install openjdk@21

# Verify installation
java -version
```

### 2. Install Maven

```bash
# macOS with Homebrew
brew install maven

# Verify installation
mvn -version
```

### 3. Install Ollama

Ollama is required for the agent's natural language understanding capabilities.

```bash
# macOS
brew install ollama

# Linux
curl -fsSL https://ollama.ai/install.sh | sh

# Windows
# Download from https://ollama.ai/download
```

### 4. Pull the qwen2.5 Model

The agent uses qwen2.5 by default for excellent multilingual support (English and Chinese):

```bash
# Start Ollama service (if not auto-started)
ollama serve

# Pull the qwen2.5 model (in another terminal)
ollama pull qwen2.5

# Verify the model is available
ollama list
```

### 5. Configure Environment (Optional)

Copy the example environment file and customize if needed:

```bash
cd agent
cp .env.example .env
# Edit .env to customize Ollama settings
```

### 6. Build the Project

```bash
cd aloha-java
mvn clean install
```

## Running the Agent

The agent starts three transport servers simultaneously:

```bash
cd agent
mvn compile quarkus:dev
```

The agent will be available on:
- gRPC: `localhost:11000`
- JSON-RPC 2.0: `ws://localhost:11001`
- REST: `http://localhost:11002`
- Agent Card: `http://localhost:11002/.well-known/agent-card.json`

## Running the Host

### JSON-RPC 2.0 Transport

```bash
cd host
mvn compile exec:java -Dexec.args="--transport jsonrpc --port 11001 --message 'Roll a 6-sided dice'"
```

### gRPC Transport

```bash
mvn compile exec:java -Dexec.args="--transport grpc --port 11000 --message 'Roll a 20-sided dice'"
```

### REST Transport

```bash
mvn compile exec:java -Dexec.args="--transport rest --port 11002 --message 'Check if 7 is prime'"
```

## Configuration

### Agent Configuration

The agent can be configured via environment variables or by editing `agent/src/main/resources/application.properties`:

**Environment Variables** (recommended):
```bash
export OLLAMA_BASE_URL=http://localhost:11434
export OLLAMA_MODEL=qwen2.5
export OLLAMA_TEMPERATURE=0.7
```

**Configuration File**:
```properties
# Server ports
quarkus.grpc.server.port=11000
quarkus.http.port=11001

# Ollama configuration
quarkus.langchain4j.ollama.base-url=${OLLAMA_BASE_URL:http://localhost:11434}
quarkus.langchain4j.ollama.chat-model.model-id=${OLLAMA_MODEL:qwen2.5}
quarkus.langchain4j.ollama.chat-model.temperature=${OLLAMA_TEMPERATURE:0.7}

# Agent metadata
agent.name=Dice Agent
agent.description=An agent that can roll dice and check prime numbers
agent.version=1.0.0
```

### Host Configuration

Command-line arguments:
- `--transport <jsonrpc|grpc|rest>`: Transport protocol to use
- `--host <hostname>`: Agent hostname (default: localhost)
- `--port <port>`: Agent port
- `--message <text>`: Message to send to the agent

## LLM Integration

This agent uses **Ollama with the qwen2.5 model** for natural language understanding and tool invocation. The LLM interprets user requests in both English and Chinese and calls the appropriate tools (roll_dice, check_prime) to fulfill the request.

### Why qwen2.5?

- **Multilingual Support**: Excellent performance in both English and Chinese
- **Local Deployment**: Runs entirely on your machine, no API keys needed
- **Privacy**: All data stays local
- **Tool Calling**: Native support for function calling

### Supported Models

While qwen2.5 is the default, you can use other Ollama models by setting the `OLLAMA_MODEL` environment variable:

```bash
export OLLAMA_MODEL=llama3.2
export OLLAMA_MODEL=mistral
export OLLAMA_MODEL=qwen2.5:7b
export OLLAMA_MODEL=qwen2.5:14b
```

Different model sizes offer trade-offs between speed and quality:
- `qwen2.5` (7B) - Fast, good quality (default)
- `qwen2.5:14b` - Slower, better quality
- `qwen2.5:32b` - Slowest, best quality (requires more RAM)

## Features

### Agent Tools

1. **roll_dice(N)**: Rolls an N-sided dice
   - Example: "Roll a 20-sided dice"
   - Example (Chinese): "投掷一个6面骰子"
   
2. **check_prime(nums)**: Checks if numbers are prime
   - Example: "Check if 2, 4, 7, 9, 11 are prime"
   - Example (Chinese): "检查17是否为质数"

### Supported Operations

- Message sending with streaming responses
- Task status tracking
- Task cancellation
- Agent card discovery
- Session management
- Multilingual support (English and Chinese)

## Testing

Run unit tests:

```bash
mvn test
```

## Cross-Language Testing

The Java agent can communicate with hosts written in any supported language:

```bash
# Python host -> Java agent
cd ../aloha-python/host
uv run python -m host --transport grpc --port 11000 --message "Roll a dice"

# JavaScript host -> Java agent
cd ../aloha-js/host
npm start -- --transport rest --port 11002 --message "Roll a dice"
```

## Troubleshooting

### Ollama Connection Issues

**Problem**: Agent fails to start or shows "Failed to connect to Ollama" errors

**Solutions**:
1. Check if Ollama is running:
   ```bash
   ollama list
   ```
   
2. Start Ollama service:
   ```bash
   ollama serve
   ```
   
3. Verify the base URL is correct:
   ```bash
   curl http://localhost:11434/api/tags
   ```

4. Check firewall settings if using a remote Ollama instance

### Model Not Found

**Problem**: "Model 'qwen2.5' not found" error

**Solution**:
```bash
# Pull the model
ollama pull qwen2.5

# Verify it's available
ollama list
```

### Poor Response Quality

**Problem**: Agent gives incorrect or low-quality responses

**Solutions**:
1. Try a larger model variant:
   ```bash
   ollama pull qwen2.5:14b
   export OLLAMA_MODEL=qwen2.5:14b
   ```

2. Adjust temperature (lower = more deterministic):
   ```bash
   export OLLAMA_TEMPERATURE=0.3
   ```

3. Ensure you have enough RAM for the model

### Port Already in Use

**Problem**: Ports 11000, 11001, or 11002 are already in use

**Solution**: Modify the ports in `application.properties`:
```properties
quarkus.http.port=12000
quarkus.grpc.server.port=12001
```

### Build Failures

**Problem**: Maven build fails

**Solution**: Clean and rebuild:
```bash
mvn clean install -U
```

### Agent Starts But Doesn't Respond

**Problem**: Agent starts successfully but doesn't process requests

**Solutions**:
1. Check the logs for LLM errors
2. Verify Ollama is responding:
   ```bash
   ollama run qwen2.5 "Hello"
   ```
3. Restart both Ollama and the agent

## References

- [A2A Protocol Specification](https://a2a-protocol.org/latest/specification/)
- [A2A Java SDK](https://github.com/a2asdk/a2a-java-sdk)
- [Quarkus Documentation](https://quarkus.io/guides/)
- [Langchain4j Documentation](https://docs.langchain4j.dev/)
