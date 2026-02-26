package com.aloha.a2a.server.transport;

import io.a2a.server.TransportMetadata;

/**
 * SPI provider that registers gRPC as an available transport
 * for the A2A SDK's AgentCardValidator.
 */
public class GrpcTransportMetadata implements TransportMetadata {

    @Override
    public String getTransportProtocol() {
        return "GRPC";
    }
}
