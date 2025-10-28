#!/bin/bash

echo "Starting Agent2 in JSON-RPC mode..."
mvn spring-boot:run -Dspring-boot.run.profiles=jsonrpc
