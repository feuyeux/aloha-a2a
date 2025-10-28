package com.aloha.a2a.agent2.tools;

import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Component
public class Tools {
    
    private static final Logger logger = LoggerFactory.getLogger(Tools.class);
    private final Random random = new Random();

    @Tool("Rolls an N-sided dice and returns a random number between 1 and N")
    public int rollDice(int sides) {
        if (sides <= 0) {
            logger.error("Invalid dice sides: {}", sides);
            throw new IllegalArgumentException("Dice must have at least 1 side");
        }
        
        int result = random.nextInt(sides) + 1;
        logger.info("Rolled {}-sided dice: {}", sides, result);
        return result;
    }

    @Tool("Checks if the given numbers are prime and returns which ones are prime")
    public String checkPrime(List<Integer> numbers) {
        if (numbers == null || numbers.isEmpty()) {
            return "No numbers provided to check.";
        }
        
        List<Integer> primes = numbers.stream()
                .filter(this::isPrime)
                .toList();
        
        if (primes.isEmpty()) {
            logger.info("No prime numbers found in: {}", numbers);
            return "None of the numbers are prime.";
        }
        
        String result = primes.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(", ")) + " are prime numbers.";
        
        logger.info("Prime check for {}: {}", numbers, result);
        return result;
    }

    private boolean isPrime(int n) {
        if (n <= 1) {
            return false;
        }
        if (n == 2) {
            return true;
        }
        if (n % 2 == 0) {
            return false;
        }
        
        int sqrt = (int) Math.sqrt(n);
        for (int i = 3; i <= sqrt; i += 2) {
            if (n % i == 0) {
                return false;
            }
        }
        
        return true;
    }
}
