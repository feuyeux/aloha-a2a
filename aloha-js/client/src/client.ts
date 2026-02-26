/**
 * A2A Client with REST transport support.
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
import { A2AClient } from '@a2a-js/sdk/client';

/**
 * A2A Client with REST transport support.
 */
export class AlohaClient {
    private serverUrl: string;
    private transport: string;
    private client: A2AClient;
    private agentCard: AgentCard | null = null;

    constructor(serverUrl: string, transport: string = 'rest') {
        this.serverUrl = serverUrl;
        this.transport = transport.toLowerCase();

        // Create the A2A client
        // Note: The @a2a-js/sdk currently primarily supports REST transport
        this.client = new A2AClient(serverUrl);

        console.log(`Client created for ${serverUrl} using ${this.transport} transport`);
    }

    /**
     * Initialize the client by fetching agent card.
     */
    async initialize(): Promise<void> {
        console.log(`Connecting to agent at: ${this.serverUrl}`);

        try {
            // Fetch the public agent card
            this.agentCard = await this.client.getAgentCard();

            console.log('Successfully fetched public agent card:');
            console.log(`  Name: ${this.agentCard.name}`);
            console.log(`  Description: ${this.agentCard.description}`);
            console.log(`  Version: ${this.agentCard.version}`);

            if (this.agentCard.capabilities?.streaming) {
                console.log('  Streaming: Supported');
            }

            console.log('Client initialized successfully');
        } catch (error) {
            console.error('Failed to fetch agent card:', error);
            throw error;
        }
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
        let finalResponse = false;

        try {
            // Use sendMessageStream to get streaming events
            const stream = this.client.sendMessageStream(params);

            // Iterate over events from the stream
            for await (const event of stream) {
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
                        finalResponse = true;
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

                        finalResponse = true;
                    }
                }
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
        const response = await fetch(`${this.serverUrl}/v1/transports`);
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
