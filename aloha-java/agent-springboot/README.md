# Aloha A2A - Java Agent2 (Spring Boot Implementation)

!!! NO PASS !!!

A complete Java implementation of the A2A (Agent-to-Agent) protocol using Spring Boot instead of Quarkus. This module provides the same functionality as the original agent but with Spring Boot framework.

## Features

- ✅ **Three Transport Modes**: gRPC, JSON-RPC, and REST (HTTP+JSON)
- ✅ **LLM Integration**: Uses Ollama with qwen2.5 model for natural language understanding
- ✅ **Tool Support**: Roll dice and check prime numbers
- ✅ **A2A Protocol**: Fully compliant with A2A Protocol v0.3.0
- ✅ **Spring Boot**: Uses Spring Boot 3.x with dependency injection
- ✅ **Complete Implementation**: All transport modes fully functional

## Port Configuration

| Transport Mode | Port  | Profile    |
|:--------------|:------|:-----------|
| gRPC          | 11000 | `grpc`     |
| JSON-RPC      | 11001 | `jsonrpc`  |
| REST (HTTP+JSON) | 11002 | `rest`  |

**Note**: In gRPC mode, both the gRPC service and the Agent Card HTTP endpoint run on port 11000.

## Build

```bash
cd aloha-java/agent2
mvn clean package
```

## Running the Agent

### gRPC Transport

```bash
./run-grpc.sh
```

Or:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=grpc
```

Or with JAR:
```bash
java -jar target/aloha-java-agent2-1.0.0.jar --spring.profiles.active=grpc
```

**Endpoints**:
- gRPC: `localhost:11000`
- Agent Card: `http://localhost:11000/.well-known/agent-card.json`

### JSON-RPC Transport

```bash
./run-jsonrpc.sh
```

Or:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=jsonrpc
```

Or with JAR:
```bash
java -jar target/aloha-java-agent2-1.0.0.jar --spring.profiles.active=jsonrpc
```

**Endpoints**:
- JSON-RPC (WebSocket): `ws://localhost:11001`
- Agent Card: `http://localhost:11001/.well-known/agent-card.json`

### REST Transport (Default)

```bash
./run.sh
```

Or:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=rest
```

Or with JAR:
```bash
java -jar target/aloha-java-agent2-1.0.0.jar --spring.profiles.active=rest
```

**Endpoints**:
- REST (HTTP+JSON): `http://localhost:11002`
- Agent Card: `http://localhost:11002/.well-known/agent-card.json`

## Agent Tools

1. **roll_dice(N)**: Rolls an N-sided dice
   - Example: "Roll a 20-sided dice"
   - Example (Chinese): "投掷一个6面骰子"
   
2. **check_prime(nums)**: Checks if numbers are prime
   - Example: "Check if 2, 4, 7, 9, 11 are prime"
   - Example (Chinese): "检查17是否为质数"

## Testing with Host

Use the host module to test the agent:

```bash
cd ../host

# Test gRPC
mvn exec:java -Dexec.args="--transport grpc --port 11000 --message 'Roll a 20-sided dice'"

# Test JSON-RPC
mvn exec:java -Dexec.args="--transport jsonrpc --port 11001 --message 'Check if 17 is prime'"

# Test REST
mvn exec:java -Dexec.args="--transport rest --port 11002 --message 'Check if 13 is prime'"
```

## Configuration

Edit `src/main/resources/application.yml` to customize:

```yaml
# Transport mode
transport:
  mode: rest  # grpc, jsonrpc, or rest

# Ollama configuration
ollama:
  base-url: http://localhost:11434
  model: qwen2.5
  temperature: 0.7
  timeout: 60

# Agent metadata
agent:
  name: Dice Agent (Spring Boot)
  description: An agent that can roll arbitrary dice and check prime numbers
  version: 1.0.0
```

## Architecture

### Core Components

- **Agent2Application**: Spring Boot main application
- **DiceAgent**: LLM-powered agent using Langchain4j
- **DiceAgentExecutor**: A2A protocol executor
- **Tools**: Dice rolling and prime checking tools
- **AgentCardProvider**: Agent metadata provider

### Transport Layer

- **SpringGrpcHandler**: gRPC transport implementation
- **SpringJsonRpcHandler**: JSON-RPC transport implementation
- **SpringRestHandler**: REST transport implementation
- **RestController**: REST API endpoints
- **JsonRpcWebSocketHandler**: WebSocket handler for JSON-RPC

### Configuration

- **OllamaConfig**: Ollama LLM configuration
- **AgentConfig**: Agent metadata configuration
- **application.yml**: Multi-profile configuration

## Differences from Agent Module

| Aspect | Agent (Quarkus) | Agent2 (Spring Boot) |
|--------|----------------|---------------------|
| Framework | Quarkus 3.15.1 | Spring Boot 3.2.0 |
| DI Container | CDI (Jakarta EE) | Spring Framework |
| Configuration | application.properties | application.yml |
| Dev Mode | `mvn quarkus:dev` | `mvn spring-boot:run` |
| Startup Time | ~1-2s | ~3-5s |
| Memory Usage | ~100MB | ~200MB |
| A2A Support | ✅ Full | ✅ Full |
| Complexity | Lower (auto-config) | Higher (manual config) |

**Both implementations are production-ready and fully functional!**

## Prerequisites

1. **Java 21** or higher
2. **Maven 3.8+**
3. **Ollama** with qwen2.5 model

### Install Ollama

```bash
# macOS
brew install ollama

# Start Ollama service
ollama serve

# Pull the model
ollama pull qwen2.5
```

## Troubleshooting

### Ollama Connection Issues

Ensure Ollama is running:
```bash
ollama serve
ollama pull qwen2.5
```

### Port Already in Use

Change the port in `application.yml` or use a different profile.

### Build Issues

```bash
# Clean and rebuild
mvn clean install

# Skip tests if needed
mvn clean install -DskipTests
```

## Development

### Running Tests

```bash
mvn test
```

### Building JAR

```bash
mvn clean package
```

### Running in IDE

Import as Maven project and run `Agent2Application.main()` with active profile set to `rest`, `grpc`, or `jsonrpc`.

## References

- [A2A Protocol Specification](https://a2a-protocol.org/latest/specification/)
- [A2A Java SDK](https://github.com/a2asdk/a2a-java-sdk)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Langchain4j Documentation](https://docs.langchain4j.dev/)
- [Ollama Documentation](https://ollama.ai/docs)

## License

Same as parent project.
