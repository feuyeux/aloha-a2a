#!/bin/bash

echo "Starting Agent2 in REST mode..."
mvn spring-boot:run -Dspring-boot.run.profiles=rest
