package com.aloha.a2a.agent;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manual test for DiceAgent with Ollama integration.
 * Run this test to verify the agent works with Ollama qwen2.5.
 */
@QuarkusTest
public class DiceAgentManualTest {
    
    private static final Logger logger = LoggerFactory.getLogger(DiceAgentManualTest.class);
    
    @Inject
    DiceAgent diceAgent;
    
    @Test
    public void testRollDice() {
        logger.info("=== Test 1: Roll Dice ===");
        String response = diceAgent.rollAndAnswer("Roll a 20-sided dice");
        logger.info("Response: {}", response);
        System.out.println("\n=== Roll Dice Response ===");
        System.out.println(response);
        System.out.println("==========================\n");
    }
    
    @Test
    public void testCheckPrime() {
        logger.info("=== Test 2: Check Prime ===");
        String response = diceAgent.rollAndAnswer("Is 17 prime?");
        logger.info("Response: {}", response);
        System.out.println("\n=== Check Prime Response ===");
        System.out.println(response);
        System.out.println("============================\n");
    }
    
    @Test
    public void testCombinedOperation() {
        logger.info("=== Test 3: Combined Operation ===");
        String response = diceAgent.rollAndAnswer("Roll a 12-sided dice and check if the result is prime");
        logger.info("Response: {}", response);
        System.out.println("\n=== Combined Operation Response ===");
        System.out.println(response);
        System.out.println("===================================\n");
    }
    
    @Test
    public void testMultiplePrimes() {
        logger.info("=== Test 4: Multiple Primes ===");
        String response = diceAgent.rollAndAnswer("Check if 2, 7, 11, 15, 17 are prime");
        logger.info("Response: {}", response);
        System.out.println("\n=== Multiple Primes Response ===");
        System.out.println(response);
        System.out.println("================================\n");
    }
}
