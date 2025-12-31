package com.ai.agenticrag.orchestrator;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OllamaHttpClient using MockWebServer.
 */
class OllamaHttpClientTest {

    private MockWebServer mockServer;
    private OllamaHttpClient client;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();
        String baseUrl = mockServer.url("/").toString();
        // Remove trailing slash
        baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        client = new OllamaHttpClient(baseUrl, "test-model");
    }

    @AfterEach
    void tearDown() throws IOException {
        mockServer.shutdown();
    }

    @Test
    void chat_successfulResponse() {
        String responseJson = """
                {
                    "model": "test-model",
                    "created_at": "2024-01-01T00:00:00Z",
                    "message": {
                        "role": "assistant",
                        "content": "This is the response from the model."
                    },
                    "done": true
                }
                """;

        mockServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader("Content-Type", "application/json"));

        String result = client.chat("You are helpful.", "What is AI?");

        assertEquals("This is the response from the model.", result);
    }

    @Test
    void chat_sendsCorrectRequest() throws InterruptedException {
        String responseJson = """
                {
                    "model": "test-model",
                    "message": {"role": "assistant", "content": "Response"},
                    "done": true
                }
                """;

        mockServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader("Content-Type", "application/json"));

        client.chat("System prompt", "User prompt");

        var request = mockServer.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/api/chat", request.getPath());
        assertTrue(request.getHeader("Content-Type").contains("application/json"));

        String body = request.getBody().readUtf8();
        assertTrue(body.contains("test-model"));
        assertTrue(body.contains("System prompt"));
        assertTrue(body.contains("User prompt"));
    }

    @Test
    void chat_handlesNon200Response() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> client.chat("system", "user"));

        assertTrue(exception.getMessage().contains("Ollama API error"));
        assertTrue(exception.getMessage().contains("500"));
    }

    @Test
    void chat_handles400Response() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(400)
                .setBody("Bad Request"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> client.chat("system", "user"));

        assertTrue(exception.getMessage().contains("400"));
    }

    @Test
    void chat_handlesEmptyContent() {
        String responseJson = """
                {
                    "model": "test-model",
                    "message": {"role": "assistant", "content": ""},
                    "done": true
                }
                """;

        mockServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader("Content-Type", "application/json"));

        String result = client.chat("system", "user");

        assertEquals("", result);
    }

    @Test
    void chat_handlesLongResponse() {
        StringBuilder longContent = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longContent.append("This is sentence number ").append(i).append(". ");
        }

        String responseJson = String.format("""
                {
                    "model": "test-model",
                    "message": {"role": "assistant", "content": "%s"},
                    "done": true
                }
                """, longContent.toString().replace("\"", "\\\""));

        mockServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader("Content-Type", "application/json"));

        String result = client.chat("system", "user");

        assertTrue(result.length() > 1000);
    }

    @Test
    void chat_handlesSpecialCharactersInResponse() {
        String responseJson = """
                {
                    "model": "test-model",
                    "message": {"role": "assistant", "content": "Price: $100.00\\nDiscount: 10%\\n\\"Quote\\""},
                    "done": true
                }
                """;

        mockServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader("Content-Type", "application/json"));

        String result = client.chat("system", "user");

        assertTrue(result.contains("$100.00"));
        assertTrue(result.contains("10%"));
    }

    @Test
    void chat_handlesUnicodeContent() {
        String responseJson = """
                {
                    "model": "test-model",
                    "message": {"role": "assistant", "content": "日本語 中文 한국어 🎉"},
                    "done": true
                }
                """;

        mockServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader("Content-Type", "application/json"));

        String result = client.chat("system", "user");

        assertTrue(result.contains("日本語"));
        assertTrue(result.contains("🎉"));
    }

    @Test
    void chat_sendsStreamFalse() throws InterruptedException {
        String responseJson = """
                {
                    "model": "test-model",
                    "message": {"role": "assistant", "content": "Response"},
                    "done": true
                }
                """;

        mockServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader("Content-Type", "application/json"));

        client.chat("system", "user");

        var request = mockServer.takeRequest();
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"stream\":false"));
    }
}
