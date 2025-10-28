package com.aloha.a2a.agent2.agent;

import com.aloha.a2a.agent2.config.OllamaConfig;
import com.aloha.a2a.agent2.tools.Tools;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class DiceAgent {
    
    private static final Logger logger = LoggerFactory.getLogger(DiceAgent.class);
    private final DiceAgentService agentService;
    
    public DiceAgent(OllamaConfig ollamaConfig, Tools tools) {
        logger.info("Initializing DiceAgent with Ollama: {}", ollamaConfig.getBaseUrl());
        
        ChatLanguageModel model = OllamaChatModel.builder()
                .baseUrl(ollamaConfig.getBaseUrl())
                .modelName(ollamaConfig.getModel())
                .temperature(ollamaConfig.getTemperature())
                .timeout(Duration.ofSeconds(ollamaConfig.getTimeout()))
                .build();
        
        this.agentService = AiServices.builder(DiceAgentService.class)
                .chatLanguageModel(model)
                .tools(tools)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();
        
        logger.info("DiceAgent initialized successfully");
    }
    
    public String rollAndAnswer(String question) {
        logger.debug("Processing question: {}", question);
        return agentService.chat(question);
    }
    
    interface DiceAgentService {
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
        String chat(String userMessage);
    }
}
