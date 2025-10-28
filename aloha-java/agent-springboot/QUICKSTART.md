# Agent2 Quick Start Guide

✅ **Production Ready** - All transport modes verified working (2025-10-28)

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

## Quick Start

### 1. Build the Project

```bash
cd aloha-java/agent2
mvn clean package
```

### 2. Run the Agent

Choose one of the transport modes:

#### REST Mode (Recommended for beginners)
```bash
./run.sh
```

The agent will start on port 11002.

#### gRPC Mode
```bash
./run-grpc.sh
```

The agent will start on port 11000 (both gRPC and HTTP for agent card)

#### JSON-RPC Mode
```bash
./run-jsonrpc.sh
```

The agent will start on port 11001.

### 3. Test the Agent

#### Check Agent Card
```bash
# REST mode
curl http://localhost:11002/.well-known/agent-card.json

# gRPC mode
curl http://localhost:11000/.well-known/agent-card.json

# JSON-RPC mode
curl http://localhost:11001/.well-known/agent-card.json
```

#### Test with Host Client - All Modes Verified ✅

In a new terminal:

```bash
cd ../host

# Test REST - ✅ VERIFIED WORKING
mvn exec:java -Dexec.args="--transport rest --port 11002 --message 'Roll a 20-sided dice'"
# Expected: I rolled a 20-sided dice and got X.

# Test gRPC - ✅ VERIFIED WORKING
mvn exec:java -Dexec.args="--transport grpc --port 11000 --message 'Is 17 prime?'"
# Expected: Yes, 17 is a prime number.

# Test JSON-RPC - ✅ VERIFIED WORKING
mvn exec:java -Dexec.args="--transport jsonrpc --port 11001 --message 'Roll a 6-sided dice and check if it is prime'"
# Expected: I rolled a 6-sided dice and got X. The number X is [not] a prime number.
```

## Example Interactions

### Roll Dice
```bash
mvn exec:java -Dexec.args="--transport rest --port 11002 --message 'Roll a 20-sided dice'"
```

Expected output:
```
The result is 13.
```

### Check Prime Numbers
```bash
mvn exec:java -Dexec.args="--transport rest --port 11002 --message 'Check if 2, 4, 7, 9, 11 are prime'"
```

Expected output:
```
2, 7, 11 are prime numbers.
```

### Combined Operation
```bash
mvn exec:java -Dexec.args="--transport rest --port 11002 --message 'Roll a 6-sided dice and tell me if the result is prime'"
```

Expected output:
```
I rolled a 6-sided dice and got 5. Yes, 5 is a prime number.
```

### Chinese Language Support
```bash
mvn exec:java -Dexec.args="--transport rest --port 11002 --message '投掷一个6面骰子'"
```

Expected output:
```
你投掷了一个6面骰子，结果是4。
```

## Troubleshooting

### Ollama Not Running
```
Error: Failed to connect to Ollama
```

**Solution**: Start Ollama service
```bash
ollama serve
```

### Model Not Found
```
Error: Model qwen2.5 not found
```

**Solution**: Pull the model
```bash
ollama pull qwen2.5
```

### Port Already in Use
```
Error: Port 11002 is already in use
```

**Solution**: Stop the conflicting process or use a different transport mode

### Build Errors
```bash
# Clean and rebuild
mvn clean install

# Skip tests if needed
mvn clean install -DskipTests
```

## Configuration

Edit `src/main/resources/application.yml` to customize:

```yaml
# Change transport mode
transport:
  mode: rest  # grpc, jsonrpc, or rest

# Change Ollama URL
ollama:
  base-url: http://localhost:11434
  model: qwen2.5

# Change agent metadata
agent:
  name: My Custom Agent
  description: My custom dice agent
```

## Next Steps

1. Explore the [README.md](README.md) for detailed usage
2. Read [IMPLEMENTATION.md](IMPLEMENTATION.md) for architecture details
3. Check [COMPARISON.md](COMPARISON.md) for differences with agent module
4. Try different transport modes
5. Experiment with different prompts and questions

## Support

For issues or questions:
1. Check the logs for error messages
2. Verify Ollama is running and accessible
3. Ensure all ports are available
4. Review the implementation documentation


## Verification Status

✅ **All transport modes verified working** (2025-10-28):
- REST mode: ✅ Verified
- gRPC mode: ✅ Verified
- JSON-RPC mode: ✅ Verified

See [FINAL_TEST_RESULTS.md](FINAL_TEST_RESULTS.md) for detailed test results.
