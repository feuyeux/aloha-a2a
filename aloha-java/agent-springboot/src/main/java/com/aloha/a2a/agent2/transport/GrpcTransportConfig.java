package com.aloha.a2a.agent2.transport;

import com.aloha.a2a.agent2.card.AgentCardProvider;
import com.aloha.a2a.agent2.executor.DiceAgentExecutor;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;

@Configuration
@ConditionalOnProperty(name = "transport.mode", havingValue = "grpc")
public class GrpcTransportConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(GrpcTransportConfig.class);
    
    private Server grpcServer;
    
    @Value("${grpc.server.port:11000}")
    private int grpcPort;
    
    private final DiceAgentExecutor executor;
    private final AgentCardProvider cardProvider;
    
    public GrpcTransportConfig(DiceAgentExecutor executor, AgentCardProvider cardProvider) {
        this.executor = executor;
        this.cardProvider = cardProvider;
    }
    
    @PostConstruct
    public void startGrpcServer() throws IOException {
        logger.info("Starting gRPC server on port {}", grpcPort);
        
        SpringGrpcHandler handler = new SpringGrpcHandler(executor, cardProvider.getAgentCard());
        
        grpcServer = ServerBuilder.forPort(grpcPort)
                .addService(handler)
                .build()
                .start();
        
        logger.info("gRPC server started successfully on port {}", grpcPort);
    }
    
    @PreDestroy
    public void stopGrpcServer() {
        if (grpcServer != null) {
            logger.info("Shutting down gRPC server");
            grpcServer.shutdown();
        }
    }
}

