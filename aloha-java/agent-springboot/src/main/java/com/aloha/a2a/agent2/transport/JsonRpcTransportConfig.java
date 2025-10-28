package com.aloha.a2a.agent2.transport;

import com.aloha.a2a.agent2.card.AgentCardProvider;
import com.aloha.a2a.agent2.executor.DiceAgentExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@ConditionalOnProperty(name = "transport.mode", havingValue = "jsonrpc")
public class JsonRpcTransportConfig implements WebSocketConfigurer {
    
    private static final Logger logger = LoggerFactory.getLogger(JsonRpcTransportConfig.class);
    
    private final DiceAgentExecutor executor;
    private final AgentCardProvider cardProvider;
    
    public JsonRpcTransportConfig(DiceAgentExecutor executor, AgentCardProvider cardProvider) {
        this.executor = executor;
        this.cardProvider = cardProvider;
    }
    
    @Bean
    public SpringJsonRpcHandler jsonRpcTransportHandler() {
        logger.info("Initializing JSON-RPC transport handler");
        return new SpringJsonRpcHandler(executor, cardProvider.getAgentCard());
    }
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        logger.info("Registering WebSocket handler for JSON-RPC");
        registry.addHandler(new JsonRpcWebSocketHandler(jsonRpcTransportHandler()), "/")
                .setAllowedOrigins("*");
    }
}

