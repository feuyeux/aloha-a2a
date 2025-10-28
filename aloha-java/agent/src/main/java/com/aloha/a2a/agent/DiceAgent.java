package com.aloha.a2a.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Dice Agent interface for A2A protocol implementation with LLM integration.
 * <p>
 * This interface is automatically implemented by Langchain4j through the {@code @RegisterAiService}
 * annotation. It provides natural language processing capabilities using Ollama with the qwen2.5 model.
 * <p>
 * The agent can:
 * <ul>
 *   <li>Roll N-sided dice (e.g., "Roll a 20-sided dice")</li>
 *   <li>Check if numbers are prime (e.g., "Is 17 prime?")</li>
 *   <li>Perform combined operations (e.g., "Roll a dice and check if it's prime")</li>
 *   <li>Support both English and Chinese language inputs</li>
 * </ul>
 * <p>
 * The agent uses the {@link Tools} class for actual dice rolling and prime checking operations.
 * The LLM decides which tools to call based on the user's natural language input.
 *
 * @see Tools
 * @see DiceAgentExecutor
 * @see io.quarkiverse.langchain4j.RegisterAiService
 */
@RegisterAiService(tools = Tools.class)
@ApplicationScoped
public interface DiceAgent {

    /**
     * Processes user messages and executes dice rolling and prime checking operations.
     * <p>
     * This method is the main entry point for the agent. It:
     * <ol>
     *   <li>Receives a natural language question from the user</li>
     *   <li>Sends it to the LLM (Ollama with qwen2.5 model)</li>
     *   <li>The LLM analyzes the request and decides which tools to call</li>
     *   <li>Executes the appropriate tool(s) from {@link Tools}</li>
     *   <li>Returns a natural language response to the user</li>
     * </ol>
     * <p>
     * Example interactions:
     * <ul>
     *   <li>Input: "Roll a 20-sided dice" → Output: "The result is 13."</li>
     *   <li>Input: "Is 17 prime?" → Output: "Yes, 17 is a prime number."</li>
     *   <li>Input: "投掷一个6面骰子" → Output: "你投掷了一个6面骰子，结果是4。"</li>
     * </ul>
     *
     * @param question the user's question or request in natural language (English or Chinese)
     * @return the agent's response in natural language, including tool execution results
     */
    @SystemMessage(
            """
                    You are a dice rolling agent that can roll arbitrary N-sided dice and check if numbers are prime.
                    
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
                    始终使用工具，不要自己计算。
                    """)
    String rollAndAnswer(@UserMessage String question);
}
