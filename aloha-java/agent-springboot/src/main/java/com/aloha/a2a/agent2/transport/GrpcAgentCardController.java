package com.aloha.a2a.agent2.transport;

import com.aloha.a2a.agent2.card.AgentCardProvider;
import io.a2a.spec.AgentCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "transport.mode", havingValue = "grpc")
public class GrpcAgentCardController {
    
    private static final Logger logger = LoggerFactory.getLogger(GrpcAgentCardController.class);
    private final AgentCardProvider cardProvider;
    
    public GrpcAgentCardController(AgentCardProvider cardProvider) {
        this.cardProvider = cardProvider;
        logger.info("gRPC agent card controller initialized for HTTP endpoint");
    }
    
    @GetMapping(value = "/.well-known/agent-card.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AgentCard> getAgentCard() {
        logger.debug("Agent card requested via HTTP");
        return ResponseEntity.ok(cardProvider.getAgentCard());
    }
}
