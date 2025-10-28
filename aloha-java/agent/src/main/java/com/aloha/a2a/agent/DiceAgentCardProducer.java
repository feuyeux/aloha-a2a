package com.aloha.a2a.agent;

import io.a2a.server.PublicAgentCard;
import io.a2a.spec.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Producer for Dice Agent Card configuration.
 * <p>
 * This class generates the A2A Agent Card, which provides metadata and capabilities
 * information for agent discovery. The card is dynamically generated based on the
 * configured transport mode (gRPC, JSON-RPC, or REST).
 * <p>
 * The Agent Card includes:
 * <ul>
 *   <li>Agent name, description, and version</li>
 *   <li>Supported capabilities (streaming, push notifications, etc.)</li>
 *   <li>Available skills (roll dice, check prime)</li>
 *   <li>Transport interfaces and endpoints</li>
 *   <li>Protocol version information</li>
 * </ul>
 * <p>
 * The card is accessible via the standard A2A endpoint:
 * {@code /.well-known/agent-card.json}
 *
 * @see io.a2a.server.PublicAgentCard
 * @see io.a2a.spec.AgentCard
 */
@ApplicationScoped
public class DiceAgentCardProducer {
    
    private static final Logger logger = LoggerFactory.getLogger(DiceAgentCardProducer.class);
    
    @Inject
    @ConfigProperty(name = "transport.mode", defaultValue = "grpc")
    String transportMode;
    
    @Inject
    @ConfigProperty(name = "quarkus.grpc.server.port", defaultValue = "11000")
    int grpcPort;
    
    @Inject
    @ConfigProperty(name = "quarkus.http.port", defaultValue = "11001")
    int httpPort;
    
    @ConfigProperty(name = "agent.name", defaultValue = "Dice Agent")
    String agentName;
    
    @ConfigProperty(name = "agent.description", 
            defaultValue = "An agent that can roll arbitrary dice and check prime numbers")
    String agentDescription;
    
    @ConfigProperty(name = "agent.version", defaultValue = "1.0.0")
    String agentVersion;
    
    /**
     * Produces the agent card for the dice agent based on the configured transport mode.
     * <p>
     * This method is called by the A2A framework to generate the agent card.
     * The card content varies based on the transport mode:
     * <ul>
     *   <li><b>gRPC mode</b>: Includes gRPC interface on port 11000</li>
     *   <li><b>JSON-RPC mode</b>: Includes JSON-RPC interface on port 11001</li>
     *   <li><b>REST mode</b>: Includes HTTP+JSON interface on port 11002</li>
     * </ul>
     * <p>
     * The transport mode is determined by the Quarkus profile used at startup:
     * <ul>
     *   <li>{@code -Dquarkus.profile=grpc} for gRPC mode</li>
     *   <li>{@code -Dquarkus.profile=jsonrpc} for JSON-RPC mode</li>
     *   <li>{@code -Dquarkus.profile=rest} for REST mode</li>
     * </ul>
     *
     * @return the configured agent card with transport-specific endpoints
     */
    @Produces
    @PublicAgentCard
    public AgentCard agentCard() {
        List<AgentSkill> skills = List.of(
                new AgentSkill.Builder()
                        .id("roll-dice")
                        .name("Roll Dice")
                        .description("Rolls an N-sided dice and returns a random number between 1 and N")
                        .tags(List.of("dice", "random", "games"))
                        .examples(List.of(
                                "Roll a 6-sided dice",
                                "Roll a 20-sided dice",
                                "Can you roll a d12?"))
                        .build(),
                new AgentSkill.Builder()
                        .id("check-prime")
                        .name("Prime Checker")
                        .description("Checks if given numbers are prime")
                        .tags(List.of("math", "prime", "numbers"))
                        .examples(List.of(
                                "Is 17 prime?",
                                "Check if 2, 4, 7, 9, 11 are prime",
                                "Which of these numbers are prime: 13, 15, 19"))
                        .build());
        
        // Build agent card based on transport mode
        AgentCard.Builder cardBuilder = new AgentCard.Builder()
                .name(agentName)
                .description(agentDescription)
                .version(agentVersion)
                .capabilities(
                        new AgentCapabilities.Builder()
                                .streaming(true)
                                .pushNotifications(false)
                                .stateTransitionHistory(false)
                                .build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(skills)
                .protocolVersion("0.3.0");
        
        String mode = transportMode.toLowerCase();
        switch (mode) {
            case "grpc":
                // gRPC mode: gRPC on port 11000, HTTP also on 11000 for agent card
                String grpcUrl = "localhost:" + grpcPort;
                cardBuilder.url(grpcUrl)
                        .preferredTransport(TransportProtocol.GRPC.asString())
                        .additionalInterfaces(List.of(
                                new AgentInterface(TransportProtocol.GRPC.asString(), grpcUrl)));
                logger.info("Produced gRPC agent card: grpcPort={}, httpPort={}", grpcPort, httpPort);
                break;
                
            case "jsonrpc":
                // JSON-RPC mode: HTTP/WebSocket on port 11001
                String jsonRpcUrl = "localhost:" + httpPort;
                cardBuilder.url("http://" + jsonRpcUrl + "/")
                        .preferredTransport(TransportProtocol.JSONRPC.asString())
                        .additionalInterfaces(List.of(
                                new AgentInterface(TransportProtocol.JSONRPC.asString(), "http://" + jsonRpcUrl + "/")));
                logger.info("Produced JSON-RPC agent card: httpPort={}", httpPort);
                break;
                
            case "rest":
                // REST mode: HTTP on port 11002
                String restUrl = "localhost:" + httpPort;
                cardBuilder.url("http://" + restUrl + "/")
                        .preferredTransport(TransportProtocol.HTTP_JSON.asString())
                        .additionalInterfaces(List.of(
                                new AgentInterface(TransportProtocol.HTTP_JSON.asString(), "http://" + restUrl + "/")));
                logger.info("Produced REST (HTTP+JSON) agent card: httpPort={}", httpPort);
                break;
                
            default:
                logger.warn("Unknown transport mode '{}', defaulting to gRPC", mode);
                String defaultGrpcUrl = "localhost:" + grpcPort;
                cardBuilder.url(defaultGrpcUrl)
                        .preferredTransport(TransportProtocol.GRPC.asString())
                        .additionalInterfaces(List.of(
                                new AgentInterface(TransportProtocol.GRPC.asString(), defaultGrpcUrl)));
        }
        
        AgentCard card = cardBuilder.build();
        logger.info("Agent card produced: mode='{}', url='{}', preferredTransport='{}', skills={}",
                mode, card.url(), card.preferredTransport(),
                skills.stream().map(AgentSkill::id).toList());
        
        return card;
    }
}
