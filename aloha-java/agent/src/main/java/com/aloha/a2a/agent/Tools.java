package com.aloha.a2a.agent;

import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Tools available to the Dice Agent.
 */
@ApplicationScoped
public class Tools {
    
    private static final Logger logger = LoggerFactory.getLogger(Tools.class);
    private final Random random = new Random();

    /**
     * Rolls an N-sided dice and returns the result.
     * 
     * @param sides The number of sides on the dice (must be positive)
     * @return The result of the dice roll (1 to N)
     */
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

    /**
     * Checks which numbers in the list are prime.
     * 
     * @param numbers List of integers to check
     * @return A string describing which numbers are prime
     */
    @Tool("Checks if the given numbers are prime and returns which ones are prime")
    public String checkPrime(List<Integer> numbers) {
        if (numbers == null || numbers.isEmpty()) {
            return "No numbers provided to check.";
        }
        
        List<Integer> primes = numbers.stream()
                .filter(this::isPrime)
                .collect(Collectors.toList());
        
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

    /**
     * Checks if a number is prime.
     * 
     * @param n The number to check
     * @return true if the number is prime, false otherwise
     */
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
