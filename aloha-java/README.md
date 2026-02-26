<!-- markdownlint-disable MD060 -->

# Aloha A2A - Java Implementation

A pure Java implementation of the A2A (Agent-to-Agent) protocol with support for three transport modes: gRPC, JSON-RPC, and REST (HTTP+JSON).

## Features

- **Three Transport Modes**: gRPC, JSON-RPC, and REST (HTTP+JSON)
- **Pure Java + Netty**: No framework (no Quarkus / Spring Boot) — just plain Java 21 with Netty
- **LLM Integration**: Uses Ollama with qwen2.5 model via Langchain4j
- **Tool Support**: Roll dice and check prime numbers
- **A2A SDK**: Uses A2A Java SDK v0.3.3.Final (compatible with A2A Protocol v0.3.x)

## Port Configuration

| Transport Mode    | Server Port | Agent Card URL                                       |
|:------------------|:------------|:-----------------------------------------------------|
| gRPC              | 11000       | `http://localhost:11001/.well-known/agent-card.json`  |
| JSON-RPC          | 11001       | `http://localhost:11001/.well-known/agent-card.json`  |
| REST (HTTP+JSON)  | 11001       | `http://localhost:11001/.well-known/agent-card.json`  |

> In gRPC mode, the gRPC service runs on port 11000 while the Agent Card HTTP endpoint runs on `http.port` (default 11001).

## Prerequisites

- Java 21+
- Maven 3.9+
- Ollama running locally (`http://localhost:11434`) with the `qwen2.5` model

## Build

```bash
cd aloha-java
mvn clean install
```

## gRPC Transport

### Server

```bash
cd server && mvn exec:exec -Dtransport.mode=grpc
# PowerShell
cd server ; mvn exec:exec "-Dtransport.mode=grpc"
```

**Endpoints**:

- gRPC: `localhost:11000`
- Agent Card: `http://localhost:11001/.well-known/agent-card.json`

### Client

```bash
cd client && mvn exec:exec -Dexec.args="--transport grpc --port 11000 --message 'Roll a 20-sided dice'"
# PowerShell
cd client ; cmd /c "mvn exec:exec -Dexec.args=""--transport grpc --port 11000 --message 'Roll a 20-sided dice'"""
```

## JSON-RPC Transport

### Server

```bash
cd server && mvn exec:exec -Dtransport.mode=jsonrpc
# PowerShell
cd server ; mvn exec:exec "-Dtransport.mode=jsonrpc"
```

**Endpoints**:

- JSON-RPC (WebSocket): `ws://localhost:11001`
- Agent Card: `http://localhost:11001/.well-known/agent-card.json`

### Client

```bash
cd client && mvn exec:exec -Dexec.args="--transport jsonrpc --port 11001 --message 'Check if 17 is prime'"
# PowerShell
cd client ; cmd /c "mvn exec:exec -Dexec.args=""--transport jsonrpc --port 11001 --message 'Check if 17 is prime'"""
```

## REST Transport

### Server

```bash
cd server && mvn exec:exec -Dtransport.mode=rest
# PowerShell
cd server ; mvn exec:exec "-Dtransport.mode=rest"
```

**Endpoints**:

- REST (HTTP+JSON): `http://localhost:11001`
- Agent Card: `http://localhost:11001/.well-known/agent-card.json`

### Client

```bash
cd client && mvn exec:exec -Dexec.args="--transport rest --port 11001 --message 'Check if 13 is prime'"
# PowerShell
cd client ; cmd /c "mvn exec:exec -Dexec.args=""--transport rest --port 11001 --message 'Check if 13 is prime'"""
```

## Configuration

All settings are in `server/src/main/resources/application.properties`.
Override any property with `-Dkey=value` system properties.

| Property               | Default                      | Description                              |
|:-----------------------|:-----------------------------|:-----------------------------------------|
| `transport.mode`       | `grpc`                       | Transport: `grpc`, `jsonrpc`, or `rest`  |
| `grpc.server.port`     | `11000`                      | gRPC server port                         |
| `http.port`            | `11001`                      | HTTP port (agent card / JSON-RPC / REST) |
| `ollama.base-url`      | `http://localhost:11434`     | Ollama API base URL                      |
| `ollama.model`         | `qwen2.5`                    | Ollama model name                        |
| `ollama.temperature`   | `0.7`                        | LLM temperature                          |
| `ollama.timeout`       | `60`                         | Ollama timeout (seconds)                 |

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
- [Langchain4j Documentation](https://docs.langchain4j.dev/)
