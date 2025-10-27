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
 * A2A Host client with multi-transport support.
 * Supports JSON-RPC 2.0, gRPC, and REST transports.
 */
public class Client {
    
    private static final Logger logger = LoggerFactory.getLogger(Client.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private final String serverUrl;
    private final List<ManagedChannel> managedChannels = new ArrayList<>();
    private io.a2a.client.Client a2aClient;
    
    /**
     * Create a new client.
     *
     * @param serverUrl The server URL to connect to
     */
    public Client(String serverUrl) {
        this.serverUrl = serverUrl;
    }
    
    /**
     * Initialize the client by fetching agent card and setting up transports.
     *
     * @throws Exception if initialization fails
     */
    public void initialize() throws Exception {
        logger.info("Connecting to agent at: {}", serverUrl);
        
        // Fetch the public agent card
        AgentCard publicAgentCard = new A2ACardResolver(serverUrl).getAgentCard();
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
        
        // Create the client with multi-transport support
        a2aClient = io.a2a.client.Client.builder(publicAgentCard)
                .withTransport(GrpcTransport.class, new GrpcTransportConfig(channelFactory))
                .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig())
                .clientConfig(clientConfig)
                .build();
        
        logger.info("Client initialized successfully");
    }
    
    /**
     * Send a message to the agent and wait for response.
     *
     * @param messageText The message text to send
     * @return The agent's response
     * @throws Exception if sending fails
     */
    public String sendMessage(String messageText) throws Exception {
        CompletableFuture<String> messageResponse = new CompletableFuture<>();
        
        // Rebuild client with consumers for this message
        AgentCard publicAgentCard = new A2ACardResolver(serverUrl).getAgentCard();
        
        // Create consumers for handling client events
        List<BiConsumer<ClientEvent, AgentCard>> consumers = createConsumers(messageResponse);
        
        // Create error handler for streaming errors
        Consumer<Throwable> streamingErrorHandler = error -> {
            logger.error("Streaming error occurred: {}", error.getMessage(), error);
            messageResponse.completeExceptionally(error);
        };
        
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
        
        // Create the client with consumers
        a2aClient = io.a2a.client.Client.builder(publicAgentCard)
                .addConsumers(consumers)
                .streamingErrorHandler(streamingErrorHandler)
                .withTransport(GrpcTransport.class, new GrpcTransportConfig(channelFactory))
                .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig())
                .clientConfig(clientConfig)
                .build();
        
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
     * Create event consumers for handling agent responses.
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
     * Extract text from message parts.
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
     * Clean up client resources.
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
