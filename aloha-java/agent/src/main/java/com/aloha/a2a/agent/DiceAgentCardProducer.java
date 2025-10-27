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
 * Provides agent metadata and capabilities for discovery.
 */
@ApplicationScoped
public class DiceAgentCardProducer {
    
    private static final Logger logger = LoggerFactory.getLogger(DiceAgentCardProducer.class);
    
    @Inject
    @ConfigProperty(name = "quarkus.http.port")
    int httpPort;
    
    @Inject
    @ConfigProperty(name = "quarkus.grpc.server.port")
    int grpcPort;
    
    @ConfigProperty(name = "agent.name", defaultValue = "Dice Agent")
    String agentName;
    
    @ConfigProperty(name = "agent.description", 
            defaultValue = "An agent that can roll arbitrary dice and check prime numbers")
    String agentDescription;
    
    @ConfigProperty(name = "agent.version", defaultValue = "1.0.0")
    String agentVersion;
    
    /**
     * Produces the agent card for the dice agent.
     *
     * @return the configured agent card
     */
    @Produces
    @PublicAgentCard
    public AgentCard agentCard() {
        String url = "localhost:" + httpPort;
        String grpcUrl = "localhost:" + grpcPort;
        
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
        
        AgentCard card = new AgentCard.Builder()
                .name(agentName)
                .description(agentDescription)
                .version(agentVersion)
                .url(url)
                .preferredTransport(TransportProtocol.GRPC.asString())
                .capabilities(
                        new AgentCapabilities.Builder()
                                .streaming(true)
                                .pushNotifications(false)
                                .stateTransitionHistory(false)
                                .build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(skills)
                .protocolVersion("0.3.0")
                .additionalInterfaces(List.of(
                        new AgentInterface(TransportProtocol.GRPC.asString(), grpcUrl),
                        new AgentInterface(TransportProtocol.JSONRPC.asString(), "http://" + url)))
                .build();
        
        logger.info("Produced public agent card: name='{}', url='{}', preferredTransport='{}', skills={}",
                agentName, url, TransportProtocol.GRPC.asString(), 
                skills.stream().map(AgentSkill::id).toList());
        
        return card;
    }
}
