# Aloha A2A - Java Implementation

A Java implementation of the A2A (Agent-to-Agent) protocol with support for three transport modes: gRPC, JSON-RPC, and REST (HTTP+JSON).

## Features

- **Three Transport Modes**: gRPC, JSON-RPC, and REST (HTTP+JSON)
- **LLM Integration**: Uses Ollama with qwen2.5 model for natural language understanding
- **Tool Support**: Roll dice and check prime numbers
- **A2A Protocol**: Fully compliant with A2A Protocol v0.3.0

## Port Configuration

| Transport Mode | Port  | Profile    |
|:--------------|:------|:-----------|
| gRPC          | 11000 | `grpc`     |
| JSON-RPC      | 11001 | `jsonrpc`  |
| REST (HTTP+JSON) | 11002 | `rest`  |

**Note**: In gRPC mode, the gRPC service runs on port 11000, while the Agent Card HTTP endpoint is available on port 8080 to avoid port conflicts.

## Build

```bash
cd aloha-java
mvn clean install
```

## gRPC Transport

### Agent

```bash
cd agent
mvn quarkus:dev -Dquarkus.profile=grpc
```

**Endpoints**:
- gRPC: `localhost:11000`
- Agent Card: `http://localhost:8080/.well-known/agent-card.json`

### Host

```bash
cd host
mvn exec:java -Dexec.args="--transport grpc --port 11000 --message 'Roll a 20-sided dice'"
```

### JSON-RPC Transport

### Agent

```bash
cd agent
mvn quarkus:dev -Dquarkus.profile=jsonrpc
```

**Endpoints**:
- JSON-RPC (WebSocket): `ws://localhost:11001`
- Agent Card: `http://localhost:11001/.well-known/agent-card.json`

### Host

```bash
cd host
mvn exec:java -Dexec.args="--transport jsonrpc --port 11001 --message 'Check if 17 is prime'"
```

## REST Transport

### Agent

```bash
cd agent
mvn quarkus:dev -Dquarkus.profile=rest
```

**Endpoints**:
- REST (HTTP+JSON): `http://localhost:11002`
- Agent Card: `http://localhost:11002/.well-known/agent-card.json`

### Host

```bash
cd host
mvn exec:java -Dexec.args="--transport rest --port 11002 --message 'Check if 13 is prime'"
```

## Agent Tools

1. **roll_dice(N)**: Rolls an N-sided dice
   - Example: "Roll a 20-sided dice"
   - Example (Chinese): "投掷一个6面骰子"
   
2. **check_prime(nums)**: Checks if numbers are prime
   - Example: "Check if 2, 4, 7, 9, 11 are prime"
   - Example (Chinese): "检查17是否为质数"

## References

- [A2A Protocol Specification](https://a2a-protocol.org/latest/specification/)
- [A2A Java SDK](https://github.com/a2asdk/a2a-java-sdk)
- [Quarkus Documentation](https://quarkus.io/guides/)
- [Langchain4j Documentation](https://docs.langchain4j.dev/)
