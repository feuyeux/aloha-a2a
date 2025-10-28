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
import io.a2a.transport.rest.handler.RestHandler;

import java.util.concurrent.Executors;

public class SpringRestHandler extends RestHandler {
    
    public SpringRestHandler(AgentExecutor executor, AgentCard agentCard) {
        super(agentCard, createRequestHandler(executor));
    }
    
    private static RequestHandler createRequestHandler(AgentExecutor executor) {
        TaskStore taskStore = new InMemoryTaskStore();
        QueueManager queueManager = new InMemoryQueueManager();
        PushNotificationConfigStore pushConfigStore = null;
        PushNotificationSender pushSender = null;
        
        return new DefaultRequestHandler(
                executor,
                taskStore,
                queueManager,
                pushConfigStore,
                pushSender,
                Executors.newCachedThreadPool()
        );
    }
}
