#!/bin/bash

echo "================================"
echo "Agent2 Complete Test Suite"
echo "================================"
echo ""

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test counter
TESTS_PASSED=0
TESTS_FAILED=0

# Function to print test result
print_result() {
    if [ $1 -eq 0 ]; then
        echo -e "${GREEN}✓ PASSED${NC}: $2"
        ((TESTS_PASSED++))
    else
        echo -e "${RED}✗ FAILED${NC}: $2"
        ((TESTS_FAILED++))
    fi
}

echo "1. Building project..."
mvn clean package -DskipTests -q
print_result $? "Build"
echo ""

echo "2. Running unit tests..."
mvn test -q > /dev/null 2>&1
print_result $? "Unit tests"
echo ""

echo "3. Checking JAR file..."
if [ -f "target/aloha-java-agent2-1.0.0.jar" ]; then
    SIZE=$(ls -lh target/aloha-java-agent2-1.0.0.jar | awk '{print $5}')
    echo -e "${GREEN}✓ PASSED${NC}: JAR file exists (Size: $SIZE)"
    ((TESTS_PASSED++))
else
    echo -e "${RED}✗ FAILED${NC}: JAR file not found"
    ((TESTS_FAILED++))
fi
echo ""

echo "4. Checking source files..."
REQUIRED_FILES=(
    "src/main/java/com/aloha/a2a/agent2/Agent2Application.java"
    "src/main/java/com/aloha/a2a/agent2/agent/DiceAgent.java"
    "src/main/java/com/aloha/a2a/agent2/executor/DiceAgentExecutor.java"
    "src/main/java/com/aloha/a2a/agent2/tools/Tools.java"
    "src/main/java/com/aloha/a2a/agent2/transport/SpringGrpcHandler.java"
    "src/main/java/com/aloha/a2a/agent2/transport/SpringJsonRpcHandler.java"
    "src/main/java/com/aloha/a2a/agent2/transport/SpringRestHandler.java"
    "src/main/java/com/aloha/a2a/agent2/transport/RestController.java"
    "src/main/java/com/aloha/a2a/agent2/transport/JsonRpcWebSocketHandler.java"
    "src/main/java/com/aloha/a2a/agent2/card/AgentCardProvider.java"
)

for file in "${REQUIRED_FILES[@]}"; do
    if [ -f "$file" ]; then
        echo -e "${GREEN}✓${NC} $file"
        ((TESTS_PASSED++))
    else
        echo -e "${RED}✗${NC} $file (missing)"
        ((TESTS_FAILED++))
    fi
done
echo ""

echo "5. Checking configuration files..."
CONFIG_FILES=(
    "src/main/resources/application.yml"
    "src/main/resources/logback-spring.xml"
    "pom.xml"
)

for file in "${CONFIG_FILES[@]}"; do
    if [ -f "$file" ]; then
        echo -e "${GREEN}✓${NC} $file"
        ((TESTS_PASSED++))
    else
        echo -e "${RED}✗${NC} $file (missing)"
        ((TESTS_FAILED++))
    fi
done
echo ""

echo "6. Checking documentation..."
DOC_FILES=(
    "README.md"
    "QUICKSTART.md"
    "IMPLEMENTATION.md"
    "COMPARISON.md"
    "SUMMARY.md"
)

for file in "${DOC_FILES[@]}"; do
    if [ -f "$file" ]; then
        echo -e "${GREEN}✓${NC} $file"
        ((TESTS_PASSED++))
    else
        echo -e "${RED}✗${NC} $file (missing)"
        ((TESTS_FAILED++))
    fi
done
echo ""

echo "7. Checking scripts..."
SCRIPT_FILES=(
    "run.sh"
    "run-grpc.sh"
    "run-jsonrpc.sh"
)

for file in "${SCRIPT_FILES[@]}"; do
    if [ -f "$file" ] && [ -x "$file" ]; then
        echo -e "${GREEN}✓${NC} $file (executable)"
        ((TESTS_PASSED++))
    elif [ -f "$file" ]; then
        echo -e "${YELLOW}⚠${NC} $file (not executable)"
        ((TESTS_PASSED++))
    else
        echo -e "${RED}✗${NC} $file (missing)"
        ((TESTS_FAILED++))
    fi
done
echo ""

echo "================================"
echo "Test Summary"
echo "================================"
echo -e "Tests Passed: ${GREEN}$TESTS_PASSED${NC}"
echo -e "Tests Failed: ${RED}$TESTS_FAILED${NC}"
echo ""

if [ $TESTS_FAILED -eq 0 ]; then
    echo -e "${GREEN}All tests passed! ✓${NC}"
    echo ""
    echo "Agent2 is ready to use!"
    echo ""
    echo "To run the agent:"
    echo "  REST mode:     ./run.sh"
    echo "  gRPC mode:     ./run-grpc.sh"
    echo "  JSON-RPC mode: ./run-jsonrpc.sh"
    echo ""
    exit 0
else
    echo -e "${RED}Some tests failed! ✗${NC}"
    echo ""
    exit 1
fi
