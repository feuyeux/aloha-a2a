package com.aloha.a2a.host;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for the A2A Host client.
 * <p>
 * This class provides a command-line interface for sending messages to A2A agents
 * using different transport protocols (gRPC, JSON-RPC, or REST).
 * <p>
 * Usage:
 * <pre>
 * mvn exec:java -Dexec.args="--transport grpc --port 11000 --message 'Roll a dice'"
 * </pre>
 * <p>
 * Command-line arguments:
 * <ul>
 *   <li>{@code --transport <grpc|jsonrpc|rest>}: Transport protocol to use (default: grpc)</li>
 *   <li>{@code --host <hostname>}: Agent hostname (default: localhost)</li>
 *   <li>{@code --port <port>}: Agent port (default: 11000 for gRPC, 11001 for JSON-RPC, 11002 for REST)</li>
 *   <li>{@code --message <text>}: Message to send to the agent (default: "Roll a 6-sided dice")</li>
 *   <li>{@code --help, -h}: Show help message</li>
 * </ul>
 *
 * @see Client
 * @see TransportType
 */
public class Main {
    
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    
    private static final String DEFAULT_HOST = "localhost";
    // Port configuration according to specification:
    // Java: gRPC=11000, JSON-RPC=11001, REST=11002
    private static final int DEFAULT_GRPC_PORT = 11000;
    private static final int DEFAULT_JSONRPC_PORT = 11001;
    private static final int DEFAULT_REST_PORT = 11002;
    private static final String DEFAULT_MESSAGE = "Roll a 6-sided dice";
    
    /**
     * Main entry point for the A2A Host client.
     * <p>
     * Parses command-line arguments, creates a client, sends a message to the agent,
     * and displays the response.
     *
     * @param args command-line arguments (see class documentation for details)
     */
    public static void main(String[] args) {
        String host = DEFAULT_HOST;
        Integer port = null; // Will be set based on transport if not specified
        String transport = "grpc"; // Default to gRPC
        String message = DEFAULT_MESSAGE;
        
        // Parse command-line arguments
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--host":
                    if (i + 1 < args.length) {
                        host = args[i + 1];
                        i++;
                    } else {
                        logger.error("Error: --host requires a value");
                        printUsageAndExit();
                    }
                    break;
                    
                case "--port":
                    if (i + 1 < args.length) {
                        try {
                            port = Integer.parseInt(args[i + 1]);
                            i++;
                        } catch (NumberFormatException e) {
                            logger.error("Error: --port must be a number");
                            printUsageAndExit();
                        }
                    } else {
                        logger.error("Error: --port requires a value");
                        printUsageAndExit();
                    }
                    break;
                    
                case "--transport":
                    if (i + 1 < args.length) {
                        transport = args[i + 1].toLowerCase();
                        if (!transport.equals("jsonrpc") && !transport.equals("grpc") && !transport.equals("rest")) {
                            logger.error("Error: --transport must be one of: jsonrpc, grpc, rest");
                            printUsageAndExit();
                        }
                        i++;
                    } else {
                        logger.error("Error: --transport requires a value");
                        printUsageAndExit();
                    }
                    break;
                    
                case "--message":
                    if (i + 1 < args.length) {
                        message = args[i + 1];
                        i++;
                    } else {
                        logger.error("Error: --message requires a value");
                        printUsageAndExit();
                    }
                    break;
                    
                case "--help":
                case "-h":
                    printUsageAndExit();
                    break;
                    
                default:
                    logger.error("Error: Unknown argument: {}", args[i]);
                    printUsageAndExit();
            }
        }
        
        // Set default port based on transport if not specified
        if (port == null) {
            port = switch (transport) {
                case "grpc" -> DEFAULT_GRPC_PORT;
                case "jsonrpc" -> DEFAULT_JSONRPC_PORT;
                case "rest" -> DEFAULT_REST_PORT;
                default -> DEFAULT_GRPC_PORT;
            };
        }
        
        // Build server URL
        String serverUrl = buildServerUrl(host, port, transport);
        
        logger.info("Configuration:");
        logger.info("  Host: {}", host);
        logger.info("  Port: {}", port);
        logger.info("  Transport: {}", transport);
        logger.info("  Server URL: {}", serverUrl);
        logger.info("  Message: {}", message);
        
        // Create and run client
        TransportType transportType = null;
        try {
            transportType = TransportType.fromString(transport);
        } catch (IllegalArgumentException e) {
            logger.error("Error: {}", e.getMessage());
            printUsageAndExit();
        }
        
        Client client = new Client(serverUrl, transportType);
        
        try {
            client.initialize();
            String response = client.sendMessage(message);
            
            System.out.println("\n=== Agent Response ===");
            System.out.println(response);
            System.out.println("======================\n");
            
        } catch (Exception e) {
            logger.error("An error occurred: {}", e.getMessage(), e);
            System.exit(1);
        } finally {
            client.close();
        }
    }
    
    /**
     * Builds the server URL based on the transport protocol.
     * <p>
     * URL format varies by transport:
     * <ul>
     *   <li><b>gRPC</b>: "host:port" (e.g., "localhost:11000")</li>
     *   <li><b>JSON-RPC/REST</b>: "http://host:port" (e.g., "http://localhost:11001")</li>
     * </ul>
     *
     * @param host the hostname or IP address
     * @param port the port number
     * @param transport the transport protocol ("grpc", "jsonrpc", or "rest")
     * @return the formatted server URL
     */
    private static String buildServerUrl(String host, int port, String transport) {
        return switch (transport) {
            case "grpc" -> host + ":" + port;
            case "jsonrpc", "rest" -> "http://" + host + ":" + port;
            default -> "http://" + host + ":" + port;
        };
    }
    
    /**
     * Prints usage information and exits the program.
     * <p>
     * Displays help text with all available command-line options and examples.
     */
    private static void printUsageAndExit() {
        System.out.println("Usage: java -jar host.jar [OPTIONS]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --host <hostname>           Agent hostname (default: localhost)");
        System.out.println("  --port <port>               Agent port (default: 11000 for gRPC, 11001 for JSON-RPC, 11002 for REST)");
        System.out.println("  --transport <protocol>      Transport protocol: jsonrpc, grpc, or rest (default: grpc)");
        System.out.println("  --message <text>            Message to send (default: \"Roll a 6-sided dice\")");
        System.out.println("  --help, -h                  Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  # Send message using gRPC (default)");
        System.out.println("  java -jar host.jar --message \"Roll a 20-sided dice\"");
        System.out.println();
        System.out.println("  # Send message using JSON-RPC");
        System.out.println("  java -jar host.jar --transport jsonrpc --port 11001 --message \"Is 17 prime?\"");
        System.out.println();
        System.out.println("  # Send message using REST");
        System.out.println("  java -jar host.jar --transport rest --port 11002 --message \"Check if 2, 7, 11 are prime\"");
        System.out.println();
        System.exit(0);
    }
}
