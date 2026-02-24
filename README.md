# Aloha A2A Multi-Language Implementation

This project provides complete A2A (Agent-to-Agent) protocol implementations in five programming languages: Java, Python, JavaScript/TypeScript, C#, and Go. Each implementation includes both agent (server) and host (client) components with support for three transport protocols.

## Overview

The Aloha A2A framework demonstrates cross-language interoperability using the [A2A protocol specification](https://a2a-protocol.org/latest/specification/). Each language implementation supports:

- **Three Transport Protocols**: JSON-RPC 2.0, gRPC, and HTTP+JSON/REST
- **Streaming Communication**: Real-time bidirectional data flow
- **Agent and Host**: Complete server and client implementations
- **Example Tools**: Dice rolling and prime number checking
- **Cross-Language Compatibility**: Any host can communicate with any agent regardless of language

### What is A2A?

The Agent-to-Agent (A2A) protocol is a standardized communication protocol that enables AI agents to interact with each other seamlessly. It provides:

- **Standardized Message Format**: Consistent structure for agent communication
- **Multiple Transport Options**: Flexibility to choose the best transport for your use case
- **Streaming Support**: Real-time updates during long-running operations
- **Task Management**: Track and manage asynchronous operations
- **Agent Discovery**: Discover agent capabilities through agent cards

## Language Implementations

| Language | Agent | Host | SDK Version |
|----------|-------|------|-------------|
| Java | ✓ | ✓ | v0.3.0.Beta2 |
| Python | ✓ | ✓ | v0.3.10 |
| JavaScript/TypeScript | ✓ | ✓ | v0.3.4 |
| C# | ✓ | ✓ | v0.3.3-preview |
| Go | ✓ | ✓ | latest |

## Directory Structure

```
aloha-a2a/
├── aloha-java/          # Java implementation
├── aloha-python/        # Python implementation
├── aloha-js/            # JavaScript/TypeScript implementation
├── aloha-csharp/        # C# implementation
├── aloha-go/            # Go implementation
├── test-suite/          # Cross-language interoperability test suite
└── README.md            # This file
```

## Quick Start

### Prerequisites

Before getting started, ensure you have the following installed:

- **Ollama**: Local LLM runtime (required for all implementations)
- **Java**: JDK 21+ and Maven 3.8+ (for Java implementation)
- **Python**: Python 3.11+ and pip or uv (for Python implementation)
- **Node.js**: Node.js 18+ and npm (for JavaScript implementation)
- **.NET**: .NET 8.0+ SDK (for C# implementation)
- **Go**: Go 1.21+ (for Go implementation)

#### Installing Ollama

Ollama is required to run the LLM-powered agents. Install it for your platform:

**macOS:**
```bash
brew install ollama
```

**Linux:**
```bash
curl -fsSL https://ollama.ai/install.sh | sh
```

**Windows:**
Download from [https://ollama.ai/download](https://ollama.ai/download)

**Pull the qwen2.5 model:**
```bash
ollama pull qwen2.5
```

**Start Ollama service** (if not auto-started):
```bash
ollama serve
```

**Verify installation:**
```bash
ollama list
```

### Running Your First Agent and Host

Here's a quick example using Python:

**Step 1 - Ensure Ollama is running:**
```bash
# Check if Ollama is running
ollama list

# If not running, start it
ollama serve
```

**Step 2 - Start the Agent:**
```bash
cd aloha-a2a/aloha-python/agent
pip install -e .
python -m agent
```

**Step 3 - Run the Host:**
```bash
cd aloha-a2a/aloha-python/host
pip install -e .
python -m host --message "Roll a 6-sided dice"
```

You should see the agent process your request using Ollama's qwen2.5 model and return a dice roll result!

### General Setup Steps

Each language directory contains its own README with specific setup instructions. General steps:

1. Navigate to the language directory (e.g., `cd aloha-java`)
2. Follow the setup instructions in the language-specific README
3. Start the agent server
4. Run the host client to interact with the agent

See the [Language-Specific Setup](#language-specific-setup) section below for detailed instructions.

## Transport Protocols

The A2A protocol supports three transport mechanisms, each with different characteristics:

### Port Allocation

Each language implementation uses a dedicated port range to avoid conflicts:

| Language | gRPC Port | JSON-RPC Port | REST Port |
|----------|-----------|---------------|-----------|
| Java     | 11000     | 11001         | 11002     |
| Go       | 12000     | 12001         | 12002     |
| Python   | 13000     | 13001         | 13002     |
| JavaScript | 14000   | 14001         | 14002     |
| C#       | 15000     | 15001         | 15002     |

See [PORT_CONFIGURATION.md](PORT_CONFIGURATION.md) for detailed port configuration information.

### gRPC
- **Protocol**: Binary protocol with HTTP/2
- **Best for**: High-performance, low-latency communication
- **Streaming**: Bidirectional streaming support
- **Use case**: Microservices, high-throughput scenarios

**Example connection:**
```bash
# Java agent
grpc://localhost:11000

# Python agent
grpc://localhost:13000
```

### JSON-RPC 2.0
- **Protocol**: WebSocket or HTTP POST
- **Best for**: Real-time bidirectional communication
- **Streaming**: Native WebSocket streaming
- **Use case**: Interactive applications requiring low latency

**Example connection:**
```bash
# WebSocket to Java agent
ws://localhost:11001

# HTTP POST to Python agent
curl -X POST http://localhost:13001/jsonrpc \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"message/send","params":{...},"id":1}'
```

### REST (HTTP+JSON)
- **Protocol**: Standard HTTP with JSON payloads
- **Best for**: Simple integration, debugging, web applications
- **Streaming**: Server-sent events (SSE)
- **Use case**: Web applications, easy debugging, broad compatibility

**Example connection:**
```bash
# Send message to Java agent
curl -X POST http://localhost:11002/v1/message:send \
  -H "Content-Type: application/json" \
  -d '{
    "kind": "message",
    "messageId": "msg-123",
    "role": "user",
    "parts": [{"kind": "text", "text": "Roll a 6-sided dice"}],
    "contextId": "ctx-123"
  }'

# Get agent card from Python agent
curl http://localhost:13002/.well-known/agent-card.json
```

### Choosing a Transport

| Feature | JSON-RPC 2.0 | gRPC | REST |
|---------|--------------|------|------|
| Ease of Use | Medium | Medium | Easy |
| Performance | Good | Excellent | Good |
| Debugging | Medium | Hard | Easy |
| Browser Support | Yes (WebSocket) | Limited | Yes |
| Streaming | Native | Native | SSE |
| Binary Data | No | Yes | No |

**Recommendation**: Start with REST for development and debugging, then switch to gRPC for production if performance is critical.

## Cross-Language Interoperability

All implementations are designed to be fully interoperable. You can:

- Run a Java agent and connect with a Python host
- Run a Python agent and connect with a C# host
- Mix and match any language combination across any transport protocol

### Interoperability Matrix

The framework supports **75 combinations** (5 languages × 5 languages × 3 transports):

```
Host Language → Agent Language
  ↓
Java, Python, JavaScript, C#, Go
  ×
Java, Python, JavaScript, C#, Go
  ×
JSON-RPC 2.0, gRPC, REST
```

All combinations are tested and validated. See the [Testing](#testing) section for details.

### Example: Cross-Language Communication

**Scenario**: Python host communicating with Java agent via gRPC

**Terminal 1 - Start Java Agent:**
```bash
cd aloha-a2a/aloha-java/agent
mvn compile quarkus:dev
```

**Terminal 2 - Run Python Host:**
```bash
cd aloha-a2a/aloha-python/host
python -m host --transport grpc --port 13000 --message "Roll a 20-sided dice"
```

The Python host successfully communicates with the Java agent, demonstrating true cross-language interoperability!

## Language-Specific Setup

### Java

**Requirements**: JDK 21+, Maven 3.8+, Ollama (for LLM)

**Setup Agent:**
```bash
cd aloha-a2a/aloha-java/agent
mvn clean install
mvn compile quarkus:dev
```

**Run Host:**
```bash
cd aloha-a2a/aloha-java/host
mvn compile exec:java -Dexec.args="--transport grpc --port 11000 --message 'Roll a 6-sided dice'"
```

**Documentation**: [aloha-java/README.md](aloha-java/README.md)

### Python

**Requirements**: Python 3.11+, pip or uv

**Setup Agent:**
```bash
cd aloha-a2a/aloha-python/agent
pip install -e .
python -m agent
```

**Run Host:**
```bash
cd aloha-a2a/aloha-python/host
pip install -e .
python -m host --transport rest --port 13002 --message "Roll a 20-sided dice"
```

**Documentation**: [aloha-python/README.md](aloha-python/README.md)

### JavaScript/TypeScript

**Requirements**: Node.js 18+, npm

**Setup Agent:**
```bash
cd aloha-a2a/aloha-js/agent
npm install
npm run build
npm start
```

**Run Host:**
```bash
cd aloha-a2a/aloha-js/host
npm install
npm run build
npm start -- --transport rest --port 14002 --message "Is 17 prime?"
```

**Documentation**: [aloha-js/README.md](aloha-js/README.md)

### C#

**Requirements**: .NET 8.0+ SDK

**Setup Agent:**
```bash
cd aloha-a2a/aloha-csharp/Agent
dotnet restore
dotnet run
```

**Run Host:**
```bash
cd aloha-a2a/aloha-csharp/Host
dotnet run -- --transport rest --port 15002 --message "Check if 2, 7, 11 are prime"
```

**Documentation**: [aloha-csharp/README.md](aloha-csharp/README.md)

### Go

**Requirements**: Go 1.21+

**Setup Agent:**
```bash
cd aloha-a2a/aloha-go/agent
go build
./agent
```

**Run Host:**
```bash
cd aloha-a2a/aloha-go/host
go build
./host --transport grpc --port 12000 --message "Roll a 12-sided dice and check if it's prime"
```

**Documentation**: [aloha-go/README.md](aloha-go/README.md)

## Features

### Agent Capabilities

Each agent implementation provides two example tools:

#### 1. Roll Dice Tool

Rolls an N-sided dice and returns a random number between 1 and N.

**Examples:**
- "Roll a 6-sided dice"
- "Roll a 20-sided dice"
- "Roll a d12"

**Tool Signature:**
```
roll_dice(sides: int) -> int
```

#### 2. Check Prime Tool

Checks which numbers in a list are prime numbers.

**Examples:**
- "Is 17 prime?"
- "Check if 2, 4, 7, 9, 11 are prime"
- "Are these numbers prime: 13, 15, 19"

**Tool Signature:**
```
check_prime(numbers: list[int]) -> string
```

### Additional Features

- **Agent Card**: Metadata describing agent capabilities served at `/.well-known/agent-card.json`
- **Streaming**: Real-time status updates during processing
- **Task Management**: Track task status and cancel ongoing operations
- **Session Management**: Maintain conversation context across multiple messages
- **Error Handling**: Graceful error handling with descriptive messages
- **LLM Integration**: Natural language understanding powered by Ollama qwen2.5 model
- **Multi-Language Support**: qwen2.5 provides excellent Chinese and English language support

### Agent Card Example

```json
{
  "name": "Dice Agent",
  "description": "An agent that can roll arbitrary dice and check prime numbers",
  "url": "localhost:11002",
  "version": "1.0.0",
  "capabilities": {
    "streaming": true,
    "pushNotifications": false
  },
  "skills": [
    {
      "id": "roll-dice",
      "name": "Roll Dice",
      "description": "Rolls an N-sided dice",
      "examples": ["Roll a 20-sided dice"]
    },
    {
      "id": "check-prime",
      "name": "Prime Checker",
      "description": "Checks if numbers are prime",
      "examples": ["Is 17 prime?"]
    }
  ]
}
```

## How to Select Transport Protocol

When running a host, you can specify which transport protocol to use:

### Using JSON-RPC 2.0

**Python (connect to Python agent):**
```bash
python -m host --transport jsonrpc --port 13001 --message "Roll a dice"
```

**Java (connect to Java agent):**
```bash
mvn exec:java -Dexec.args="--transport jsonrpc --port 11001 --message 'Roll a dice'"
```

**JavaScript (connect to JS agent):**
```bash
npm start -- --transport jsonrpc --port 14001 --message "Roll a dice"
```

**C# (connect to C# agent):**
```bash
dotnet run -- --transport jsonrpc --port 15001 --message "Roll a dice"
```

**Go (connect to Go agent):**
```bash
./host --transport jsonrpc --port 12001 --message "Roll a dice"
```

### Using gRPC

**Python (connect to Python agent):**
```bash
python -m host --transport grpc --port 13000 --message "Roll a dice"
```

**Java (connect to Java agent):**
```bash
mvn exec:java -Dexec.args="--transport grpc --port 11000 --message 'Roll a dice'"
```

**JavaScript (connect to JS agent):**
```bash
npm start -- --transport grpc --port 14000 --message "Roll a dice"
```

**C# (connect to C# agent):**
```bash
dotnet run -- --transport grpc --port 15000 --message "Roll a dice"
```

**Go (connect to Go agent):**
```bash
./host --transport grpc --port 12000 --message "Roll a dice"
```

### Using REST

**Python (connect to Python agent):**
```bash
python -m host --transport rest --port 13002 --message "Roll a dice"
```

**Java (connect to Java agent):**
```bash
mvn exec:java -Dexec.args="--transport rest --port 11002 --message 'Roll a dice'"
```

**JavaScript (connect to JS agent):**
```bash
npm start -- --transport rest --port 14002 --message "Roll a dice"
```

**C# (connect to C# agent):**
```bash
dotnet run -- --transport rest --port 15002 --message "Roll a dice"
```

**Go (connect to Go agent):**
```bash
./host --transport rest --port 12002 --message "Roll a dice"
```

## Requirements

### Common Requirements (All Implementations)

- **Ollama**: Local LLM runtime with qwen2.5 model
  - Installation: See [Installing Ollama](#installing-ollama) section above
  - Model: `ollama pull qwen2.5`
  - Service: `ollama serve` (must be running)

### Language-Specific Requirements

See individual language READMEs for specific requirements:

- **Java**: JDK 21+, Maven 3.8+, Ollama
- **Python**: Python 3.11+, uv or pip, Ollama
- **JavaScript**: Node.js 18+, npm or pnpm, Ollama
- **C#**: .NET 8.0+, Ollama
- **Go**: Go 1.21+, Ollama

## Troubleshooting

### Common Issues

#### Agent Won't Start

**Symptom**: Agent fails to start or exits immediately

**Solutions**:
1. Check if ports are already in use:
   ```bash
   # Check Java agent ports
   lsof -i :11000
   lsof -i :11001
   lsof -i :11002
   
   # Check Python agent ports
   lsof -i :13000
   lsof -i :13001
   lsof -i :13002
   ```
2. Kill processes using the ports:
   ```bash
   kill -9 $(lsof -ti:11000)
   ```
3. Check dependencies are installed
4. Review agent logs for error messages

#### Host Can't Connect to Agent

**Symptom**: Connection timeout or refused

**Solutions**:
1. Verify agent is running:
   ```bash
   # Java agent
   curl http://localhost:11002/.well-known/agent-card.json
   
   # Python agent
   curl http://localhost:13002/.well-known/agent-card.json
   ```
2. Check transport and port match (see [PORT_CONFIGURATION.md](PORT_CONFIGURATION.md)):
   - Java: gRPC 11000, JSON-RPC 11001, REST 11002
   - Go: gRPC 12000, JSON-RPC 12001, REST 12002
   - Python: gRPC 13000, JSON-RPC 13001, REST 13002
   - JavaScript: gRPC 14000, JSON-RPC 14001, REST 14002
   - C#: gRPC 15000, JSON-RPC 15001, REST 15002
3. Verify firewall settings
4. Check agent logs for errors

#### Ollama/LLM Integration Errors

**Symptom**: Agent starts but can't process requests, or "Ollama connection failed" errors

**Solutions**:

1. **Check Ollama is running**:
   ```bash
   # Check if Ollama is running
   curl http://localhost:11434/api/tags
   
   # If not running, start it
   ollama serve
   ```

2. **Verify qwen2.5 model is installed**:
   ```bash
   # List installed models
   ollama list
   
   # If qwen2.5 is not listed, pull it
   ollama pull qwen2.5
   ```

3. **Check Ollama connection settings**:
   ```bash
   # Default Ollama URL (should work for local setup)
   export OLLAMA_BASE_URL=http://localhost:11434
   export OLLAMA_MODEL=qwen2.5
   ```

4. **Test Ollama directly**:
   ```bash
   # Test if Ollama responds
   curl http://localhost:11434/api/generate -d '{
     "model": "qwen2.5",
     "prompt": "Hello"
   }'
   ```

5. **Check agent logs** for specific Ollama error messages

6. **Restart Ollama service**:
   ```bash
   # Stop Ollama
   pkill ollama
   
   # Start Ollama
   ollama serve
   ```

#### Build Failures

**Java**:
```bash
cd aloha-java
mvn clean install -U
```

**Python**:
```bash
cd aloha-python/agent
pip install --upgrade pip
pip install -e . --force-reinstall
```

**JavaScript**:
```bash
cd aloha-js/agent
rm -rf node_modules package-lock.json
npm install
```

**C#**:
```bash
cd aloha-csharp
dotnet clean
dotnet restore
dotnet build
```

**Go**:
```bash
cd aloha-go
go clean -modcache
go mod tidy
go build ./...
```

### Getting Help

1. Check language-specific README files
2. Review agent logs
3. Test agent card endpoint
4. Verify A2A SDK versions (especially [a2a-dotnet](https://github.com/a2aproject/a2a-dotnet/tags) and [a2a-go](https://github.com/a2aproject/a2a-go/tags), and align with latest stable tag)
5. Consult [A2A Protocol Specification](https://a2a-protocol.org/latest/specification/)

## Documentation

### Project Documentation

- [Main README](README.md) - This file
- [Design Document](.kiro/specs/aloha-a2a-multi-language/design.md) - Architecture and design decisions
- [Requirements Document](.kiro/specs/aloha-a2a-multi-language/requirements.md) - Detailed requirements

### Language-Specific Documentation

- [Java Implementation](aloha-java/README.md)
- [Python Implementation](aloha-python/README.md)
- [JavaScript Implementation](aloha-js/README.md)
- [C# Implementation](aloha-csharp/README.md)
- [Go Implementation](aloha-go/README.md)

### Testing Documentation

- [Test Suite README](test-suite/README.md) - Overview of test suite
- [Quick Start Guide](test-suite/QUICKSTART.md) - Get started with testing
- [Testing Guide](test-suite/TESTING_GUIDE.md) - Comprehensive testing guide
- [Architecture](test-suite/ARCHITECTURE.md) - Test suite architecture

### External Resources

- [A2A Protocol Specification](https://a2a-protocol.org/latest/specification/)
- [A2A GitHub Organization](https://github.com/a2aproject)

## Examples

### Example 1: Simple Dice Roll

**Start Python Agent:**
```bash
cd aloha-a2a/aloha-python/agent
python -m agent
```

**Run Java Host:**
```bash
cd aloha-a2a/aloha-java/host
mvn compile exec:java -Dexec.args="--transport rest --port 11002 --message 'Roll a 6-sided dice'"
```

**Expected Output:**
```
Connecting to agent at localhost:11002 using REST transport...
Sending message: Roll a 6-sided dice

Response:
I rolled a 6-sided dice and got: 4
```

### Example 2: Prime Number Check

**Start Go Agent:**
```bash
cd aloha-a2a/aloha-go/agent
./agent
```

**Run Python Host:**
```bash
cd aloha-a2a/aloha-python/host
python -m host --transport grpc --port 13000 --message "Check if 2, 4, 7, 9, 11 are prime"
```

**Expected Output:**
```
Connecting to agent at localhost:13000 using gRPC transport...
Sending message: Check if 2, 4, 7, 9, 11 are prime

Response:
The prime numbers are: 2, 7, 11
```

### Example 3: Combined Operations

**Start C# Agent:**
```bash
cd aloha-a2a/aloha-csharp/Agent
dotnet run
```

**Run JavaScript Host:**
```bash
cd aloha-a2a/aloha-js/host
npm start -- --transport rest --port 14002 --message "Roll a 12-sided dice and check if the result is prime"
```

**Expected Output:**
```
Connecting to agent at localhost:14002 using REST transport...
Sending message: Roll a 12-sided dice and check if the result is prime

Response:
I rolled a 12-sided dice and got: 7
7 is a prime number!
```

### Example 4: Streaming Responses

**Start Java Agent:**
```bash
cd aloha-a2a/aloha-java/agent
mvn compile quarkus:dev
```

**Run Python Host with Streaming:**
```bash
cd aloha-a2a/aloha-python/host
python -m host --transport grpc --port 13000 --message "Roll a 20-sided dice" --stream
```

**Expected Output:**
```
Connecting to agent at localhost:13000 using gRPC transport...
Sending message: Roll a 20-sided dice

[Status Update] Task submitted
[Status Update] Processing request...
[Status Update] Invoking roll_dice tool...
[Status Update] Task completed

Final Response:
I rolled a 20-sided dice and got: 17
```

### Example 5: Agent Card Discovery

**Query Agent Card:**
```bash
# Java agent
curl http://localhost:11002/.well-known/agent-card.json | jq

# Python agent
curl http://localhost:13002/.well-known/agent-card.json | jq
```

**Expected Output:**
```json
{
  "name": "Dice Agent",
  "description": "An agent that can roll arbitrary dice and check prime numbers",
  "url": "localhost:11002",
  "version": "1.0.0",
  "capabilities": {
    "streaming": true,
    "pushNotifications": false
  },
  "skills": [
    {
      "id": "roll-dice",
      "name": "Roll Dice",
      "description": "Rolls an N-sided dice",
      "examples": ["Roll a 20-sided dice"]
    },
    {
      "id": "check-prime",
      "name": "Prime Checker",
      "description": "Checks if numbers are prime",
      "examples": ["Is 17 prime?"]
    }
  ]
}
```

## Validation

### Manual Validation

To validate the A2A multi-language implementation:

1. **Start each agent** and verify it responds:
   ```bash
   # Example: Test Python agent
   curl http://localhost:11012/.well-known/agent-card.json
   ```

2. **Test cross-language communication** by running a host in one language against an agent in another:
   ```bash
   # Example: Python host → Java agent
   cd aloha-a2a/aloha-python/host
   python -m host --transport rest --port 11002 --message "Roll a dice"
   
   # Example: Java host → Python agent
   cd aloha-a2a/aloha-java/host
   mvn exec:java -Dexec.args="--transport rest --port 13002 --message 'Roll a dice'"
   ```

3. **Run the comprehensive test suite** (see [Testing](#testing) section below)

## Testing

### Cross-Language Interoperability Test Suite

The `test-suite/` directory contains a comprehensive testing framework that validates all 75 combinations (5 languages × 5 languages × 3 transports).

#### Quick Start

```bash
cd aloha-a2a/test-suite
pip install -e .
./run_tests.sh all
```

#### Test Coverage

- **75 test combinations**: 5 languages × 5 languages × 3 transports
- **10 test scenarios per combination**: 750 total tests
- **Automated agent lifecycle management**: Agents start/stop automatically
- **Comprehensive reporting**: JSON, Markdown, and HTML reports
- **Compatibility matrices**: Visual representation of working combinations
- **Performance metrics**: Response time analysis

#### Running Specific Tests

**Test all transports:**
```bash
python -m src.cli --transport all --output test-results
```

**Test JSON-RPC only:**
```bash
python -m src.cli --transport json-rpc --output test-results/json-rpc
```

**Test gRPC only:**
```bash
python -m src.cli --transport grpc --output test-results/grpc
```

**Test REST only:**
```bash
python -m src.cli --transport rest --output test-results/rest
```

#### Test Scenarios

Each combination runs these 10 scenarios:

1. **Basic Message Exchange** - Simple dice roll request
2. **Streaming Response** - Monitor real-time status updates
3. **Tool Invocation** - Verify tool execution
4. **Multi-Tool Execution** - Multiple tools in one request
5. **Prime Validation** - Complex logic validation
6. **Task Cancellation** - Cancel ongoing operations
7. **Session Continuity** - Multiple messages in same context
8. **Error Handling** - Graceful error handling
9. **Agent Card Discovery** - Agent metadata retrieval
10. **Protocol Compliance** - A2A protocol validation

#### Test Results

Results are saved in multiple formats:

```
test-results/
├── summary.json                           # Overall summary
├── REPORT.md                              # Markdown report
├── report.html                            # HTML report (open in browser)
├── java-host_python-agent_grpc.json      # Individual test results
├── python-host_java-agent_rest.json
└── ... (75 result files)
```

#### Expected Output

```
Cross-Language Interoperability Test Results
============================================

Overall Statistics:
  Total Test Combinations: 75
  Passed: 75
  Failed: 0
  Success Rate: 100.0%

By Transport Protocol:
  JSON-RPC 2.0: 25/25 (100.0%)
  gRPC: 25/25 (100.0%)
  REST: 25/25 (100.0%)

By Language (as Agent):
  Java: 15/15 (100.0%)
  Python: 15/15 (100.0%)
  JavaScript: 15/15 (100.0%)
  C#: 15/15 (100.0%)
  Go: 15/15 (100.0%)

Average Response Times:
  JSON-RPC 2.0: 1.2s
  gRPC: 0.8s
  REST: 1.5s
```

#### Documentation

- [Test Suite README](test-suite/README.md) - Overview and installation
- [Quick Start Guide](test-suite/QUICKSTART.md) - Get started quickly
- [Testing Guide](test-suite/TESTING_GUIDE.md) - Comprehensive guide
- [Architecture](test-suite/ARCHITECTURE.md) - Technical architecture

#### Unit Tests

See individual language directories for unit testing instructions:

- **Java**: `mvn test`
- **Python**: `pytest`
- **JavaScript**: `npm test`
- **C#**: `dotnet test`
- **Go**: `go test ./...`

## Best Practices

### For Agent Development

1. **Implement All Transport Protocols**: Support JSON-RPC, gRPC, and REST for maximum compatibility
2. **Provide Agent Cards**: Always serve agent metadata at `/.well-known/agent-card.json`
3. **Support Streaming**: Implement streaming for real-time status updates
4. **Handle Errors Gracefully**: Return descriptive error messages following A2A protocol
5. **Log Important Events**: Log server startup, requests, and errors
6. **Validate Input**: Validate all incoming requests before processing
7. **Use Official SDKs**: Use official A2A SDKs for protocol compliance

### For Host Development

1. **Handle Streaming**: Process streaming responses for better user experience
2. **Implement Timeouts**: Set appropriate timeouts for requests
3. **Retry on Failure**: Implement retry logic with exponential backoff
4. **Discover Capabilities**: Query agent card before sending requests
5. **Maintain Context**: Use contextId for conversation continuity
6. **Handle Errors**: Gracefully handle connection and protocol errors

### For Testing

1. **Test All Transports**: Verify your implementation works with all three transports
2. **Test Cross-Language**: Test with agents/hosts in different languages
3. **Test Error Cases**: Verify error handling with invalid inputs
4. **Test Streaming**: Verify streaming responses work correctly
5. **Test Performance**: Measure and optimize response times
6. **Use Test Suite**: Run the comprehensive test suite before deployment

## Contributing

Contributions are welcome! To contribute:

1. **Fork the Repository**: Create your own fork
2. **Create a Branch**: `git checkout -b feature/your-feature`
3. **Follow Code Style**: Match the existing code style for each language
4. **Add Tests**: Include tests for new features
5. **Update Documentation**: Update READMEs and comments
6. **Run Tests**: Ensure all tests pass
7. **Submit Pull Request**: Create a PR with a clear description

### Code Style Guidelines

- **Java**: Follow Google Java Style Guide
- **Python**: Follow PEP 8, use type hints
- **JavaScript**: Use TypeScript, follow Airbnb style guide
- **C#**: Follow Microsoft C# coding conventions
- **Go**: Follow Effective Go guidelines

### Adding New Features

When adding new features:

1. Update the design document
2. Implement in all five languages
3. Add tests to the test suite
4. Update all relevant READMEs
5. Ensure cross-language compatibility

## Roadmap

### Completed ✅

- [x] Multi-language implementations (Java, Python, JavaScript, C#, Go)
- [x] Multi-transport support (JSON-RPC, gRPC, REST)
- [x] Streaming communication
- [x] Agent card discovery
- [x] Cross-language interoperability test suite
- [x] Comprehensive documentation

### Planned 🚧

- [ ] Authentication and authorization
- [ ] TLS/SSL support for all transports
- [ ] Rate limiting
- [ ] Metrics and monitoring
- [ ] Docker containers for easy deployment
- [ ] Kubernetes deployment examples
- [ ] Additional example agents (weather, calculator, etc.)
- [ ] Performance benchmarking suite
- [ ] CI/CD pipeline for automated testing

## License

See the main repository LICENSE file.

## Acknowledgments

- [A2A Protocol Specification](https://a2a-protocol.org/) - Protocol definition
- A2A SDK maintainers for official SDKs
- Contributors to this project

## Support

For questions, issues, or contributions:

1. Check the [documentation](#documentation)
2. Review [troubleshooting](#troubleshooting) section
3. Search existing issues
4. Create a new issue with details
5. Join community discussions

---

**Happy coding with A2A! 🚀**
