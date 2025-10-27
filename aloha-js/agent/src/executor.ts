/**
 * Dice Agent Executor implementing A2A protocol with LLM integration.
 */

import { Ollama } from 'ollama';
import { rollDice, checkPrime } from './tools.js';

/**
 * System prompt for the agent
 */
const SYSTEM_PROMPT = `You are a dice rolling agent that can roll arbitrary N-sided dice and check if numbers are prime.

When asked to roll a dice, call the rollDice tool with the number of sides as an integer parameter.

When asked to check if numbers are prime, call the checkPrime tool with a list of integers.

When asked to roll a dice and check if the result is prime:
1. First call rollDice to get the result
2. Then call checkPrime with the result from step 1
3. Include both the dice result and prime check in your response

Always use the tools - never try to roll dice or check primes yourself.
Be conversational and friendly in your responses.

你是一个骰子代理，可以投掷任意面数的骰子并检查数字是否为质数。
当被要求投掷骰子时，使用 rollDice 工具。
当被要求检查质数时，使用 checkPrime 工具。
始终使用工具，不要自己计算。`;

/**
 * Tool definitions for Ollama
 */
const TOOLS: any[] = [
    {
        type: 'function',
        function: {
            name: 'rollDice',
            description: 'Rolls an N-sided dice and returns a random number between 1 and N',
            parameters: {
                type: 'object',
                properties: {
                    sides: {
                        type: 'number',
                        description: 'The number of sides on the dice (must be positive)',
                    },
                },
                required: ['sides'],
            },
        },
    },
    {
        type: 'function',
        function: {
            name: 'checkPrime',
            description: 'Checks if the given numbers are prime and returns which ones are prime',
            parameters: {
                type: 'object',
                properties: {
                    numbers: {
                        type: 'array',
                        items: { type: 'number' },
                        description: 'List of integers to check for primality',
                    },
                },
                required: ['numbers'],
            },
        },
    },
];

/**
 * Agent executor for the Dice Agent.
 * Processes requests using LLM with tool support.
 */
export class DiceAgentExecutor {
    private client: Ollama | null;
    private model: string;
    private baseUrl: string;

    constructor() {
        this.baseUrl = process.env.OLLAMA_BASE_URL || 'http://localhost:11434';
        this.model = process.env.OLLAMA_MODEL || 'qwen2.5';
        this.client = null;
        this._setupLLM();
    }

    /**
     * Setup LLM with Ollama client.
     */
    private _setupLLM(): void {
        try {
            this.client = new Ollama({ host: this.baseUrl });
            console.log(`Ollama client initialized with base URL: ${this.baseUrl}, model: ${this.model}`);
            
            // Validate connection on startup
            this._validateConnection().catch((error) => {
                console.warn('Ollama connection validation failed:', error.message);
                console.warn('Agent will use fallback mode. Please ensure Ollama is running:');
                console.warn('  1. Install Ollama: https://ollama.ai/download');
                console.warn(`  2. Pull model: ollama pull ${this.model}`);
                console.warn('  3. Start Ollama: ollama serve');
                this.client = null;
            });
        } catch (error: any) {
            console.warn('Failed to initialize Ollama client:', error.message);
            console.warn('Agent will use fallback mode');
            this.client = null;
        }
    }

    /**
     * Validate Ollama connection by listing models.
     */
    private async _validateConnection(): Promise<void> {
        if (!this.client) {
            throw new Error('Ollama client not initialized');
        }

        try {
            const response = await this.client.list();
            const modelNames = response.models.map((m: any) => m.name);
            
            if (!modelNames.some((name: string) => name.includes(this.model))) {
                throw new Error(
                    `Model '${this.model}' not found in Ollama. ` +
                    `Available models: ${modelNames.join(', ') || 'none'}. ` +
                    `Please pull the model: ollama pull ${this.model}`
                );
            }
            
            console.log(`Ollama connection validated. Model '${this.model}' is available.`);
        } catch (error: any) {
            throw new Error(
                `Failed to connect to Ollama at ${this.baseUrl}: ${error.message}. ` +
                `Please ensure Ollama is installed and running.`
            );
        }
    }

    /**
     * Execute agent logic and return response.
     * 
     * @param messageText - The user's message
     * @returns The agent's response
     */
    async execute(messageText: string): Promise<string> {
        console.log(`Processing message: ${messageText}`);

        try {
            if (!this.client) {
                // Fallback mode without LLM
                return this._fallbackProcessing(messageText);
            }

            // Process with LLM
            const response = await this._processWithLLM(messageText);
            console.log(`LLM returned response: ${response}`);
            return response;
        } catch (error: any) {
            console.error('Error during execution:', error);
            
            // If Ollama fails, try fallback
            if (error.message.includes('Ollama') || error.message.includes('ECONNREFUSED')) {
                console.warn('Ollama unavailable, using fallback mode');
                return this._fallbackProcessing(messageText);
            }
            
            throw error;
        }
    }

    /**
     * Process message with LLM and execute tools as needed.
     * 
     * @param messageText - The user's message
     * @returns The agent's response
     */
    private async _processWithLLM(messageText: string): Promise<string> {
        if (!this.client) {
            throw new Error('Ollama client not available');
        }

        try {
            const messages: any[] = [
                { role: 'system', content: SYSTEM_PROMPT },
                { role: 'user', content: messageText },
            ];

            let maxIterations = 5; // Prevent infinite loops
            let iteration = 0;

            while (iteration < maxIterations) {
                iteration++;

                // Call Ollama with tools
                const response = await this.client.chat({
                    model: this.model,
                    messages: messages,
                    tools: TOOLS,
                });

                // Check if tools were called
                if (response.message.tool_calls && response.message.tool_calls.length > 0) {
                    console.log(`LLM requested ${response.message.tool_calls.length} tool call(s) in iteration ${iteration}`);
                    
                    // Add the assistant's message with tool calls
                    messages.push(response.message);

                    // Execute tools and collect results
                    for (const toolCall of response.message.tool_calls) {
                        const result = await this._executeToolCall(toolCall);
                        messages.push({
                            role: 'tool',
                            content: JSON.stringify(result),
                        });
                    }

                    // Continue loop to get next response
                    continue;
                }

                // No more tools to call, return the response
                return response.message.content;
            }

            // Max iterations reached
            console.warn('Max tool call iterations reached');
            return 'I processed your request but reached the maximum number of tool calls.';
        } catch (error: any) {
            console.error('Error processing with LLM:', error);
            throw new Error(`Ollama processing failed: ${error.message}`);
        }
    }

    /**
     * Execute a tool call from the LLM.
     * 
     * @param toolCall - The tool call from Ollama
     * @returns The result of the tool execution
     */
    private async _executeToolCall(toolCall: any): Promise<any> {
        const functionName = toolCall.function.name;
        const args = toolCall.function.arguments;

        console.log(`Executing tool: ${functionName} with args:`, args);

        try {
            switch (functionName) {
                case 'rollDice': {
                    const sides = args.sides;
                    if (typeof sides !== 'number' || sides <= 0) {
                        throw new Error('Invalid sides parameter: must be a positive number');
                    }
                    const result = rollDice(sides);
                    return { result, sides };
                }

                case 'checkPrime': {
                    const numbers = args.numbers;
                    if (!Array.isArray(numbers)) {
                        throw new Error('Invalid numbers parameter: must be an array');
                    }
                    const result = checkPrime(numbers);
                    return { result, numbers };
                }

                default:
                    throw new Error(`Unknown tool: ${functionName}`);
            }
        } catch (error: any) {
            console.error(`Tool execution error for ${functionName}:`, error);
            return { error: error.message };
        }
    }

    /**
     * Fallback processing without LLM (simple pattern matching).
     * 
     * @param messageText - The user's message
     * @returns Response based on simple pattern matching
     */
    private _fallbackProcessing(messageText: string): string {
        const messageLower = messageText.toLowerCase();

        // Simple pattern matching for dice rolling
        if (messageLower.includes('roll') && messageLower.includes('dice')) {
            // Try to extract number
            const match = messageLower.match(/(\d+)[-\s]?sided/);
            if (match) {
                const sides = parseInt(match[1], 10);
                const result = rollDice(sides);
                return `I rolled a ${sides}-sided dice and got: ${result}`;
            } else {
                // Default to 6-sided
                const result = rollDice(6);
                return `I rolled a 6-sided dice and got: ${result}`;
            }
        }

        // Simple pattern matching for prime checking
        if (messageLower.includes('prime')) {
            const numbers = messageText.match(/\b\d+\b/g)?.map(n => parseInt(n, 10)) || [];
            if (numbers.length > 0) {
                const result = checkPrime(numbers);
                return result;
            } else {
                return 'Please provide numbers to check for primality.';
            }
        }

        return 'I can roll dice and check if numbers are prime. What would you like me to do?';
    }
}
