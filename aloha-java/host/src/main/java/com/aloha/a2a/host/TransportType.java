package com.aloha.a2a.host;

/**
 * Enumeration of supported A2A transport types.
 * <p>
 * This enum defines the three transport protocols supported by the A2A Java implementation:
 * <ul>
 *   <li><b>gRPC</b>: High-performance binary protocol using Protocol Buffers</li>
 *   <li><b>JSON-RPC</b>: WebSocket-based protocol using JSON-RPC 2.0</li>
 *   <li><b>REST</b>: RESTful HTTP protocol using JSON (HTTP+JSON)</li>
 * </ul>
 * <p>
 * Each transport type has a corresponding port:
 * <ul>
 *   <li>gRPC: 11000</li>
 *   <li>JSON-RPC: 11001</li>
 *   <li>REST: 11002</li>
 * </ul>
 *
 * @see Client
 * @see Main
 */
public enum TransportType {
    /**
     * JSON-RPC 2.0 transport over WebSocket.
     * <p>
     * Uses WebSocket for bidirectional communication with JSON-RPC 2.0 protocol.
     * Default port: 11001
     */
    JSONRPC("jsonrpc"),
    
    /**
     * gRPC transport using Protocol Buffers.
     * <p>
     * High-performance binary protocol with strong typing and code generation.
     * Default port: 11000
     */
    GRPC("grpc"),
    
    /**
     * REST transport over HTTP using JSON (HTTP+JSON).
     * <p>
     * RESTful HTTP protocol with JSON payloads for request/response.
     * Default port: 11002
     */
    REST("rest");
    
    private final String value;
    
    TransportType(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    /**
     * Parses a transport type from its string value.
     * <p>
     * This method performs case-insensitive matching against the transport type values.
     *
     * @param value the string value to parse (e.g., "grpc", "jsonrpc", "rest")
     * @return the corresponding {@link TransportType} enum constant
     * @throws IllegalArgumentException if the value is not a recognized transport type
     */
    public static TransportType fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Transport type cannot be null");
        }
        
        for (TransportType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        
        throw new IllegalArgumentException("Unknown transport type: " + value + 
                ". Supported types: jsonrpc, grpc, rest");
    }
    
    @Override
    public String toString() {
        return value;
    }
}