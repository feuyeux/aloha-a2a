package com.aloha.a2a.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Dice Agent implementing A2A protocol with multi-transport support.
 * Uses Langchain4j for LLM integration with tool support.
 */
@RegisterAiService(tools = Tools.class)
@ApplicationScoped
public interface DiceAgent {

    /**
     * Processes user messages and executes dice rolling and prime checking operations.
     *
     * @param question the user's question or request
     * @return the agent's response
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
