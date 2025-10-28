#!/bin/bash

echo "Starting Agent2 in gRPC mode..."
mvn spring-boot:run -Dspring-boot.run.profiles=grpc
