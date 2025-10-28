package com.aloha.a2a.agent2.transport;

import com.aloha.a2a.agent2.card.AgentCardProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.a2a.server.ServerCallContext;
import io.a2a.spec.AgentCard;
import io.a2a.transport.rest.handler.RestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

@org.springframework.web.bind.annotation.RestController
@ConditionalOnProperty(name = "transport.mode", havingValue = "rest")
public class RestController {
    
    private static final Logger logger = LoggerFactory.getLogger(RestController.class);
    private final SpringRestHandler handler;
    private final AgentCardProvider cardProvider;
    private final ObjectMapper objectMapper;
    
    public RestController(SpringRestHandler handler, AgentCardProvider cardProvider) {
        this.handler = handler;
        this.cardProvider = cardProvider;
        this.objectMapper = new ObjectMapper();
        logger.info("REST controller initialized");
    }
    
    @GetMapping(value = "/.well-known/agent-card.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AgentCard> getAgentCard() {
        logger.debug("Agent card requested");
        return ResponseEntity.ok(cardProvider.getAgentCard());
    }
    
    @PostMapping(value = "/", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> sendMessage(@RequestBody String request, HttpServletRequest httpRequest) {
        logger.info("Send message request received");
        try {
            ServerCallContext context = createCallContext(httpRequest);
            RestHandler.HTTPRestResponse response = handler.sendMessage(request, context);
            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        } catch (Exception e) {
            logger.error("Error processing send message", e);
            return ResponseEntity.internalServerError()
                    .body("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }
    
    @GetMapping(value = "/{taskId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getTask(@PathVariable String taskId, 
                                         @RequestParam(required = false) Integer maxEvents,
                                         HttpServletRequest httpRequest) {
        logger.info("Get task request: taskId={}", taskId);
        try {
            ServerCallContext context = createCallContext(httpRequest);
            RestHandler.HTTPRestResponse response = handler.getTask(taskId, maxEvents, context);
            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        } catch (Exception e) {
            logger.error("Error getting task", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }
    
    @DeleteMapping(value = "/{taskId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> cancelTask(@PathVariable String taskId, HttpServletRequest httpRequest) {
        logger.info("Cancel task request: taskId={}", taskId);
        try {
            ServerCallContext context = createCallContext(httpRequest);
            RestHandler.HTTPRestResponse response = handler.cancelTask(taskId, context);
            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        } catch (Exception e) {
            logger.error("Error canceling task", e);
            return ResponseEntity.internalServerError()
                    .body("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }
    
    private ServerCallContext createCallContext(HttpServletRequest request) {
        // Create a simple ServerCallContext from HTTP request
        // Extract authentication token if present
        String authHeader = request.getHeader("Authorization");
        io.a2a.server.auth.User user;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            user = com.aloha.a2a.agent2.auth.SimpleUser.authenticated(token);
        } else {
            user = com.aloha.a2a.agent2.auth.SimpleUser.anonymous();
        }
        
        return new ServerCallContext(
            user,
            new java.util.HashMap<>(),  // state
            new java.util.HashSet<>()   // requested extensions
        );
    }
}
