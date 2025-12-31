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
 * Unit tests for QueryRewriteAgent.
 */
@ExtendWith(MockitoExtension.class)
class QueryRewriteAgentTest {

    @Mock
    private OllamaHttpClient ollamaClient;

    private QueryRewriteAgent queryRewriteAgent;

    @BeforeEach
    void setUp() {
        queryRewriteAgent = new QueryRewriteAgent(ollamaClient);
    }

    @Test
    void name_returnsQueryRewriter() {
        assertEquals("query-rewriter", queryRewriteAgent.name());
    }

    @Test
    void trace_returnsEventWithPrevQuery() {
        AgentContext ctx = new AgentContext("original question");
        ctx.query = "modified query";

        StepVerifier.create(queryRewriteAgent.trace(ctx))
                .assertNext(event -> {
                    assertEquals("query-rewriter", event.agent());
                    assertEquals("rewrite", event.type());
                    assertEquals("modified query", event.data().get("prevQuery"));
                })
                .verifyComplete();
    }

    @Test
    void run_extractsQueryFromJsonResponse() {
        AgentContext ctx = new AgentContext("What is revenue?");
        ctx.query = "What is revenue?";
        ctx.draftAnswer = "Failed answer";
        ctx.retrieved.add(new ChunkHit(1L, 100L, "Doc", 0, "Content about revenue", 0.9));

        when(ollamaClient.chat(anyString(), anyString()))
                .thenReturn("{\"query\":\"annual revenue growth rate\"}");

        StepVerifier.create(queryRewriteAgent.run(ctx))
                .verifyComplete();

        assertEquals("annual revenue growth rate", ctx.query);
    }

    @Test
    void run_keepsOriginalQueryWhenResponseIsBlank() {
        AgentContext ctx = new AgentContext("original question");
        ctx.query = "original query";
        ctx.draftAnswer = "Failed";
        ctx.retrieved.add(new ChunkHit(1L, 100L, "Doc", 0, "Content", 0.9));

        when(ollamaClient.chat(anyString(), anyString()))
                .thenReturn("{\"query\":\"\"}");

        StepVerifier.create(queryRewriteAgent.run(ctx))
                .verifyComplete();

        assertEquals("original query", ctx.query);
    }

    @Test
    void run_handlesResponseWithoutQueryKey() {
        AgentContext ctx = new AgentContext("question");
        ctx.query = "original";
        ctx.draftAnswer = "Bad answer";
        ctx.retrieved.add(new ChunkHit(1L, 100L, "Doc", 0, "Content", 0.9));

        when(ollamaClient.chat(anyString(), anyString()))
                .thenReturn("Some random response without JSON");

        StepVerifier.create(queryRewriteAgent.run(ctx))
                .verifyComplete();

        // extractQuery returns original when no "query" key found
        assertEquals("Some random response without JSON", ctx.query);
    }

    @Test
    void run_handlesNullFromOllama() {
        AgentContext ctx = new AgentContext("question");
        ctx.query = "original";
        ctx.draftAnswer = "Answer";
        ctx.retrieved.add(new ChunkHit(1L, 100L, "Doc", 0, "Content", 0.9));

        when(ollamaClient.chat(anyString(), anyString()))
                .thenReturn(null);

        StepVerifier.create(queryRewriteAgent.run(ctx))
                .verifyComplete();

        // Should keep original query when null returned
        assertEquals("original", ctx.query);
    }

    @Test
    void run_includesHintsFromRetrievedChunks() {
        AgentContext ctx = new AgentContext("What is the profit?");
        ctx.query = "profit";
        ctx.draftAnswer = "Incomplete answer";
        ctx.retrieved.add(new ChunkHit(1L, 100L, "Annual Report", 0, "Revenue was $100M", 0.9));
        ctx.retrieved.add(new ChunkHit(2L, 100L, "Annual Report", 1, "Expenses were $80M", 0.8));

        when(ollamaClient.chat(anyString(), anyString()))
                .thenReturn("{\"query\":\"net profit calculation revenue minus expenses\"}");

        StepVerifier.create(queryRewriteAgent.run(ctx))
                .verifyComplete();

        verify(ollamaClient).chat(anyString(), contains("Annual Report :: Revenue was $100M"));
    }

    @Test
    void run_limitsHintsToFiveChunks() {
        AgentContext ctx = new AgentContext("Complex question");
        ctx.query = "complex";
        ctx.draftAnswer = "Some answer";

        for (int i = 0; i < 10; i++) {
            ctx.retrieved.add(new ChunkHit(i, 100L, "Doc" + i, i, "Content " + i, 0.9 - i * 0.05));
        }

        when(ollamaClient.chat(anyString(), anyString()))
                .thenReturn("{\"query\":\"rewritten\"}");

        StepVerifier.create(queryRewriteAgent.run(ctx))
                .verifyComplete();

        // Verify only first 5 are used in hints
        verify(ollamaClient).chat(anyString(), argThat(userPrompt -> userPrompt.contains("Doc0") &&
                userPrompt.contains("Doc4") &&
                !userPrompt.contains("Doc5")));
    }

    @Test
    void extractQuery_withValidJson() {
        when(ollamaClient.chat(anyString(), anyString()))
                .thenReturn("{\"query\":\"extracted query\"}");

        AgentContext ctx = new AgentContext("q");
        ctx.draftAnswer = "a";
        ctx.retrieved.add(new ChunkHit(1L, 1L, "t", 0, "c", 0.9));

        StepVerifier.create(queryRewriteAgent.run(ctx))
                .verifyComplete();

        assertEquals("extracted query", ctx.query);
    }

    @Test
    void extractQuery_withExtraJsonFields() {
        when(ollamaClient.chat(anyString(), anyString()))
                .thenReturn("{\"reasoning\":\"...\",\"query\":\"the actual query\",\"confidence\":0.9}");

        AgentContext ctx = new AgentContext("q");
        ctx.draftAnswer = "a";
        ctx.retrieved.add(new ChunkHit(1L, 1L, "t", 0, "c", 0.9));

        StepVerifier.create(queryRewriteAgent.run(ctx))
                .verifyComplete();

        assertEquals("the actual query", ctx.query);
    }
}
