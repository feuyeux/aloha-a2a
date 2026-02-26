package com.aloha.a2a.server.transport;

import io.a2a.server.TransportMetadata;

/**
 * SPI provider that registers JSON-RPC as an available transport
 * for the A2A SDK's AgentCardValidator.
 */
public class JsonRpcTransportMetadata implements TransportMetadata {

    @Override
    public String getTransportProtocol() {
        return "JSONRPC";
    }
}
