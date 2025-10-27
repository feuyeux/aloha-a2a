package com.aloha.a2a.agent;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Startup logger that validates Ollama connection and logs configuration.
 */
@ApplicationScoped
public class StartupLogger {
    
    private static final Logger logger = LoggerFactory.getLogger(StartupLogger.class);
    
    @ConfigProperty(name = "quarkus.langchain4j.ollama.base-url")
    String ollamaBaseUrl;
    
    @ConfigProperty(name = "quarkus.langchain4j.ollama.chat-model.model-id")
    String ollamaModel;
    
    void onStart(@Observes StartupEvent ev) {
        logger.info("=".repeat(80));
        logger.info("Dice Agent Starting");
        logger.info("=".repeat(80));
        logger.info("Ollama Configuration:");
        logger.info("  Base URL: {}", ollamaBaseUrl);
        logger.info("  Model: {}", ollamaModel);
        logger.info("-".repeat(80));
        
        // Validate Ollama connection
        validateOllamaConnection();
        
        logger.info("=".repeat(80));
        logger.info("Agent ready to accept requests");
        logger.info("=".repeat(80));
    }
    
    private void validateOllamaConnection() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaBaseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                logger.info("✓ Successfully connected to Ollama at {}", ollamaBaseUrl);
                
                // Check if the configured model is available
                if (response.body().contains("\"" + ollamaModel + "\"")) {
                    logger.info("✓ Model '{}' is available", ollamaModel);
                } else {
                    logger.warn("⚠ Model '{}' may not be available. Please run: ollama pull {}", 
                            ollamaModel, ollamaModel);
                    logger.warn("  Available models can be listed with: ollama list");
                }
            } else {
                logger.warn("⚠ Ollama responded with status code: {}", response.statusCode());
            }
            
        } catch (Exception e) {
            logger.error("✗ Failed to connect to Ollama at {}", ollamaBaseUrl);
            logger.error("  Error: {}", e.getMessage());
            logger.error("");
            logger.error("Please ensure Ollama is installed and running:");
            logger.error("  1. Install Ollama: https://ollama.ai/download");
            logger.error("  2. Start Ollama service: ollama serve");
            logger.error("  3. Pull the model: ollama pull {}", ollamaModel);
            logger.error("");
            logger.error("The agent will continue to start but LLM features may not work.");
        }
    }
}
