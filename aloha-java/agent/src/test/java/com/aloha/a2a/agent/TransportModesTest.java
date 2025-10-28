package com.aloha.a2a.agent;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for all three transport modes and agent tools.
 * <p>
 * This test class covers:
 * <ul>
 *   <li>Unit tests for Tools class (rollDice, checkPrime)</li>
 *   <li>Integration tests for gRPC, JSON-RPC, and REST transport modes</li>
 *   <li>End-to-end tests with actual LLM and tool invocation (requires Ollama)</li>
 * </ul>
 * <p>
 * <b>Running tests</b>:
 * <pre>
 * # Without Ollama (unit tests + agent card tests)
 * mvn test -Dtest=TransportModesTest
 *
 * # With Ollama (all tests including E2E)
 * mvn test -Dtest=TransportModesTest -Dollama.enabled=true
 * </pre>
 */
@QuarkusTest
public class TransportModesTest {

    @Inject
    Tools tools;

    /**
     * Unit tests for the Tools class.
     */
    @Nested
    class ToolsUnitTests {

        @Test
        void testRollDice_ValidRange() {
            int result = tools.rollDice(6);
            assertTrue(result >= 1 && result <= 6);
        }

        @Test
        void testRollDice_InvalidSides() {
            assertThrows(IllegalArgumentException.class, () -> tools.rollDice(0));
            assertThrows(IllegalArgumentException.class, () -> tools.rollDice(-1));
        }

        @Test
        void testCheckPrime_SinglePrime() {
            String result = tools.checkPrime(List.of(17));
            assertTrue(result.contains("17") && result.contains("prime"));
        }

        @Test
        void testCheckPrime_MixedNumbers() {
            String result = tools.checkPrime(Arrays.asList(2, 4, 7, 9, 11));
            assertTrue(result.contains("2") && result.contains("7") && result.contains("11"));
        }

        @Test
        void testCheckPrime_EmptyList() {
            String result = tools.checkPrime(Collections.emptyList());
            assertTrue(result.contains("No numbers"));
        }
    }

    /**
     * gRPC transport mode tests.
     */
    @Nested
    @QuarkusTest
    @TestProfile(GrpcProfile.class)
    class GrpcModeTests {

        @Inject
        DiceAgent diceAgent;

        @Test
        void testAgentCard() {
            given()
                .when()
                    .get("/.well-known/agent-card.json")
                .then()
                    .statusCode(200)
                    .body("name", equalTo("Dice Agent"))
                    .body("preferredTransport", equalTo("GRPC"))
                    .body("url", equalTo("localhost:11000"))
                    .body("skills.size()", equalTo(2));
        }

        @Test
        @EnabledIfSystemProperty(named = "ollama.enabled", matches = "true")
        void testRollDice() {
            String response = diceAgent.rollAndAnswer("Roll a 20-sided dice");
            assertNotNull(response);
            assertTrue(response.matches(".*\\d+.*"));
            System.out.println("[gRPC] Roll: " + response);
        }

        @Test
        @EnabledIfSystemProperty(named = "ollama.enabled", matches = "true")
        void testCheckPrime() {
            String response = diceAgent.rollAndAnswer("Is 17 prime?");
            assertNotNull(response);
            assertTrue(response.contains("17") && response.toLowerCase().contains("prime"));
            System.out.println("[gRPC] Prime: " + response);
        }
    }

    /**
     * JSON-RPC transport mode tests.
     */
    @Nested
    @QuarkusTest
    @TestProfile(JsonRpcProfile.class)
    class JsonRpcModeTests {

        @Inject
        DiceAgent diceAgent;

        @Test
        void testAgentCard() {
            given()
                .when()
                    .get("/.well-known/agent-card.json")
                .then()
                    .statusCode(200)
                    .body("name", equalTo("Dice Agent"))
                    .body("preferredTransport", equalTo("JSONRPC"))
                    .body("url", startsWith("http://localhost:11001"))
                    .body("skills.size()", equalTo(2));
        }

        @Test
        @EnabledIfSystemProperty(named = "ollama.enabled", matches = "true")
        void testRollDice() {
            String response = diceAgent.rollAndAnswer("Roll a 12-sided dice");
            assertNotNull(response);
            assertTrue(response.matches(".*\\d+.*"));
            System.out.println("[JSON-RPC] Roll: " + response);
        }

        @Test
        @EnabledIfSystemProperty(named = "ollama.enabled", matches = "true")
        void testCheckPrime() {
            String response = diceAgent.rollAndAnswer("Check if 2, 7, 11 are prime");
            assertNotNull(response);
            assertTrue(response.contains("2") && response.contains("7") && response.contains("11"));
            System.out.println("[JSON-RPC] Prime: " + response);
        }
    }

    /**
     * REST (HTTP+JSON) transport mode tests.
     */
    @Nested
    @QuarkusTest
    @TestProfile(RestProfile.class)
    class RestModeTests {

        @Inject
        DiceAgent diceAgent;

        @Test
        void testAgentCard() {
            given()
                .when()
                    .get("/.well-known/agent-card.json")
                .then()
                    .statusCode(200)
                    .body("name", equalTo("Dice Agent"))
                    .body("preferredTransport", equalTo("HTTP+JSON"))
                    .body("url", startsWith("http://localhost:11002"))
                    .body("skills.size()", equalTo(2));
        }

        @Test
        @EnabledIfSystemProperty(named = "ollama.enabled", matches = "true")
        void testRollDice() {
            String response = diceAgent.rollAndAnswer("Roll a 6-sided dice");
            assertNotNull(response);
            assertTrue(response.matches(".*\\d+.*"));
            System.out.println("[REST] Roll: " + response);
        }

        @Test
        @EnabledIfSystemProperty(named = "ollama.enabled", matches = "true")
        void testCheckPrime() {
            String response = diceAgent.rollAndAnswer("Is 13 prime?");
            assertNotNull(response);
            assertTrue(response.contains("13") && response.toLowerCase().contains("prime"));
            System.out.println("[REST] Prime: " + response);
        }
    }

    // Test Profiles
    public static class GrpcProfile implements io.quarkus.test.junit.QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "grpc";
        }
    }

    public static class JsonRpcProfile implements io.quarkus.test.junit.QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "jsonrpc";
        }
    }

    public static class RestProfile implements io.quarkus.test.junit.QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "rest";
        }
    }
}
