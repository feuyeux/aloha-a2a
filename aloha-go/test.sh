#!/bin/bash

# Test script for Go A2A implementation
# This script tests REST behavior and transport capability probe

set -e

echo "=========================================="
echo "Go A2A Implementation Test"
echo "=========================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

AGENT_BIN=""
HOST_BIN=""
PYTHON_CMD=""

resolve_binary() {
    local base_path="$1"
    if [ -f "$base_path" ]; then
        echo "$base_path"
        return 0
    fi
    if [ -f "${base_path}.exe" ]; then
        echo "${base_path}.exe"
        return 0
    fi
    return 1
}

resolve_python() {
    if command -v python >/dev/null 2>&1; then
        echo "python"
        return 0
    fi
    if command -v python3 >/dev/null 2>&1; then
        echo "python3"
        return 0
    fi
    if command -v py >/dev/null 2>&1; then
        echo "py"
        return 0
    fi
    return 1
}

# Check if binaries exist (or can be built)
if ! AGENT_BIN="$(resolve_binary "agent/agent")"; then
    if command -v go >/dev/null 2>&1; then
        echo -e "${YELLOW}Building agent...${NC}"
        (cd agent && go build -o agent)
        AGENT_BIN="$(resolve_binary "agent/agent")"
    else
        echo -e "${RED}✗ Agent binary not found and 'go' command is unavailable${NC}"
        exit 1
    fi
fi

if ! HOST_BIN="$(resolve_binary "host/host")"; then
    if command -v go >/dev/null 2>&1; then
        echo -e "${YELLOW}Building host...${NC}"
        (cd host && go build -o host)
        HOST_BIN="$(resolve_binary "host/host")"
    else
        echo -e "${RED}✗ Host binary not found and 'go' command is unavailable${NC}"
        exit 1
    fi
fi

echo -e "${GREEN}✓ Binaries ready${NC}"
echo ""

# Start agent in background
echo "Starting agent..."
"$AGENT_BIN" > /tmp/go-agent.log 2>&1 &
AGENT_PID=$!

# Wait for agent to start
sleep 2

# Check if agent is running
if ! kill -0 $AGENT_PID 2>/dev/null; then
    echo -e "${RED}✗ Agent failed to start${NC}"
    cat /tmp/go-agent.log
    exit 1
fi

echo -e "${GREEN}✓ Agent started (PID: $AGENT_PID)${NC}"
echo ""

test_rest_transport() {
    echo "Testing rest transport (port 12002)..."

    if "$HOST_BIN" --transport "rest" --port "12002" --message "Roll a 6-sided dice" > /tmp/go-host-rest.log 2>&1; then
        echo -e "${GREEN}✓ rest test passed${NC}"
        return 0
    else
        echo -e "${RED}✗ rest test failed${NC}"
        cat /tmp/go-host-rest.log
        return 1
    fi
}

# Test REST transport
FAILED=0
test_rest_transport || FAILED=$((FAILED + 1))
echo ""

# Test transport capability probe endpoint
echo "Testing transport capability probe endpoint..."
if curl -s http://localhost:12002/v1/transports | grep -q '"rest"'; then
    echo -e "${GREEN}✓ Transport probe endpoint test passed${NC}"
else
    echo -e "${RED}✗ Transport probe endpoint test failed${NC}"
    FAILED=$((FAILED + 1))
fi
echo ""

# Test agent card
echo "Testing agent card endpoint..."
if curl -s http://localhost:12002/.well-known/agent-card.json | grep -q "Dice Agent"; then
    echo -e "${GREEN}✓ Agent card test passed${NC}"
else
    echo -e "${RED}✗ Agent card test failed${NC}"
    FAILED=$((FAILED + 1))
fi
echo ""

# Test REST stream conformance baseline (Go target only)
echo "Testing REST stream conformance baseline (Go)..."
rm -f /tmp/go-stream-conformance.log
if PYTHON_CMD="$(resolve_python)"; then
    if [ "$PYTHON_CMD" = "py" ]; then
        if py -3 ../compare-stream-conformance.py --targets go --go-url http://localhost:12002/v1/message:stream > /tmp/go-stream-conformance.log 2>&1; then
            echo -e "${GREEN}✓ Stream conformance baseline passed${NC}"
        else
            echo -e "${RED}✗ Stream conformance baseline failed${NC}"
            cat /tmp/go-stream-conformance.log
            FAILED=$((FAILED + 1))
        fi
    else
        if "$PYTHON_CMD" ../compare-stream-conformance.py --targets go --go-url http://localhost:12002/v1/message:stream > /tmp/go-stream-conformance.log 2>&1; then
            echo -e "${GREEN}✓ Stream conformance baseline passed${NC}"
        else
            echo -e "${RED}✗ Stream conformance baseline failed${NC}"
            cat /tmp/go-stream-conformance.log
            FAILED=$((FAILED + 1))
        fi
    fi
else
    echo -e "${YELLOW}⚠ Skipping stream conformance check (python/python3/py not found in this shell)${NC}"
fi

echo ""

# Test AgentCard truth check (Go target only)
echo "Testing AgentCard truth check (Go)..."
rm -f /tmp/go-agentcard-truth.log
if PYTHON_CMD="$(resolve_python)"; then
    if [ "$PYTHON_CMD" = "py" ]; then
        if py -3 ../compare-agentcard-truth.py --targets go --go-base-url http://localhost:12002 > /tmp/go-agentcard-truth.log 2>&1; then
            echo -e "${GREEN}✓ AgentCard truth check passed${NC}"
        else
            echo -e "${RED}✗ AgentCard truth check failed${NC}"
            cat /tmp/go-agentcard-truth.log
            FAILED=$((FAILED + 1))
        fi
    else
        if "$PYTHON_CMD" ../compare-agentcard-truth.py --targets go --go-base-url http://localhost:12002 > /tmp/go-agentcard-truth.log 2>&1; then
            echo -e "${GREEN}✓ AgentCard truth check passed${NC}"
        else
            echo -e "${RED}✗ AgentCard truth check failed${NC}"
            cat /tmp/go-agentcard-truth.log
            FAILED=$((FAILED + 1))
        fi
    fi
else
    echo -e "${YELLOW}⚠ Skipping AgentCard truth check (python/python3/py not found in this shell)${NC}"
fi

echo ""

# Optional experimental JSON-RPC send test
if [ "${A2A_EXPERIMENTAL_TRANSPORTS:-0}" = "1" ]; then
    echo "Testing experimental JSON-RPC send path (port 12001)..."
    if "$HOST_BIN" --transport "jsonrpc" --port "12001" --message "Is 17 prime?" > /tmp/go-host-jsonrpc.log 2>&1; then
        echo -e "${GREEN}✓ Experimental JSON-RPC send test passed${NC}"
    else
        echo -e "${RED}✗ Experimental JSON-RPC send test failed${NC}"
        cat /tmp/go-host-jsonrpc.log
        FAILED=$((FAILED + 1))
    fi
    echo ""
else
    echo -e "${YELLOW}⚠ Skipping experimental JSON-RPC test (set A2A_EXPERIMENTAL_TRANSPORTS=1 to enable)${NC}"
    echo ""
fi

# Cleanup
echo "Stopping agent..."
kill $AGENT_PID 2>/dev/null || true
wait $AGENT_PID 2>/dev/null || true
echo -e "${GREEN}✓ Agent stopped${NC}"
echo ""

# Summary
echo "=========================================="
echo "Test Summary"
echo "=========================================="
if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}All tests passed!${NC}"
    exit 0
else
    echo -e "${RED}$FAILED test(s) failed${NC}"
    exit 1
fi
