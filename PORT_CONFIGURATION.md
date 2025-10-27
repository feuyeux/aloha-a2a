# Port Configuration

This document describes the port allocation for all A2A services across different language implementations.

## Port Allocation

Each language implementation uses a dedicated port range to avoid conflicts when running multiple agents simultaneously:

| Language | gRPC Port | JSON-RPC Port | REST Port |
|----------|-----------|---------------|-----------|
| Java     | 11000     | 11001         | 11002     |
| Go       | 12000     | 12001         | 12002     |
| Python   | 13000     | 13001         | 13002     |
| JavaScript | 14000   | 14001         | 14002     |
| C#       | 15000     | 15001         | 15002     |

## Transport Protocols

### gRPC
- **Protocol**: Binary protocol with HTTP/2
- **Best for**: High-performance, low-latency communication
- **Connection**: `grpc://localhost:<port>`

### JSON-RPC 2.0
- **Protocol**: WebSocket or HTTP POST
- **Best for**: Real-time bidirectional communication
- **Connection**: `ws://localhost:<port>` or `http://localhost:<port>/jsonrpc`

### REST (HTTP+JSON)
- **Protocol**: Standard HTTP with JSON payloads
- **Best for**: Simple integration, debugging, web applications
- **Connection**: `http://localhost:<port>`

## Configuration

### Environment Variables

All agents support port configuration via environment variables:

```bash
GRPC_PORT=<port>      # gRPC transport port
JSONRPC_PORT=<port>   # JSON-RPC transport port
REST_PORT=<port>      # REST transport port
HOST=<address>        # Bind address (default: 0.0.0.0)
```

### Command-Line Arguments

All host clients support port specification:

```bash
--transport <type>    # Transport protocol: grpc, jsonrpc, or rest
--host <hostname>     # Agent hostname (default: localhost)
--port <port>         # Agent port (auto-selected based on transport if not specified)
--message <text>      # Message to send to the agent
```

## Examples

### Running Multiple Agents Simultaneously

You can run agents from different languages at the same time without port conflicts:

```bash
# Terminal 1: Start Java agent (ports 11000-11002)
cd aloha-java/agent
mvn quarkus:dev

# Terminal 2: Start Python agent (ports 13000-13002)
cd aloha-python/agent
python agent.py

# Terminal 3: Start Go agent (ports 12000-12002)
cd aloha-go/agent
./agent

# Terminal 4: Start JavaScript agent (ports 14000-14002)
cd aloha-js/agent
npm start

# Terminal 5: Start C# agent (ports 15000-15002)
cd aloha-csharp/Agent
dotnet run
```

### Connecting to Specific Agents

#### Connect to Java Agent (REST)
```bash
python -m host --transport rest --port 11002 --message "Roll a dice"
```

#### Connect to Go Agent (gRPC)
```bash
python -m host --transport grpc --port 12000 --message "Roll a dice"
```

#### Connect to Python Agent (JSON-RPC)
```bash
python -m host --transport jsonrpc --port 13001 --message "Roll a dice"
```

#### Connect to JavaScript Agent (REST)
```bash
python -m host --transport rest --port 14002 --message "Roll a dice"
```

#### Connect to C# Agent (REST)
```bash
python -m host --transport rest --port 15002 --message "Roll a dice"
```

## Troubleshooting

### Port Already in Use

If you encounter "port already in use" errors:

1. Check which process is using the port:
   ```bash
   # macOS/Linux
   lsof -i :<port>
   
   # Windows
   netstat -ano | findstr :<port>
   ```

2. Kill the process or use a different port:
   ```bash
   # Using environment variable
   GRPC_PORT=12100 python agent.py
   
   # Using command-line argument (for host)
   python -m host --port 12100 --message "Roll a dice"
   ```

### Connection Refused

If you get "connection refused" errors:

1. Verify the agent is running
2. Check that you're using the correct port for the language implementation
3. Ensure the transport type matches (gRPC/JSON-RPC/REST)
4. Verify firewall settings allow connections on the port

### Default Port Selection

When no port is specified, host clients automatically select the default port based on:
- The transport type (gRPC, JSON-RPC, or REST)
- The language implementation you're connecting to

Make sure to specify the correct port for the agent you want to connect to.
