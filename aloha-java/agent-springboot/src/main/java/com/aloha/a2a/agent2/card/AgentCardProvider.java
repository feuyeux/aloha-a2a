package com.aloha.a2a.agent2.card;

import com.aloha.a2a.agent2.config.AgentConfig;
import io.a2a.spec.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AgentCardProvider {
    
    private static final Logger logger = LoggerFactory.getLogger(AgentCardProvider.class);
    
    private final AgentConfig agentConfig;
    private final String transportMode;
    private final int grpcPort;
    private final int httpPort;
    
    public AgentCardProvider(
            AgentConfig agentConfig,
            @Value("${transport.mode:rest}") String transportMode,
            @Value("${grpc.server.port:11000}") int grpcPort,
            @Value("${server.port:11002}") int httpPort) {
        this.agentConfig = agentConfig;
        this.transportMode = transportMode;
        this.grpcPort = grpcPort;
        this.httpPort = httpPort;
    }
    
    public AgentCard getAgentCard() {
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
                .name(agentConfig.getName())
                .description(agentConfig.getDescription())
                .version(agentConfig.getVersion())
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
                String grpcUrl = "localhost:" + grpcPort;
                cardBuilder.url(grpcUrl)
                        .preferredTransport(TransportProtocol.GRPC.asString())
                        .additionalInterfaces(List.of(
                                new AgentInterface(TransportProtocol.GRPC.asString(), grpcUrl)));
                logger.info("Produced gRPC agent card: grpcPort={}, httpPort={}", grpcPort, httpPort);
                break;
                
            case "jsonrpc":
                String jsonRpcUrl = "localhost:" + httpPort;
                cardBuilder.url("http://" + jsonRpcUrl + "/")
                        .preferredTransport(TransportProtocol.JSONRPC.asString())
                        .additionalInterfaces(List.of(
                                new AgentInterface(TransportProtocol.JSONRPC.asString(), "http://" + jsonRpcUrl + "/")));
                logger.info("Produced JSON-RPC agent card: httpPort={}", httpPort);
                break;
                
            case "rest":
                String restUrl = "localhost:" + httpPort;
                cardBuilder.url("http://" + restUrl + "/")
                        .preferredTransport(TransportProtocol.HTTP_JSON.asString())
                        .additionalInterfaces(List.of(
                                new AgentInterface(TransportProtocol.HTTP_JSON.asString(), "http://" + restUrl + "/")));
                logger.info("Produced REST (HTTP+JSON) agent card: httpPort={}", httpPort);
                break;
                
            default:
                logger.warn("Unknown transport mode '{}', defaulting to REST", mode);
                String defaultRestUrl = "localhost:" + httpPort;
                cardBuilder.url("http://" + defaultRestUrl + "/")
                        .preferredTransport(TransportProtocol.HTTP_JSON.asString())
                        .additionalInterfaces(List.of(
                                new AgentInterface(TransportProtocol.HTTP_JSON.asString(), "http://" + defaultRestUrl + "/")));
        }
        
        AgentCard card = cardBuilder.build();
        logger.info("Agent card produced: mode='{}', url='{}', preferredTransport='{}', skills={}",
                mode, card.url(), card.preferredTransport(),
                skills.stream().map(AgentSkill::id).toList());
        
        return card;
    }
}

