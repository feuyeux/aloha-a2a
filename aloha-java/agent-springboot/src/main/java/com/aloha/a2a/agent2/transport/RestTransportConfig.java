package com.aloha.a2a.agent2.transport;

import com.aloha.a2a.agent2.card.AgentCardProvider;
import com.aloha.a2a.agent2.executor.DiceAgentExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@ConditionalOnProperty(name = "transport.mode", havingValue = "rest")
public class RestTransportConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(RestTransportConfig.class);
    
    @Bean
    public SpringRestHandler restTransportHandler(
            DiceAgentExecutor executor,
            AgentCardProvider cardProvider) {
        logger.info("Initializing REST transport handler");
        return new SpringRestHandler(executor, cardProvider.getAgentCard());
    }
    
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins("*")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*");
            }
        };
    }
}

