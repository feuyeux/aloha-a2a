package com.aloha.a2a.agent2.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.a2a.server.ServerCallContext;
import io.a2a.spec.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

public class JsonRpcWebSocketHandler extends TextWebSocketHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(JsonRpcWebSocketHandler.class);
    private final SpringJsonRpcHandler handler;
    private final ObjectMapper objectMapper;
    
    public JsonRpcWebSocketHandler(SpringJsonRpcHandler handler) {
        this.handler = handler;
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        logger.info("WebSocket connection established: {}", session.getId());
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        logger.debug("Received WebSocket message: {}", message.getPayload());
        
        try {
            // Parse JSON-RPC request
            @SuppressWarnings("unchecked")
            Map<String, Object> jsonRpcRequest = objectMapper.readValue(message.getPayload(), Map.class);
            String method = (String) jsonRpcRequest.get("method");
            Object id = jsonRpcRequest.get("id");
            
            ServerCallContext context = createCallContext(session);
            Object result = null;
            
            // Route to appropriate handler method
            if ("sendMessage".equals(method)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> params = (Map<String, Object>) jsonRpcRequest.get("params");
                String paramsJson = objectMapper.writeValueAsString(params);
                SendMessageRequest req = objectMapper.readValue(paramsJson, SendMessageRequest.class);
                SendMessageResponse response = handler.onMessageSend(req, context);
                result = response;
            } else if ("getTask".equals(method)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> params = (Map<String, Object>) jsonRpcRequest.get("params");
                String paramsJson = objectMapper.writeValueAsString(params);
                GetTaskRequest req = objectMapper.readValue(paramsJson, GetTaskRequest.class);
                GetTaskResponse response = handler.onGetTask(req, context);
                result = response;
            } else if ("cancelTask".equals(method)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> params = (Map<String, Object>) jsonRpcRequest.get("params");
                String paramsJson = objectMapper.writeValueAsString(params);
                CancelTaskRequest req = objectMapper.readValue(paramsJson, CancelTaskRequest.class);
                CancelTaskResponse response = handler.onCancelTask(req, context);
                result = response;
            } else {
                // Unknown method
                Map<String, Object> error = Map.of(
                    "jsonrpc", "2.0",
                    "id", id,
                    "error", Map.of(
                        "code", -32601,
                        "message", "Method not found: " + method
                    )
                );
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(error)));
                return;
            }
            
            // Send JSON-RPC response
            Map<String, Object> jsonRpcResponse = Map.of(
                "jsonrpc", "2.0",
                "id", id,
                "result", result
            );
            
            String responseJson = objectMapper.writeValueAsString(jsonRpcResponse);
            session.sendMessage(new TextMessage(responseJson));
            
        } catch (Exception e) {
            logger.error("Error processing WebSocket message", e);
            // Send JSON-RPC error response
            Map<String, Object> errorResponse = Map.of(
                "jsonrpc", "2.0",
                "id", null,
                "error", Map.of(
                    "code", -32603,
                    "message", "Internal error: " + e.getMessage()
                )
            );
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorResponse)));
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        logger.info("WebSocket connection closed: {} - {}", session.getId(), status);
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        logger.error("WebSocket transport error: {}", session.getId(), exception);
    }
    
    private ServerCallContext createCallContext(WebSocketSession session) {
        // Create a simple ServerCallContext
        // In a real implementation, extract auth from WebSocket handshake
        return new ServerCallContext(
            null,  // user
            new java.util.HashMap<>(),  // state
            new java.util.HashSet<>()   // requested extensions
        );
    }
}
