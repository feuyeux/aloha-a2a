package com.aloha.a2a.agent2.tools;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ToolsTest {

    private final Tools tools = new Tools();

    @Test
    void testRollDice() {
        int result = tools.rollDice(6);
        assertTrue(result >= 1 && result <= 6, "Dice result should be between 1 and 6");
    }

    @Test
    void testRollDiceInvalidSides() {
        assertThrows(IllegalArgumentException.class, () -> tools.rollDice(0));
        assertThrows(IllegalArgumentException.class, () -> tools.rollDice(-1));
    }

    @Test
    void testCheckPrime() {
        String result = tools.checkPrime(List.of(2, 3, 5, 7, 11));
        assertTrue(result.contains("2, 3, 5, 7, 11 are prime numbers"));
    }

    @Test
    void testCheckPrimeWithNonPrimes() {
        String result = tools.checkPrime(List.of(4, 6, 8, 9));
        assertEquals("None of the numbers are prime.", result);
    }

    @Test
    void testCheckPrimeMixed() {
        String result = tools.checkPrime(List.of(2, 4, 7, 9, 11));
        assertTrue(result.contains("2, 7, 11 are prime numbers"));
    }

    @Test
    void testCheckPrimeEmpty() {
        String result = tools.checkPrime(List.of());
        assertEquals("No numbers provided to check.", result);
    }
}
