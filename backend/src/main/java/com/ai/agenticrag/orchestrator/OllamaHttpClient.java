package com.ai.agenticrag.orchestrator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Direct HTTP client for Ollama API, bypassing Spring AI ChatClient
 * to work around integration issues in Spring AI 2.0.0-M1.
 */
@Component
public class OllamaHttpClient {

    private final String baseUrl;
    private final String modelName;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OllamaHttpClient(
            @Value("${spring.ai.ollama.base-url}") String baseUrl,
            @Value("${spring.ai.ollama.chat.model}") String modelName) {
        this.baseUrl = baseUrl;
        this.modelName = modelName;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OllamaHttpClient.class);

    /**
     * Send a chat request to Ollama with system and user prompts.
     *
     * @param systemPrompt The system prompt defining agent behavior
     * @param userPrompt The user prompt with question and context
     * @return The model's response content
     */
    public String chat(String systemPrompt, String userPrompt) {
        try {
            log.info("OLLAMA REQUEST - System prompt length: {} chars", systemPrompt.length());
            log.info("OLLAMA REQUEST - User prompt length: {} chars", userPrompt.length());
            log.info("OLLAMA REQUEST - User prompt preview: {}", userPrompt.substring(0, Math.min(500, userPrompt.length())));

            var request = new ChatRequest(
                modelName,
                List.of(
                    new Message("system", systemPrompt),
                    new Message("user", userPrompt)
                ),
                false // stream = false
            );

            String requestBody = objectMapper.writeValueAsString(request);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/chat"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofMinutes(2))
                    .build();

            HttpResponse<String> response = httpClient.send(
                httpRequest,
                HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) {
                throw new RuntimeException("Ollama API error: " + response.statusCode() + " - " + response.body());
            }

            ChatResponse chatResponse = objectMapper.readValue(response.body(), ChatResponse.class);
            String responseContent = chatResponse.message().content();
            log.info("OLLAMA RESPONSE - Length: {} chars, Preview: {}",
                responseContent.length(),
                responseContent.substring(0, Math.min(300, responseContent.length())));
            return responseContent;

        } catch (Exception e) {
            throw new RuntimeException("Failed to call Ollama API: " + e.getMessage(), e);
        }
    }

    // DTOs for Ollama API
    record ChatRequest(
        String model,
        List<Message> messages,
        boolean stream
    ) {}

    record Message(
        String role,
        String content
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatResponse(
        String model,
        @JsonProperty("created_at") String createdAt,
        Message message,
        boolean done
    ) {}
}
