package com.aloha.a2a.server;

import com.aloha.a2a.server.config.AppConfig;
import io.a2a.spec.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Provides the A2A Agent Card based on the configured transport mode.
 * <p>
 * The Agent Card includes agent metadata (name, description, version),
 * supported capabilities, available skills, and transport endpoints.
 * It is accessible via the standard A2A endpoint:
 * {@code /.well-known/agent-card.json}
 */
public class AgentCardProvider {

    private static final Logger logger = LoggerFactory.getLogger(AgentCardProvider.class);

    private final AgentCard agentCard;

    public AgentCardProvider(AppConfig config) {
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

        AgentCard.Builder cardBuilder = new AgentCard.Builder()
                .name(config.getAgentName())
                .description(config.getAgentDescription())
                .version(config.getAgentVersion())
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

        String mode = config.getTransportMode();
        String httpUrl = "http://localhost:" + config.getHttpPort() + "/";
        String grpcUrl = "localhost:" + config.getGrpcPort();

        // Always declare all three transports — the SDK validates that the AgentCard
        // lists every transport that has been registered on the server side.
        // gRPC uses its own port; JSON-RPC and REST share the http port.
        List<AgentInterface> allInterfaces = List.of(
                new AgentInterface(TransportProtocol.GRPC.asString(), grpcUrl),
                new AgentInterface(TransportProtocol.JSONRPC.asString(), httpUrl),
                new AgentInterface(TransportProtocol.HTTP_JSON.asString(), httpUrl));

        switch (mode) {
            case "grpc":
                cardBuilder.url(grpcUrl)
                        .preferredTransport(TransportProtocol.GRPC.asString())
                        .additionalInterfaces(allInterfaces);
                logger.info("Produced gRPC agent card: grpcPort={}, httpPort={}", config.getGrpcPort(), config.getHttpPort());
                break;

            case "jsonrpc":
                cardBuilder.url(httpUrl)
                        .preferredTransport(TransportProtocol.JSONRPC.asString())
                        .additionalInterfaces(allInterfaces);
                logger.info("Produced JSON-RPC agent card: httpPort={}", config.getHttpPort());
                break;

            case "rest":
                cardBuilder.url(httpUrl)
                        .preferredTransport(TransportProtocol.HTTP_JSON.asString())
                        .additionalInterfaces(allInterfaces);
                logger.info("Produced REST (HTTP+JSON) agent card: httpPort={}", config.getHttpPort());
                break;

            default:
                logger.warn("Unknown transport mode '{}', defaulting to gRPC", mode);
                cardBuilder.url(grpcUrl)
                        .preferredTransport(TransportProtocol.GRPC.asString())
                        .additionalInterfaces(allInterfaces);
        }

        this.agentCard = cardBuilder.build();
        logger.info("Agent card produced: mode='{}', url='{}', preferredTransport='{}', skills={}",
                mode, agentCard.url(), agentCard.preferredTransport(),
                skills.stream().map(AgentSkill::id).toList());
    }

    public AgentCard getAgentCard() {
        return agentCard;
    }
}
