package com.aloha.a2a.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Application configuration loader.
 * <p>
 * Loads configuration from {@code application.properties} on the classpath
 * and allows system property overrides (e.g., {@code -Dtransport.mode=grpc}).
 */
public class AppConfig {

    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);
    private final Properties properties = new Properties();

    public AppConfig() {
        // Load from classpath
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (is != null) {
                properties.load(is);
                logger.info("Loaded application.properties from classpath");
            } else {
                logger.warn("application.properties not found on classpath, using defaults");
            }
        } catch (IOException e) {
            logger.warn("Failed to load application.properties: {}", e.getMessage());
        }
    }

    /**
     * Gets a property value. System properties take precedence over file values.
     */
    public String get(String key, String defaultValue) {
        String sysVal = System.getProperty(key);
        if (sysVal != null) {
            return sysVal;
        }
        return properties.getProperty(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        String val = get(key, null);
        if (val == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            logger.warn("Invalid integer for key '{}': '{}', using default {}", key, val, defaultValue);
            return defaultValue;
        }
    }

    public double getDouble(String key, double defaultValue) {
        String val = get(key, null);
        if (val == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(val.trim());
        } catch (NumberFormatException e) {
            logger.warn("Invalid double for key '{}': '{}', using default {}", key, val, defaultValue);
            return defaultValue;
        }
    }

    // Convenience accessors

    public String getTransportMode() {
        return get("transport.mode", "grpc").toLowerCase();
    }

    public int getGrpcPort() {
        return getInt("grpc.server.port", 11000);
    }

    public int getHttpPort() {
        return getInt("http.port", 11001);
    }

    public int getRestPort() {
        return getInt("rest.port", 11002);
    }

    public String getOllamaBaseUrl() {
        return get("ollama.base-url", "http://localhost:11434");
    }

    public String getOllamaModel() {
        return get("ollama.model", "qwen2.5");
    }

    public double getOllamaTemperature() {
        return getDouble("ollama.temperature", 0.7);
    }

    public int getOllamaTimeout() {
        return getInt("ollama.timeout", 60);
    }

    public String getAgentName() {
        return get("agent.name", "Dice Agent");
    }

    public String getAgentDescription() {
        return get("agent.description", "An agent that can roll arbitrary dice and check prime numbers");
    }

    public String getAgentVersion() {
        return get("agent.version", "1.0.0");
    }
}
