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
@ConditionalOnProperty(name = "transport.mode", havingValue = "jsonrpc")
public class JsonRpcAgentCardController {
    
    private static final Logger logger = LoggerFactory.getLogger(JsonRpcAgentCardController.class);
    private final AgentCardProvider cardProvider;
    
    public JsonRpcAgentCardController(AgentCardProvider cardProvider) {
        this.cardProvider = cardProvider;
        logger.info("JSON-RPC agent card controller initialized");
    }
    
    @GetMapping(value = "/.well-known/agent-card.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AgentCard> getAgentCard() {
        logger.debug("Agent card requested via HTTP");
        return ResponseEntity.ok(cardProvider.getAgentCard());
    }
}
