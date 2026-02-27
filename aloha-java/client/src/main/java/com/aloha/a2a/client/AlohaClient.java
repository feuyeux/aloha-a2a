package com.aloha.a2a.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.a2a.A2A;
import io.a2a.client.ClientEvent;
import io.a2a.client.MessageEvent;
import io.a2a.client.TaskEvent;
import io.a2a.client.TaskUpdateEvent;
import io.a2a.client.config.ClientConfig;
import io.a2a.client.http.A2ACardResolver;
import io.a2a.client.transport.grpc.GrpcTransport;
import io.a2a.client.transport.grpc.GrpcTransportConfig;
import io.a2a.client.transport.jsonrpc.JSONRPCTransport;
import io.a2a.client.transport.jsonrpc.JSONRPCTransportConfig;
import io.a2a.client.transport.rest.RestTransport;
import io.a2a.client.transport.rest.RestTransportConfig;
import io.a2a.spec.*;
import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A2A Host client with configurable transport support.
 * <p>
 * This client can connect to A2A agents using three different transport protocols:
 * <ul>
 *   <li><b>gRPC</b>: High-performance binary protocol (port 11000)</li>
 *   <li><b>JSON-RPC</b>: WebSocket-based protocol (port 11001)</li>
 *   <li><b>REST (HTTP+JSON)</b>: RESTful HTTP protocol (port 11002)</li>
 * </ul>
 * <p>
 * Usage flow:
 * <ol>
 *   <li>Create client with server URL and transport type</li>
 *   <li>Call {@link #initialize()} to fetch agent card and setup transports</li>
 *   <li>Call {@link #sendMessage(String)} to send messages and receive responses</li>
 *   <li>Call {@link #close()} to cleanup resources</li>
 * </ol>
 * <p>
 * The client handles:
 * <ul>
 *   <li>Agent card discovery and validation</li>
 *   <li>Transport-specific configuration</li>
 *   <li>Message streaming and event handling</li>
 *   <li>Error handling and resource cleanup</li>
 * </ul>
 *
 * @see TransportType
 */
public class AlohaClient {

    private static final Logger logger = LoggerFactory.getLogger(AlohaClient.class);

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_GRPC_PORT = 11000;
    private static final int DEFAULT_JSONRPC_PORT = 11001;
    private static final int DEFAULT_REST_PORT = 11002;
    private static final int DEFAULT_GRPC_AGENT_CARD_PORT = 11001;
    private static final String DEFAULT_MESSAGE = "Roll a 6-sided dice";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    public static final String HTTP = "http://";

    private final String serverUrl;
    private final TransportType transportType;
    private final Integer agentCardPort;
    private final List<ManagedChannel> managedChannels = new ArrayList<>();
    private io.a2a.client.Client a2aClient;

    /**
     * Creates a new client with default transport (supports all available transports).
     * <p>
     * When no specific transport type is specified, the client will configure
     * all available transports and let the A2A SDK choose the best one based
     * on the agent's preferred transport.
     *
     * @param serverUrl the server URL to connect to (e.g., "localhost:11000" for gRPC)
     */
    public AlohaClient(String serverUrl) {
        this(serverUrl, null, null);
    }

    /**
     * Creates a new client with a specific transport type.
     * <p>
     * This constructor allows you to explicitly specify which transport protocol
     * to use when connecting to the agent.
     *
     * @param serverUrl     the server URL to connect to (format depends on transport type)
     * @param transportType the transport type to use, or null to support all transports
     */
    public AlohaClient(String serverUrl, TransportType transportType) {
        this(serverUrl, transportType, null);
    }

    /**
     * Creates a new client with a specific transport type and agent card port.
     *
     * @param serverUrl     the server URL to connect to
     * @param transportType the transport type to use, or null to support all transports
     * @param agentCardPort separate port for the agent card HTTP endpoint (gRPC mode), or null to use serverUrl port
     */
    public AlohaClient(String serverUrl, TransportType transportType, Integer agentCardPort) {
        this.serverUrl = serverUrl;
        this.transportType = transportType;
        this.agentCardPort = agentCardPort;
    }

    /**
     * Initializes the client by fetching the agent card and setting up transports.
     * <p>
     * This method performs the following steps:
     * <ol>
     *   <li>Fetches the public agent card from the agent's well-known endpoint</li>
     *   <li>Validates the agent card and extracts transport information</li>
     *   <li>Configures the appropriate transport(s) based on the transport type</li>
     *   <li>Creates the A2A client instance ready for message sending</li>
     * </ol>
     * <p>
     * Must be called before {@link #sendMessage(String)}.
     *
     * @throws Exception if agent card fetch fails, transport configuration fails,
     *                   or the agent doesn't support the requested transport
     */
    public void initialize() throws Exception {
        logger.info("Connecting to agent at: {} with transport: {}",
                serverUrl, transportType != null ? transportType : "all");

        // Build the correct URL for fetching the agent card
        // For gRPC, we need to use HTTP URL to fetch the card
        String agentCardUrl = buildAgentCardUrl();

        // Fetch the public agent card
        AgentCard publicAgentCard = new A2ACardResolver(agentCardUrl).getAgentCard();
        logger.info("Successfully fetched public agent card:");
        logger.info(objectMapper.writeValueAsString(publicAgentCard));

        // Create channel factory for gRPC transport
        Function<String, Channel> channelFactory = agentUrl -> {
            ManagedChannel channel = ManagedChannelBuilder.forTarget(agentUrl)
                    .usePlaintext()
                    .build();
            managedChannels.add(channel);
            return channel;
        };

        ClientConfig clientConfig = new ClientConfig.Builder()
                .setAcceptedOutputModes(List.of("Text"))
                .build();

        // Create the client with configured transports based on transport type
        var clientBuilder = io.a2a.client.Client.builder(publicAgentCard);

        if (transportType == null || transportType == TransportType.GRPC) {
            clientBuilder.withTransport(GrpcTransport.class, new GrpcTransportConfig(channelFactory));
            logger.info("Configured gRPC transport");
        }

        if (transportType == null || transportType == TransportType.JSONRPC) {
            clientBuilder.withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig());
            logger.info("Configured JSON-RPC transport");
        }

        if (transportType == null || transportType == TransportType.REST) {
            clientBuilder.withTransport(RestTransport.class, new RestTransportConfig());
            logger.info("Configured REST (HTTP+JSON) transport");
        }

        a2aClient = clientBuilder.clientConfig(clientConfig).build();

        logger.info("Client initialized successfully");
    }

    /**
     * Builds the correct URL for fetching the agent card.
     * <p>
     * The agent card is always served over HTTP, even for gRPC agents.
     * This method handles the URL conversion based on the transport type:
     * <ul>
     *   <li><b>gRPC</b>: Converts "host:port" to "http://host:port" (same port for agent card)</li>
     *   <li><b>JSON-RPC/REST</b>: Uses the provided HTTP URL directly</li>
     * </ul>
     *
     * @return the HTTP URL for fetching the agent card
     */
    private String buildAgentCardUrl() {
        // If serverUrl already starts with http:// or https://, use it directly
        if (serverUrl.startsWith(HTTP) || serverUrl.startsWith("https://")) {
            return serverUrl;
        }

        // For gRPC format (host:port), convert to HTTP URL
        if (transportType == TransportType.GRPC) {
            // Parse host:port format
            String[] parts = serverUrl.split(":");
            if (parts.length == 2) {
                String host = parts[0];
                // Use agentCardPort if specified, otherwise fall back to gRPC port
                String port = agentCardPort != null ? String.valueOf(agentCardPort) : parts[1];
                logger.info("Converting gRPC URL {} to HTTP URL for agent card fetch (port {})",
                        serverUrl, port);
                return HTTP + host + ":" + port;
            }
        }

        // Fallback: assume it's HTTP
        return HTTP + serverUrl;
    }

    /**
     * Sends a message to the agent and waits for the response.
     * <p>
     * This method:
     * <ol>
     *   <li>Creates a new A2A client with event consumers for this message</li>
     *   <li>Sends the message to the agent</li>
     *   <li>Waits for the agent to process the message and return a response</li>
     *   <li>Handles streaming updates (status, artifacts) during processing</li>
     *   <li>Returns the final response text</li>
     * </ol>
     * <p>
     * The method blocks until the agent completes processing or an error occurs.
     *
     * @param messageText the message text to send to the agent
     * @return the agent's response text
     * @throws Exception if message sending fails, agent processing fails,
     *                   or the response cannot be retrieved
     */
    public String sendMessage(String messageText) throws Exception {
        CompletableFuture<String> messageResponse = new CompletableFuture<>();

        // Rebuild client with consumers for this message
        String agentCardUrl = buildAgentCardUrl();
        AgentCard publicAgentCard = new A2ACardResolver(agentCardUrl).getAgentCard();

        // Create consumers for handling client events
        List<BiConsumer<ClientEvent, AgentCard>> consumers = createConsumers(messageResponse);

        // Create error handler for streaming errors
        Consumer<Throwable> streamingErrorHandler = error -> {
            // Ignore expected cleanup errors after successful completion
            if (messageResponse.isDone() && !messageResponse.isCompletedExceptionally()) {
                // Response already completed successfully, this is just cleanup
                logger.debug("Ignoring post-completion streaming error: {}", error.getMessage());
                return;
            }

            // Check for expected connection closure errors
            String errorMsg = error.getMessage();
            if (errorMsg != null && (
                    errorMsg.contains("Stream") && errorMsg.contains("cancelled") ||
                            errorMsg.contains("selector manager closed") ||
                            errorMsg.contains("Connection closed"))) {
                logger.debug("Connection closed after response completion: {}", errorMsg);
                return;
            }

            logger.error("Streaming error occurred: {}", error.getMessage(), error);
            messageResponse.completeExceptionally(error);
        };

        ClientConfig clientConfig = new ClientConfig.Builder()
                .setAcceptedOutputModes(List.of("Text"))
                .build();

        // Create the client with configured transports based on transport type
        var clientBuilder = io.a2a.client.Client.builder(publicAgentCard)
                .addConsumers(consumers)
                .streamingErrorHandler(streamingErrorHandler);

        if (transportType == null || transportType == TransportType.GRPC) {
            Function<String, Channel> channelFactory = agentUrl -> {
                ManagedChannel channel = ManagedChannelBuilder.forTarget(agentUrl)
                        .usePlaintext()
                        .build();
                managedChannels.add(channel);
                return channel;
            };
            clientBuilder.withTransport(GrpcTransport.class, new GrpcTransportConfig(channelFactory));
        }

        if (transportType == null || transportType == TransportType.JSONRPC) {
            clientBuilder.withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig());
        }

        if (transportType == null || transportType == TransportType.REST) {
            clientBuilder.withTransport(RestTransport.class, new RestTransportConfig());
        }

        a2aClient = clientBuilder.clientConfig(clientConfig).build();

        // Create and send the message
        Message message = A2A.toUserMessage(messageText);

        logger.info("Sending message: {}", messageText);
        a2aClient.sendMessage(message);
        logger.info("Message sent successfully. Waiting for response...");

        try {
            // Wait for response
            String responseText = messageResponse.get();
            logger.info("Final response: {}", responseText);
            return responseText;
        } catch (InterruptedException e) {
            logger.error("Interrupted while waiting for response: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
            throw e;
        } catch (ExecutionException e) {
            logger.error("Failed to get response: {}", e.getCause().getMessage(), e.getCause());
            throw e;
        }
    }

    /**
     * Creates event consumers for handling agent responses.
     * <p>
     * These consumers handle different types of events from the agent:
     * <ul>
     *   <li><b>MessageEvent</b>: Direct message responses</li>
     *   <li><b>TaskStatusUpdateEvent</b>: Task status changes (submitted, working, completed, failed)</li>
     *   <li><b>TaskArtifactUpdateEvent</b>: Streaming artifact updates during processing</li>
     * </ul>
     *
     * @param messageResponse the future to complete when the final response is received
     * @return a list of event consumers to handle agent events
     */
    private List<BiConsumer<ClientEvent, AgentCard>> createConsumers(
            CompletableFuture<String> messageResponse) {
        List<BiConsumer<ClientEvent, AgentCard>> consumers = new ArrayList<>();

        consumers.add((event, agentCard) -> {
            if (event instanceof MessageEvent messageEvent) {
                Message responseMessage = messageEvent.getMessage();
                String text = extractTextFromParts(responseMessage.getParts());
                logger.info("Received message: {}", text);
                messageResponse.complete(text);

            } else if (event instanceof TaskUpdateEvent taskUpdateEvent) {
                UpdateEvent updateEvent = taskUpdateEvent.getUpdateEvent();

                if (updateEvent instanceof TaskStatusUpdateEvent taskStatusUpdateEvent) {
                    logger.info("Received status-update: {}",
                            taskStatusUpdateEvent.getStatus().state().asString());

                    if (taskStatusUpdateEvent.isFinal()) {
                        StringBuilder textBuilder = new StringBuilder();
                        List<Artifact> artifacts = taskUpdateEvent.getTask().getArtifacts();
                        for (Artifact artifact : artifacts) {
                            textBuilder.append(extractTextFromParts(artifact.parts()));
                        }
                        String text = textBuilder.toString();
                        messageResponse.complete(text);
                    }

                } else if (updateEvent instanceof TaskArtifactUpdateEvent taskArtifactUpdateEvent) {
                    List<Part<?>> parts = taskArtifactUpdateEvent.getArtifact().parts();
                    String text = extractTextFromParts(parts);
                    logger.info("Received artifact-update: {}", text);
                }

            } else if (event instanceof TaskEvent taskEvent) {
                logger.info("Received task event: {}", taskEvent.getTask().getId());
            }
        });

        return consumers;
    }

    /**
     * Extracts text content from message parts.
     * <p>
     * This method iterates through all parts in the message and concatenates
     * the text from any {@link TextPart} instances.
     *
     * @param parts the list of message parts to extract text from
     * @return the concatenated text content
     */
    private String extractTextFromParts(List<Part<?>> parts) {
        StringBuilder textBuilder = new StringBuilder();
        if (parts != null) {
            for (Part<?> part : parts) {
                if (part instanceof TextPart textPart) {
                    textBuilder.append(textPart.getText());
                }
            }
        }
        return textBuilder.toString();
    }

    /**
     * Cleans up client resources.
     * <p>
     * This method should be called when the client is no longer needed.
     * It performs the following cleanup:
     * <ul>
     *   <li>Closes the A2A client</li>
     *   <li>Shuts down all managed gRPC channels</li>
     *   <li>Waits for graceful termination (up to 5 seconds)</li>
     *   <li>Forces shutdown if graceful termination fails</li>
     * </ul>
     */
    public void close() {
        logger.info("Cleaning up resources...");

        // Close the client
        try {
            if (a2aClient instanceof AutoCloseable) {
                ((AutoCloseable) a2aClient).close();
            }
        } catch (Exception e) {
            logger.warn("Error closing client: {}", e.getMessage(), e);
        }

        // Shutdown all managed channels
        for (ManagedChannel channel : managedChannels) {
            try {
                channel.shutdown();
                if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.warn("Channel did not terminate gracefully, forcing shutdown");
                    channel.shutdownNow();
                    if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                        logger.warn("Channel did not terminate after forced shutdown");
                    }
                }
            } catch (InterruptedException e) {
                logger.warn("Interrupted while shutting down channel: {}", e.getMessage(), e);
                channel.shutdownNow();
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.warn("Error shutting down channel: {}", e.getMessage(), e);
            }
        }

        logger.info("Resource cleanup completed");
    }

    // ==================== CLI Entry Point ====================

    /**
     * Main entry point for the A2A client.
     * <p>
     * Parses command-line arguments, creates a client, sends a message to the agent,
     * and displays the response.
     * <p>
     * Usage:
     * <pre>
     * mvn exec:java -Dexec.args="--transport grpc --port 11000 --message 'Roll a dice'"
     * </pre>
     * <p>
     * Command-line arguments:
     * <ul>
     *   <li>{@code --transport <grpc|jsonrpc|rest>}: Transport protocol (default: grpc)</li>
     *   <li>{@code --host <hostname>}: Agent hostname (default: localhost)</li>
     *   <li>{@code --port <port>}: Agent port</li>
     *   <li>{@code --agent-card-port <port>}: Port for agent card (gRPC mode)</li>
     *   <li>{@code --message <text>}: Message to send</li>
     *   <li>{@code --help, -h}: Show help message</li>
     * </ul>
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        String host = DEFAULT_HOST;
        Integer port = null;
        Integer agentCardPort = null;
        String transport = "grpc";
        String message = DEFAULT_MESSAGE;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--host":
                    if (i + 1 < args.length) {
                        host = args[++i];
                    } else {
                        logger.error("Error: --host requires a value");
                        printUsageAndExit();
                    }
                    break;
                case "--port":
                    if (i + 1 < args.length) {
                        try {
                            port = Integer.parseInt(args[++i]);
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
                        transport = args[++i].toLowerCase();
                        if (!transport.equals("jsonrpc") && !transport.equals("grpc") && !transport.equals("rest")) {
                            logger.error("Error: --transport must be one of: jsonrpc, grpc, rest");
                            printUsageAndExit();
                        }
                    } else {
                        logger.error("Error: --transport requires a value");
                        printUsageAndExit();
                    }
                    break;
                case "--agent-card-port":
                    if (i + 1 < args.length) {
                        agentCardPort = Integer.parseInt(args[++i]);
                    } else {
                        logger.error("Error: --agent-card-port requires a value");
                        printUsageAndExit();
                    }
                    break;
                case "--message":
                    if (i + 1 < args.length) {
                        message = args[++i];
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

        if (port == null) {
            port = switch (transport) {
                case "grpc" -> DEFAULT_GRPC_PORT;
                case "jsonrpc" -> DEFAULT_JSONRPC_PORT;
                case "rest" -> DEFAULT_REST_PORT;
                default -> DEFAULT_GRPC_PORT;
            };
        }
        if (agentCardPort == null && "grpc".equals(transport)) {
            agentCardPort = DEFAULT_GRPC_AGENT_CARD_PORT;
        }

        String serverUrl = buildServerUrl(host, port, transport);
        logger.info("Configuration:");
        logger.info("  Host: {}", host);
        logger.info("  Port: {}", port);
        logger.info("  Transport: {}", transport);
        logger.info("  Server URL: {}", serverUrl);
        logger.info("  Message: {}", message);

        TransportType transportType = null;
        try {
            transportType = TransportType.fromString(transport);
        } catch (IllegalArgumentException e) {
            logger.error("Error: {}", e.getMessage());
            printUsageAndExit();
        }

        AlohaClient client = new AlohaClient(serverUrl, transportType, agentCardPort);
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

    private static String buildServerUrl(String host, int port, String transport) {
        return switch (transport) {
            case "grpc" -> host + ":" + port;
            case "jsonrpc", "rest" -> "http://" + host + ":" + port;
            default -> "http://" + host + ":" + port;
        };
    }

    private static void printUsageAndExit() {
        System.out.println("Usage: java -jar client.jar [OPTIONS]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --host <hostname>           Agent hostname (default: localhost)");
        System.out.println("  --port <port>               Agent port (default: 11000 for gRPC, 11001 for JSON-RPC, 11002 for REST)");
        System.out.println("  --transport <protocol>      Transport protocol: jsonrpc, grpc, or rest (default: grpc)");
        System.out.println("  --agent-card-port <port>    Port for agent card (gRPC mode, default: 11001)");
        System.out.println("  --message <text>            Message to send (default: \"Roll a 6-sided dice\")");
        System.out.println("  --help, -h                  Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar client.jar --message \"Roll a 20-sided dice\"");
        System.out.println("  java -jar client.jar --transport jsonrpc --port 11001 --message \"Is 17 prime?\"");
        System.out.println("  java -jar client.jar --transport rest --port 11002 --message \"Check if 2, 7, 11 are prime\"");
        System.out.println();
        System.exit(0);
    }
}
