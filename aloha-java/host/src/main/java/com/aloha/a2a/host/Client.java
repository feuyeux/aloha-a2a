package com.aloha.a2a.host;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.a2a.A2A;
import io.a2a.client.*;
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
 * @see Main
 */
public class Client {

    private static final Logger logger = LoggerFactory.getLogger(Client.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    public static final String HTTP = "http://";

    private final String serverUrl;
    private final TransportType transportType;
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
    public Client(String serverUrl) {
        this(serverUrl, null);
    }
    
    /**
     * Creates a new client with a specific transport type.
     * <p>
     * This constructor allows you to explicitly specify which transport protocol
     * to use when connecting to the agent.
     *
     * @param serverUrl the server URL to connect to (format depends on transport type)
     * @param transportType the transport type to use, or null to support all transports
     */
    public Client(String serverUrl, TransportType transportType) {
        this.serverUrl = serverUrl;
        this.transportType = transportType;
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
     *   <li><b>gRPC</b>: Converts "host:port" to "<a href="http://host:8080">...</a>" (agent card port)</li>
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
        // Agent card is available on HTTP port 8080 for gRPC mode
        if (transportType == TransportType.GRPC) {
            // Parse host:port format
            String[] parts = serverUrl.split(":");
            if (parts.length == 2) {
                String host = parts[0];
                // Agent card for gRPC mode is on port 8080
                int agentCardPort = 8080;
                logger.info("Converting gRPC URL {} to HTTP URL for agent card fetch (using port {})", 
                        serverUrl, agentCardPort);
                return HTTP + host + ":" + agentCardPort;
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
}
