package com.aloha.a2a.server;

import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Tools available to the Dice Agent for LLM function calling.
 * <p>
 * This class provides two main tools that can be invoked by the LLM:
 * <ul>
 *   <li>{@link #rollDice(int)} - Rolls an N-sided dice</li>
 *   <li>{@link #checkPrime(List)} - Checks if numbers are prime</li>
 * </ul>
 * <p>
 * These tools are automatically registered with Langchain4j through the {@code @Tool} annotation
 * and can be called by the LLM based on user requests.
 *
 * @see DiceAgent
 * @see dev.langchain4j.agent.tool.Tool
 */
public class Tools {

    private static final Logger logger = LoggerFactory.getLogger(Tools.class);
    private final Random random = new Random();

    /**
     * Rolls an N-sided dice and returns a random result.
     * <p>
     * This tool simulates rolling a dice with the specified number of sides.
     * The result is a random integer between 1 and N (inclusive).
     * <p>
     * Example usage by LLM:
     * <ul>
     *   <li>"Roll a 6-sided dice" → calls rollDice(6)</li>
     *   <li>"Roll a d20" → calls rollDice(20)</li>
     * </ul>
     *
     * @param sides the number of sides on the dice (must be positive)
     * @return the result of the dice roll, a random integer from 1 to sides (inclusive)
     * @throws IllegalArgumentException if sides is less than or equal to 0
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
     * Checks which numbers in the provided list are prime numbers.
     * <p>
     * This tool analyzes a list of integers and identifies which ones are prime.
     * A prime number is a natural number greater than 1 that has no positive divisors
     * other than 1 and itself.
     * <p>
     * Example usage by LLM:
     * <ul>
     *   <li>"Is 17 prime?" → calls checkPrime([17])</li>
     *   <li>"Check if 2, 4, 7, 9, 11 are prime" → calls checkPrime([2, 4, 7, 9, 11])</li>
     * </ul>
     *
     * @param numbers the list of integers to check for primality (must not be null)
     * @return a human-readable string describing which numbers are prime,
     * or a message indicating no primes were found
     */
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

    /**
     * Determines if a given number is prime using trial division.
     * <p>
     * This is a private helper method used by {@link #checkPrime(List)}.
     * The algorithm uses trial division, checking divisibility up to the square root
     * of the number for efficiency.
     * <p>
     * Algorithm:
     * <ol>
     *   <li>Numbers ≤ 1 are not prime</li>
     *   <li>2 is prime (the only even prime)</li>
     *   <li>Even numbers > 2 are not prime</li>
     *   <li>For odd numbers, check divisibility by odd numbers from 3 to √n</li>
     * </ol>
     *
     * @param n the number to check for primality
     * @return {@code true} if the number is prime, {@code false} otherwise
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
