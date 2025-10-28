package com.aloha.a2a.agent2.executor;

import com.aloha.a2a.agent2.agent.DiceAgent;
import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.server.agentexecution.RequestContext;
import io.a2a.server.events.EventQueue;
import io.a2a.server.tasks.TaskUpdater;
import io.a2a.spec.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class DiceAgentExecutor implements AgentExecutor {
    
    private static final Logger logger = LoggerFactory.getLogger(DiceAgentExecutor.class);
    
    private static final String ERROR_CODE_INVALID_REQUEST = "invalid_request";
    private static final String ERROR_CODE_VALIDATION_ERROR = "validation_error";
    private static final String ERROR_CODE_INTERNAL_ERROR = "internal_error";
    
    private final DiceAgent diceAgent;
    
    public DiceAgentExecutor(DiceAgent diceAgent) {
        this.diceAgent = diceAgent;
        logger.info("DiceAgentExecutor initialized");
    }

    @Override
    public void execute(RequestContext context, EventQueue eventQueue) throws JSONRPCError {
        TaskUpdater updater = new TaskUpdater(context, eventQueue);
        String taskId = context.getTask() == null ? "<none>" : context.getTask().getId();

        try {
            logger.info("Received new request. taskId={}", taskId);

            try {
                validateRequest(context);
                logger.debug("Request validation passed");
            } catch (IllegalArgumentException e) {
                logger.error("Request validation failed: {}", e.getMessage());
                updater.addArtifact(
                        List.of(new TextPart("Validation error: " + e.getMessage(), null)),
                        null, null, null);
                updater.fail();
                throw new JSONRPCError(-32602, e.getMessage(), ERROR_CODE_VALIDATION_ERROR);
            }

            if (context.getTask() == null) {
                logger.debug("No task in context; marking submitted");
                updater.submit();
                logger.info("Task submitted");
            }
            updater.startWork();
            logger.info("Task started working: {}", taskId);

            String messageText = extractTextFromMessage(context.getMessage());
            logger.debug("Extracted message text: {}", messageText);

            if (messageText.trim().isEmpty()) {
                logger.warn("Empty message text received");
                updater.addArtifact(
                        List.of(new TextPart("Error: Empty message received. Please provide a message.", null)),
                        null, null, null);
                updater.fail();
                throw new JSONRPCError(-32602, "Empty message text", ERROR_CODE_INVALID_REQUEST);
            }

            logger.info("Invoking agent.rollAndAnswer");
            String response;
            try {
                response = diceAgent.rollAndAnswer(messageText);
            } catch (Exception e) {
                logger.error("LLM processing error: {}", e.getMessage(), e);
                String errorMessage = getErrorMessage(e);
                updater.addArtifact(
                        List.of(new TextPart(errorMessage, null)),
                        null, null, null);
                updater.fail();
                throw new JSONRPCError(-32603, "LLM processing failed: " + e.getMessage(), ERROR_CODE_INTERNAL_ERROR);
            }

            logger.info("Agent returned response length={}", response == null ? 0 : response.length());
            logger.debug("Agent response content: {}", response);

            TextPart responsePart = new TextPart(Objects.requireNonNull(response), null);
            List<Part<?>> parts = List.of(responsePart);

            logger.info("Adding artifact to task and completing. partsCount={}", parts.size());
            updater.addArtifact(parts, null, null, null);
            logger.debug("Artifact added");
            updater.complete();
            logger.info("Task completed successfully: {}", taskId);

        } catch (JSONRPCError e) {
            logger.error("A2A protocol error while executing task {}: {}", taskId, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during agent execution for task {}: {}", taskId, e.getMessage(), e);
            try {
                updater.addArtifact(
                        List.of(new TextPart("Internal server error: " + e.getMessage(), null)),
                        null, null, null);
                updater.fail();
                logger.info("Marked task as failed after unexpected error: {}", taskId);
            } catch (Exception inner) {
                logger.warn("Failed to update task after error: {}", inner.getMessage(), inner);
            }
            throw new JSONRPCError(-32603, "Internal error: " + e.getMessage(), ERROR_CODE_INTERNAL_ERROR);
        }
    }

    @Override
    public void cancel(RequestContext context, EventQueue eventQueue) throws JSONRPCError {
        Task task = context.getTask();
        String taskId = task == null ? "<none>" : task.getId();

        logger.info("Cancel requested for task: {}", taskId);

        if (task == null) {
            logger.error("Cancel requested but no task in context");
            throw new JSONRPCError(-32602, "No task found in context", "task_not_found");
        }

        if (task.getStatus().state() == TaskState.CANCELED) {
            logger.warn("Task already cancelled: {}", task.getId());
            throw new TaskNotCancelableError();
        }

        if (task.getStatus().state() == TaskState.COMPLETED) {
            logger.warn("Task already completed (cannot cancel): {}", task.getId());
            throw new TaskNotCancelableError();
        }

        if (task.getStatus().state() == TaskState.FAILED) {
            logger.warn("Task already failed (cannot cancel): {}", task.getId());
            throw new TaskNotCancelableError();
        }

        try {
            TaskUpdater updater = new TaskUpdater(context, eventQueue);
            updater.cancel();
            logger.info("Task cancelled successfully: {}", task.getId());
        } catch (Exception e) {
            logger.error("Error canceling task {}: {}", task.getId(), e.getMessage(), e);
            throw new JSONRPCError(-32603, "Failed to cancel task: " + e.getMessage(), "internal_error");
        }
    }

    private void validateRequest(RequestContext context) {
        if (context == null) {
            throw new IllegalArgumentException("Request context cannot be null");
        }

        Message message = context.getMessage();
        if (message == null) {
            throw new IllegalArgumentException("Invalid message: message is null");
        }

        if (message.getParts() == null || message.getParts().isEmpty()) {
            throw new IllegalArgumentException("Invalid message: no message parts provided");
        }

        boolean hasText = message.getParts().stream()
                .anyMatch(part -> part instanceof TextPart);
        if (!hasText) {
            throw new IllegalArgumentException("Invalid message: no text content found in message parts");
        }
    }

    private String extractTextFromMessage(Message message) {
        StringBuilder textBuilder = new StringBuilder();
        if (message.getParts() != null) {
            for (Part<?> part : message.getParts()) {
                if (part instanceof TextPart textPart) {
                    textBuilder.append(textPart.getText());
                }
            }
        }
        return textBuilder.toString();
    }

    private static String getErrorMessage(Exception e) {
        String errorMessage = "Error processing your request: " + e.getMessage();
        if (e.getMessage() != null &&
                (e.getMessage().contains("Connection refused") ||
                        e.getMessage().contains("connect timed out") ||
                        e.getMessage().contains("Failed to connect"))) {
            errorMessage = "Failed to connect to Ollama. Please ensure:\n" +
                    "1. Ollama is installed: https://ollama.ai/download\n" +
                    "2. Ollama service is running: ollama serve\n" +
                    "3. The model is available: ollama pull qwen2.5\n\n" +
                    "Error details: " + e.getMessage();
        }
        return errorMessage;
    }
}
