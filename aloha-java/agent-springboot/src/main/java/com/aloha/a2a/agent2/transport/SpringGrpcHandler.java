package com.aloha.a2a.agent2.transport;

import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.server.events.InMemoryQueueManager;
import io.a2a.server.events.QueueManager;
import io.a2a.server.requesthandlers.DefaultRequestHandler;
import io.a2a.server.requesthandlers.RequestHandler;
import io.a2a.server.tasks.InMemoryTaskStore;
import io.a2a.server.tasks.PushNotificationConfigStore;
import io.a2a.server.tasks.PushNotificationSender;
import io.a2a.server.tasks.TaskStore;
import io.a2a.spec.AgentCard;
import io.a2a.transport.grpc.handler.CallContextFactory;
import io.a2a.transport.grpc.handler.GrpcHandler;

import java.util.concurrent.Executors;

public class SpringGrpcHandler extends GrpcHandler {
    
    private final RequestHandler requestHandler;
    private final AgentCard agentCard;
    
    public SpringGrpcHandler(AgentExecutor executor, AgentCard agentCard) {
        TaskStore taskStore = new InMemoryTaskStore();
        QueueManager queueManager = new InMemoryQueueManager();
        PushNotificationConfigStore pushConfigStore = null; // Not using push notifications
        PushNotificationSender pushSender = null; // Not using push notifications
        
        this.requestHandler = new DefaultRequestHandler(
                executor,
                taskStore,
                queueManager,
                pushConfigStore,
                pushSender,
                Executors.newCachedThreadPool()
        );
        this.agentCard = agentCard;
    }
    
    @Override
    protected RequestHandler getRequestHandler() {
        return requestHandler;
    }
    
    @Override
    protected AgentCard getAgentCard() {
        return agentCard;
    }
    
    @Override
    protected CallContextFactory getCallContextFactory() {
        return null; // Optional, not needed for basic functionality
    }
}
