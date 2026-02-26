package com.aloha.a2a.server;

import com.aloha.a2a.server.config.AppConfig;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Dice Agent with LLM integration using plain Langchain4j + Ollama.
 * <p>
 * Provides natural language processing capabilities using Ollama with the qwen2.5 model.
 * The agent can:
 * <ul>
 *   <li>Roll N-sided dice (e.g., "Roll a 20-sided dice")</li>
 *   <li>Check if numbers are prime (e.g., "Is 17 prime?")</li>
 *   <li>Perform combined operations (e.g., "Roll a dice and check if it's prime")</li>
 *   <li>Support both English and Chinese language inputs</li>
 * </ul>
 *
 * @see Tools
 * @see DiceAgentExecutor
 */
public class DiceAgent {

    private static final Logger logger = LoggerFactory.getLogger(DiceAgent.class);

    private final DiceAgentService agentService;

    /**
     * Internal AI service interface. Langchain4j generates the implementation
     * via {@link AiServices#builder(Class)}.
     */
    private interface DiceAgentService {
        @SystemMessage("""
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

    public DiceAgent(AppConfig config, Tools tools) {
        logger.info("Initializing DiceAgent with Ollama at {}, model={}", config.getOllamaBaseUrl(), config.getOllamaModel());

        ChatModel model = OllamaChatModel.builder()
                .baseUrl(config.getOllamaBaseUrl())
                .modelName(config.getOllamaModel())
                .temperature(config.getOllamaTemperature())
                .timeout(Duration.ofSeconds(config.getOllamaTimeout()))
                .build();

        this.agentService = AiServices.builder(DiceAgentService.class)
                .chatModel(model)
                .tools(tools)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();

        logger.info("DiceAgent initialized successfully");
    }

    /**
     * Processes user messages and executes dice rolling and prime checking operations.
     *
     * @param question the user's question or request in natural language
     * @return the agent's response in natural language
     */
    public String rollAndAnswer(String question) {
        return agentService.rollAndAnswer(question);
    }
}
