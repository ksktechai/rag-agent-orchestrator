package com.ai.agenticrag.orchestrator.agents;

import com.ai.agenticrag.orchestrator.AgentContext;
import com.ai.agenticrag.orchestrator.OllamaHttpClient;
import com.ai.agenticrag.rag.ChunkHit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SynthesizerAgent.
 */
@ExtendWith(MockitoExtension.class)
class SynthesizerAgentTest {

    @Mock
    private OllamaHttpClient ollamaClient;

    private SynthesizerAgent synthesizerAgent;

    @BeforeEach
    void setUp() {
        synthesizerAgent = new SynthesizerAgent(ollamaClient);
    }

    @Test
    void name_returnsSynthesizer() {
        assertEquals("synthesizer", synthesizerAgent.name());
    }

    @Test
    void trace_returnsEventWithRetrievedCount() {
        AgentContext ctx = new AgentContext("question");
        ctx.retrieved.add(new ChunkHit(1L, 100L, "Doc", 0, "Content", 0.9));
        ctx.retrieved.add(new ChunkHit(2L, 100L, "Doc", 1, "Content2", 0.8));

        StepVerifier.create(synthesizerAgent.trace(ctx))
                .assertNext(event -> {
                    assertEquals("synthesizer", event.agent());
                    assertEquals("synthesize", event.type());
                    assertEquals(2, event.data().get("retrieved"));
                })
                .verifyComplete();
    }

    @Test
    void trace_returnsZeroWhenNoChunks() {
        AgentContext ctx = new AgentContext("question");

        StepVerifier.create(synthesizerAgent.trace(ctx))
                .assertNext(event -> {
                    assertEquals(0, event.data().get("retrieved"));
                })
                .verifyComplete();
    }

    @Test
    void run_callsOllamaAndSetsDraftAnswer() {
        AgentContext ctx = new AgentContext("What is AI?");
        ctx.retrieved.add(new ChunkHit(1L, 100L, "AI Doc", 0, "AI is artificial intelligence", 0.95));

        when(ollamaClient.chat(anyString(), anyString()))
                .thenReturn("AI stands for Artificial Intelligence [chunk:1]");

        StepVerifier.create(synthesizerAgent.run(ctx))
                .verifyComplete();

        assertEquals("AI stands for Artificial Intelligence [chunk:1]", ctx.draftAnswer);
    }

    @Test
    void run_includesChunkIdsInContext() {
        AgentContext ctx = new AgentContext("Question");
        ctx.retrieved.add(new ChunkHit(42L, 100L, "Doc", 0, "Content about the topic", 0.9));

        when(ollamaClient.chat(anyString(), anyString()))
                .thenReturn("Answer");

        StepVerifier.create(synthesizerAgent.run(ctx))
                .verifyComplete();

        // Verify the user prompt contains the chunk ID
        verify(ollamaClient).chat(anyString(), argThat(userPrompt -> userPrompt.contains("[chunk:42]") &&
                userPrompt.contains("Content about the topic")));
    }

    @Test
    void run_includesAvailableChunkIdsInPrompt() {
        AgentContext ctx = new AgentContext("Question");
        ctx.retrieved.add(new ChunkHit(1L, 100L, "Doc", 0, "Content1", 0.9));
        ctx.retrieved.add(new ChunkHit(5L, 100L, "Doc", 1, "Content2", 0.8));
        ctx.retrieved.add(new ChunkHit(10L, 100L, "Doc", 2, "Content3", 0.7));

        when(ollamaClient.chat(anyString(), anyString()))
                .thenReturn("Answer");

        StepVerifier.create(synthesizerAgent.run(ctx))
                .verifyComplete();

        verify(ollamaClient).chat(anyString(),
                argThat(userPrompt -> userPrompt.contains("1, 5, 10") || userPrompt.contains("1,5,10")));
    }

    @Test
    void run_includesQuestionInPrompt() {
        AgentContext ctx = new AgentContext("What is the meaning of life?");
        ctx.retrieved.add(new ChunkHit(1L, 100L, "Doc", 0, "Philosophy content", 0.9));

        when(ollamaClient.chat(anyString(), anyString()))
                .thenReturn("42");

        StepVerifier.create(synthesizerAgent.run(ctx))
                .verifyComplete();

        verify(ollamaClient).chat(anyString(), contains("What is the meaning of life?"));
    }

    @Test
    void run_usesSystemPromptWithCitationRules() {
        AgentContext ctx = new AgentContext("Q");
        ctx.retrieved.add(new ChunkHit(1L, 100L, "Doc", 0, "C", 0.9));

        when(ollamaClient.chat(anyString(), anyString()))
                .thenReturn("A");

        StepVerifier.create(synthesizerAgent.run(ctx))
                .verifyComplete();

        verify(ollamaClient).chat(argThat(systemPrompt -> systemPrompt.contains("CITATION RULES") &&
                systemPrompt.contains("[chunk:ID]")), anyString());
    }

    @Test
    void run_handlesEmptyRetrievedChunks() {
        AgentContext ctx = new AgentContext("Question");
        // No chunks retrieved

        when(ollamaClient.chat(anyString(), anyString()))
                .thenReturn("I don't have enough context");

        StepVerifier.create(synthesizerAgent.run(ctx))
                .verifyComplete();

        assertEquals("I don't have enough context", ctx.draftAnswer);
    }

    @Test
    void run_handlesMultipleChunks() {
        AgentContext ctx = new AgentContext("Compare X and Y");
        for (int i = 1; i <= 5; i++) {
            ctx.retrieved.add(new ChunkHit(i, 100L, "Doc" + i, i - 1, "Content " + i, 0.9 - i * 0.1));
        }

        when(ollamaClient.chat(anyString(), anyString()))
                .thenReturn("Based on [chunk:1] and [chunk:3], X is better than Y.");

        StepVerifier.create(synthesizerAgent.run(ctx))
                .verifyComplete();

        assertNotNull(ctx.draftAnswer);
        assertTrue(ctx.draftAnswer.contains("[chunk:1]"));
    }
}
