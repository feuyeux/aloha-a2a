/**
 * Aloha Server with REST transport support.
 */

import express from 'express';
import { v4 as uuidv4 } from 'uuid';
import {
    AgentCard,
    Task,
    TaskState,
    TaskStatusUpdateEvent,
    TextPart,
    Message,
} from '@a2a-js/sdk';
import {
    InMemoryTaskStore,
    TaskStore,
    AgentExecutor,
    RequestContext,
    ExecutionEventBus,
    DefaultRequestHandler,
} from '@a2a-js/sdk/server';
import { A2AExpressApp } from '@a2a-js/sdk/server/express';
import { DiceAgentExecutor } from './executor.js';

/**
 * Aloha Server implementing A2A protocol with REST transport support.
 */
export class AlohaServer {
    private grpcPort: number;
    private jsonrpcPort: number;
    private restPort: number;
    private host: string;
    private agentCard: AgentCard;
    private executor: DiceAgentExecutor;
    private taskStore: TaskStore;
    private contexts: Map<string, Message[]>;

    constructor(
        grpcPort: number = 14000,
        jsonrpcPort: number = 14001,
        restPort: number = 14002,
        host: string = '0.0.0.0'
    ) {
        this.grpcPort = grpcPort;
        this.jsonrpcPort = jsonrpcPort;
        this.restPort = restPort;
        this.host = host;
        this.contexts = new Map();

        // Create agent card
        this.agentCard = this._createAgentCard();

        // Create agent executor
        this.executor = new DiceAgentExecutor();

        // Create task store
        this.taskStore = new InMemoryTaskStore();

        console.log('AlohaServer initialized');
    }

    /**
     * Create the agent card describing capabilities.
     */
    private _createAgentCard(): AgentCard {
        return {
            protocolVersion: '0.3.0',
            name: process.env.AGENT_NAME || 'Dice Agent',
            description: process.env.AGENT_DESCRIPTION || 'An agent that can roll arbitrary dice and check prime numbers',
            url: `http://localhost:${this.restPort}`,
            provider: {
                organization: 'Aloha A2A',
                url: 'https://github.com/google/aloha-a2a',
            },
            version: process.env.AGENT_VERSION || '1.0.0',
            capabilities: {
                streaming: true,
                pushNotifications: false,
                stateTransitionHistory: true,
            },
            defaultInputModes: ['text'],
            defaultOutputModes: ['text', 'task-status'],
            skills: [
                {
                    id: 'roll-dice',
                    name: 'Roll Dice',
                    description: 'Rolls an N-sided dice',
                    tags: ['dice', 'random'],
                    examples: ['Roll a 20-sided dice', 'Roll a 6-sided dice'],
                    inputModes: ['text'],
                    outputModes: ['text', 'task-status'],
                },
                {
                    id: 'check-prime',
                    name: 'Prime Checker',
                    description: 'Checks if numbers are prime',
                    tags: ['math', 'prime'],
                    examples: ['Is 17 prime?', 'Check if 2, 4, 7, 9, 11 are prime'],
                    inputModes: ['text'],
                    outputModes: ['text', 'task-status'],
                },
            ],
            supportsAuthenticatedExtendedCard: false,
        };
    }

    /**
     * Start the agent server with multi-transport support.
     */
    async start(): Promise<void> {
        console.log('Starting Dice Agent with REST transport support');

        // Create the A2A executor wrapper
        const a2aExecutor = new DiceA2AExecutor(this.executor, this.contexts);

        // Create request handler
        const requestHandler = new DefaultRequestHandler(
            this.agentCard,
            this.taskStore,
            a2aExecutor
        );

        // Setup REST transport (primary transport for now)
        // Note: The @a2a-js/sdk currently has best support for REST/Express
        // gRPC and JSON-RPC support may require additional implementation
        const appBuilder = new A2AExpressApp(requestHandler);
        const expressApp = appBuilder.setupRoutes(express());

        expressApp.get('/v1/transports', (_req, res) => {
            res.json({
                rest: {
                    implemented: true,
                    stream: true,
                },
                jsonrpc: {
                    enabled: false,
                    implemented: false,
                    stream: false,
                },
                grpc: {
                    enabled: false,
                    implemented: false,
                    stream: false,
                },
                experimentalTransports: false,
            });
        });

        // Start REST server
        expressApp.listen(this.restPort, () => {
            console.log('='.repeat(60));
            console.log('Dice Agent is running with the following transport:');
            console.log(`  - REST:         http://${this.host}:${this.restPort}`);
            console.log(`  - Agent Card:   http://${this.host}:${this.restPort}/.well-known/agent-card.json`);
            console.log('='.repeat(60));
        });
    }
}

/**
 * A2A Protocol Error Codes
 */
const A2AErrorCode = {
    INVALID_REQUEST: 'invalid_request',
    UNSUPPORTED_OPERATION: 'unsupported_operation',
    TASK_NOT_FOUND: 'task_not_found',
    INTERNAL_ERROR: 'internal_error',
    TIMEOUT: 'timeout',
    CANCELED: 'canceled',
    VALIDATION_ERROR: 'validation_error',
};

/**
 * Validation error class
 */
class ValidationError extends Error {
    constructor(message: string) {
        super(message);
        this.name = 'ValidationError';
    }
}

/**
 * Validates incoming message structure
 */
function validateMessage(message: Message): void {
    if (!message) {
        throw new ValidationError('Invalid message: message is null or undefined');
    }

    if (!message.parts || message.parts.length === 0) {
        throw new ValidationError('Invalid message: no message parts provided');
    }

    // Check for at least one text part
    const hasText = message.parts.some(
        (part) => part.kind === 'text' && (part as TextPart).text
    );
    if (!hasText) {
        throw new ValidationError('Invalid message: no text content found in message parts');
    }
}

/**
 * Wrapper to adapt DiceAgentExecutor to A2A SDK's AgentExecutor interface.
 */
class DiceA2AExecutor implements AgentExecutor {
    private executor: DiceAgentExecutor;
    private contexts: Map<string, Message[]>;
    private cancelledTasks = new Set<string>();

    constructor(executor: DiceAgentExecutor, contexts: Map<string, Message[]>) {
        this.executor = executor;
        this.contexts = contexts;
    }

    async cancelTask(taskId: string, eventBus: ExecutionEventBus): Promise<void> {
        this.cancelledTasks.add(taskId);
        console.log(`Cancel requested for task: ${taskId}`);

        const cancelledUpdate: TaskStatusUpdateEvent = {
            kind: 'status-update',
            taskId: taskId,
            contextId: '', // Will be filled in by the framework
            status: {
                state: 'canceled',
                timestamp: new Date().toISOString(),
            },
            final: true,
        };
        eventBus.publish(cancelledUpdate);
        console.log(`Task cancelled successfully: ${taskId}`);
    }

    async execute(
        requestContext: RequestContext,
        eventBus: ExecutionEventBus
    ): Promise<void> {
        const userMessage = requestContext.userMessage;
        const existingTask = requestContext.task;

        // Determine IDs for the task and context
        const taskId = existingTask?.id || uuidv4();
        const contextId = userMessage.contextId || existingTask?.contextId || uuidv4();

        console.log(`Received new request. taskId=${taskId}`);

        try {
            // Validate incoming request
            try {
                validateMessage(userMessage);
                console.log('Message validation passed');
            } catch (error: any) {
                console.error(`Message validation failed: ${error.message}`);
                const errorUpdate: TaskStatusUpdateEvent = {
                    kind: 'status-update',
                    taskId: taskId,
                    contextId: contextId,
                    status: {
                        state: 'failed',
                        message: {
                            kind: 'message',
                            role: 'agent',
                            messageId: uuidv4(),
                            parts: [{ kind: 'text', text: `Validation error: ${error.message}` }],
                            taskId: taskId,
                            contextId: contextId,
                        },
                        timestamp: new Date().toISOString(),
                    },
                    final: true,
                };
                eventBus.publish(errorUpdate);
                return;
            }

            // 1. Publish initial Task event if it's a new task
            if (!existingTask) {
                const initialTask: Task = {
                    kind: 'task',
                    id: taskId,
                    contextId: contextId,
                    status: {
                        state: 'submitted',
                        timestamp: new Date().toISOString(),
                    },
                    history: [userMessage],
                    metadata: userMessage.metadata,
                };
                eventBus.publish(initialTask);
                console.log('Task submitted');
            }

            // 2. Publish "working" status update
            const workingStatusUpdate: TaskStatusUpdateEvent = {
                kind: 'status-update',
                taskId: taskId,
                contextId: contextId,
                status: {
                    state: 'working',
                    message: {
                        kind: 'message',
                        role: 'agent',
                        messageId: uuidv4(),
                        parts: [{ kind: 'text', text: 'Processing your request...' }],
                        taskId: taskId,
                        contextId: contextId,
                    },
                    timestamp: new Date().toISOString(),
                },
                final: false,
            };
            eventBus.publish(workingStatusUpdate);
            console.log(`Task started working: ${taskId}`);

            // 3. Extract text from message
            const messageText = this._extractTextFromMessage(userMessage);
            console.log(`Extracted message text: ${messageText}`);

            if (!messageText || !messageText.trim()) {
                console.warn('Empty message text received');
                const errorUpdate: TaskStatusUpdateEvent = {
                    kind: 'status-update',
                    taskId: taskId,
                    contextId: contextId,
                    status: {
                        state: 'failed',
                        message: {
                            kind: 'message',
                            role: 'agent',
                            messageId: uuidv4(),
                            parts: [{ kind: 'text', text: 'Error: Empty message received. Please provide a message.' }],
                            taskId: taskId,
                            contextId: contextId,
                        },
                        timestamp: new Date().toISOString(),
                    },
                    final: true,
                };
                eventBus.publish(errorUpdate);
                return;
            }

            // 4. Update context history
            const historyForContext = this.contexts.get(contextId) || [];
            if (!historyForContext.find(m => m.messageId === userMessage.messageId)) {
                historyForContext.push(userMessage);
            }
            this.contexts.set(contextId, historyForContext);

            // Check if cancelled
            if (this.cancelledTasks.has(taskId)) {
                console.log(`Task cancelled: ${taskId}`);
                const cancelledUpdate: TaskStatusUpdateEvent = {
                    kind: 'status-update',
                    taskId: taskId,
                    contextId: contextId,
                    status: {
                        state: 'canceled',
                        timestamp: new Date().toISOString(),
                    },
                    final: true,
                };
                eventBus.publish(cancelledUpdate);
                return;
            }

            // 5. Execute with LLM
            console.log('Invoking LLM with tools');
            let responseText: string;
            try {
                responseText = await this.executor.execute(messageText);
                console.log(`LLM returned response length=${responseText.length}`);
                console.log(`Response content: ${responseText}`);
            } catch (error: any) {
                console.error(`LLM processing error: ${error.message}`, error);
                const errorUpdate: TaskStatusUpdateEvent = {
                    kind: 'status-update',
                    taskId: taskId,
                    contextId: contextId,
                    status: {
                        state: 'failed',
                        message: {
                            kind: 'message',
                            role: 'agent',
                            messageId: uuidv4(),
                            parts: [{ kind: 'text', text: `Error processing your request: ${error.message}` }],
                            taskId: taskId,
                            contextId: contextId,
                        },
                        timestamp: new Date().toISOString(),
                    },
                    final: true,
                };
                eventBus.publish(errorUpdate);
                return;
            }

            // Check if cancelled after execution
            if (this.cancelledTasks.has(taskId)) {
                console.log(`Task cancelled after execution: ${taskId}`);
                const cancelledUpdate: TaskStatusUpdateEvent = {
                    kind: 'status-update',
                    taskId: taskId,
                    contextId: contextId,
                    status: {
                        state: 'canceled',
                        timestamp: new Date().toISOString(),
                    },
                    final: true,
                };
                eventBus.publish(cancelledUpdate);
                return;
            }

            // 6. Publish final task status update
            const agentMessage: Message = {
                kind: 'message',
                role: 'agent',
                messageId: uuidv4(),
                parts: [{ kind: 'text', text: responseText }],
                taskId: taskId,
                contextId: contextId,
            };
            historyForContext.push(agentMessage);
            this.contexts.set(contextId, historyForContext);

            const finalUpdate: TaskStatusUpdateEvent = {
                kind: 'status-update',
                taskId: taskId,
                contextId: contextId,
                status: {
                    state: 'completed',
                    message: agentMessage,
                    timestamp: new Date().toISOString(),
                },
                final: true,
            };
            eventBus.publish(finalUpdate);

            console.log(`Task completed successfully: ${taskId}`);
        } catch (error: any) {
            console.error(`Unexpected error during agent execution for task ${taskId}:`, error);
            try {
                const errorUpdate: TaskStatusUpdateEvent = {
                    kind: 'status-update',
                    taskId: taskId,
                    contextId: contextId,
                    status: {
                        state: 'failed',
                        message: {
                            kind: 'message',
                            role: 'agent',
                            messageId: uuidv4(),
                            parts: [{ kind: 'text', text: `Internal server error: ${error.message}` }],
                            taskId: taskId,
                            contextId: contextId,
                        },
                        timestamp: new Date().toISOString(),
                    },
                    final: true,
                };
                eventBus.publish(errorUpdate);
                console.log(`Marked task as failed after unexpected error: ${taskId}`);
            } catch (inner: any) {
                console.warn(`Failed to update task after error: ${inner.message}`);
            }
        }
    }

    /**
     * Extract text content from message parts.
     */
    private _extractTextFromMessage(message: Message): string {
        const textParts: string[] = [];
        if (message.parts) {
            for (const part of message.parts) {
                if (part.kind === 'text' && (part as TextPart).text) {
                    textParts.push((part as TextPart).text);
                }
            }
        }
        return textParts.join('');
    }
} 
