/**
 * A2A Client with multi-transport support (REST, JSON-RPC, gRPC).
 */

import { v4 as uuidv4 } from 'uuid';
import {
    AgentCard,
    Message,
    MessageSendParams,
    Task,
    TaskStatusUpdateEvent,
    TaskArtifactUpdateEvent,
    TextPart,
    Part,
} from '@a2a-js/sdk';
import { A2AClient, JsonRpcTransport, RestTransport, Transport } from '@a2a-js/sdk/client';
import { GrpcTransport } from '@a2a-js/sdk/client/grpc';

/**
 * A2A Client with multi-transport support (REST, JSON-RPC, gRPC).
 */
export class AlohaClient {
    private serverUrl: string;
    private transport: string;
    private client: A2AClient | null = null;
    private rawTransport: Transport | null = null;
    private agentCard: AgentCard | null = null;

    constructor(serverUrl: string, transport: string = 'rest') {
        this.serverUrl = serverUrl;
        this.transport = transport.toLowerCase();
        console.log(`Client created for ${serverUrl} using ${this.transport} transport`);
    }

    /**
     * Initialize the client by fetching agent card.
     */
    async initialize(): Promise<void> {
        console.log(`Connecting to agent at: ${this.serverUrl}`);

        try {
            if (this.transport === 'rest') {
                await this._initRest();
            } else if (this.transport === 'jsonrpc') {
                await this._initJsonRpc();
            } else if (this.transport === 'grpc') {
                await this._initGrpc();
            } else {
                throw new Error(`Unsupported transport: ${this.transport}`);
            }

            console.log('Successfully fetched public agent card:');
            console.log(`  Name: ${this.agentCard!.name}`);
            console.log(`  Description: ${this.agentCard!.description}`);
            console.log(`  Version: ${this.agentCard!.version}`);

            if (this.agentCard!.capabilities?.streaming) {
                console.log('  Streaming: Supported');
            }

            console.log('Client initialized successfully');
        } catch (error) {
            console.error('Failed to fetch agent card:', error);
            throw error;
        }
    }

    /**
     * Initialize REST transport.
     */
    private async _initRest(): Promise<void> {
        this.client = new A2AClient(this.serverUrl);
        this.agentCard = await this.client.getAgentCard();
    }

    /**
     * Initialize JSON-RPC transport.
     */
    private async _initJsonRpc(): Promise<void> {
        // First fetch agent card via HTTP
        const cardUrl = `${this.serverUrl}/.well-known/agent-card.json`;
        const response = await fetch(cardUrl);
        if (!response.ok) {
            throw new Error(`Failed to fetch agent card: ${response.status}`);
        }
        this.agentCard = await response.json() as AgentCard;

        // Create JSON-RPC transport
        this.rawTransport = new JsonRpcTransport({
            endpoint: this.serverUrl,
        });
    }

    /**
     * Initialize gRPC transport.
     */
    private async _initGrpc(): Promise<void> {
        // For gRPC, agent card is served via HTTP on REST port (grpc_port + 2)
        // Parse the gRPC endpoint to get host and port
        let host = 'localhost';
        let grpcPort = 14000;
        
        // serverUrl for gRPC is in format "host:port" (no http://)
        if (this.serverUrl.includes(':')) {
            const parts = this.serverUrl.split(':');
            host = parts[0];
            grpcPort = parseInt(parts[1], 10);
        }
        
        const restPort = grpcPort + 2;
        const cardUrl = `http://${host}:${restPort}/.well-known/agent-card.json`;
        
        console.log(`Fetching agent card from: ${cardUrl}`);
        const response = await fetch(cardUrl);
        if (!response.ok) {
            throw new Error(`Failed to fetch agent card: ${response.status}`);
        }
        this.agentCard = await response.json() as AgentCard;

        // Create gRPC transport
        this.rawTransport = new GrpcTransport({
            endpoint: this.serverUrl,
        });
    }

    /**
     * Send a message to the agent and wait for response.
     * 
     * @param messageText - The message text to send
     * @param contextId - Optional context ID for conversation continuity
     * @returns The agent's response
     */
    async sendMessage(messageText: string, contextId?: string): Promise<string> {
        console.log(`Sending message: ${messageText}`);

        // Create the message
        const message: Message = {
            messageId: uuidv4(),
            kind: 'message',
            role: 'user',
            parts: [{ kind: 'text', text: messageText }],
        };

        // Add context ID if provided
        if (contextId) {
            message.contextId = contextId;
        }

        // Create params for sendMessageStream
        const params: MessageSendParams = {
            message: message,
        };

        // Send message and collect response
        const responseTexts: string[] = [];

        try {
            // Use appropriate transport for streaming
            if (this.transport === 'rest' && this.client) {
                // Use A2AClient for REST
                const stream = this.client.sendMessageStream(params);
                for await (const event of stream) {
                    this._processStreamEvent(event, responseTexts);
                }
            } else if (this.rawTransport) {
                // Use raw transport for JSON-RPC and gRPC
                const stream = this.rawTransport.sendMessageStream(params);
                for await (const event of stream) {
                    this._processStreamEvent(event, responseTexts);
                }
            } else {
                throw new Error('No transport initialized');
            }

            // Return combined response
            const finalText = responseTexts.join('');
            console.log(`Final response: ${finalText}`);
            return finalText;
        } catch (error) {
            console.error('Error sending message:', error);
            throw error;
        }
    }

    /**
     * Process a stream event and extract text.
     */
    private _processStreamEvent(event: any, responseTexts: string[]): void {
        if (event.kind === 'status-update') {
            const statusUpdate = event as TaskStatusUpdateEvent;
            console.log(`Received status update: ${statusUpdate.status.state}`);

            // Extract text from status message if available
            if (statusUpdate.status.message) {
                const text = this._extractTextFromParts(statusUpdate.status.message.parts);
                if (text) {
                    responseTexts.push(text);
                }
            }

            // Check if this is the final update
            if (statusUpdate.final) {
                console.log('Received final status update');
            }
        } else if (event.kind === 'artifact-update') {
            const artifactUpdate = event as TaskArtifactUpdateEvent;
            console.log(`Received artifact: ${artifactUpdate.artifact.name || '(unnamed)'}`);

            // Extract text from artifact parts
            const text = this._extractTextFromParts(artifactUpdate.artifact.parts);
            if (text) {
                responseTexts.push(text);
            }
        } else if (event.kind === 'message') {
            const msg = event as Message;
            console.log('Received message event');

            // Extract text from message parts
            const text = this._extractTextFromParts(msg.parts);
            if (text) {
                responseTexts.push(text);
            }
        } else if (event.kind === 'task') {
            const task = event as Task;
            console.log(`Received task event: ${task.id}, status: ${task.status.state}`);

            // Check if task is completed
            if (task.status.state === 'completed' || task.status.state === 'failed' || task.status.state === 'canceled') {
                // Extract text from task status message if available
                if (task.status.message) {
                    const text = this._extractTextFromParts(task.status.message.parts);
                    if (text) {
                        responseTexts.push(text);
                    }
                }

                // Extract text from artifacts if available
                if (task.artifacts) {
                    for (const artifact of task.artifacts) {
                        const text = this._extractTextFromParts(artifact.parts);
                        if (text) {
                            responseTexts.push(text);
                        }
                    }
                }
            }
        }
    }

    /**
     * Extract text from message parts.
     * 
     * @param parts - List of message parts
     * @returns Concatenated text from all text parts
     */
    private _extractTextFromParts(parts: Part[]): string {
        const textParts: string[] = [];
        if (parts) {
            for (const part of parts) {
                if (part.kind === 'text' && (part as TextPart).text) {
                    textParts.push((part as TextPart).text);
                }
            }
        }
        return textParts.join('');
    }

    /**
     * Get the agent card.
     */
    getAgentCard(): AgentCard | null {
        return this.agentCard;
    }

    /**
     * Probe transport capability matrix from the agent.
     */
    async probeTransports(): Promise<any> {
        let probeUrl: string;
        
        if (this.transport === 'grpc') {
            // For gRPC, probe via HTTP on REST port
            let host = 'localhost';
            let grpcPort = 14000;
            if (this.serverUrl.includes(':')) {
                const parts = this.serverUrl.split(':');
                host = parts[0];
                grpcPort = parseInt(parts[1], 10);
            }
            const restPort = grpcPort + 2;
            probeUrl = `http://${host}:${restPort}/v1/transports`;
        } else {
            probeUrl = `${this.serverUrl}/v1/transports`;
        }
        
        const response = await fetch(probeUrl);
        if (!response.ok) {
            throw new Error(`Failed to probe transports: ${response.status}`);
        }
        return response.json();
    }

    /**
     * Clean up client resources.
     */
    async close(): Promise<void> {
        console.log('Cleaning up resources...');
        // The A2AClient doesn't have an explicit close method in the current SDK
        console.log('Resource cleanup completed');
    }
}
