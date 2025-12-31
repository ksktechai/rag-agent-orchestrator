package com.ai.agenticrag.orchestrator.agents;

import com.ai.agenticrag.orchestrator.AgentContext;
import com.ai.agenticrag.rag.ChunkHit;
import com.ai.agenticrag.rag.VectorStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RetrieverAgent.
 */
@ExtendWith(MockitoExtension.class)
class RetrieverAgentTest {

    @Mock
    private VectorStoreService vectorStoreService;

    private RetrieverAgent retrieverAgent;

    @BeforeEach
    void setUp() {
        retrieverAgent = new RetrieverAgent("test-1", vectorStoreService, 5);
    }

    @Test
    void name_returnsRetrieverWithId() {
        assertEquals("retriever-test-1", retrieverAgent.name());
    }

    @Test
    void name_withDifferentId() {
        RetrieverAgent agent = new RetrieverAgent("custom-id", vectorStoreService, 10);
        assertEquals("retriever-custom-id", agent.name());
    }

    @Test
    void trace_returnsEventWithQueryAndK() {
        AgentContext ctx = new AgentContext("What is AI?");
        ctx.query = "artificial intelligence definition";

        StepVerifier.create(retrieverAgent.trace(ctx))
                .assertNext(event -> {
                    assertEquals("retriever-test-1", event.agent());
                    assertEquals("retrieve", event.type());
                    assertEquals(5, event.data().get("k"));
                    assertEquals("artificial intelligence definition", event.data().get("query"));
                })
                .verifyComplete();
    }

    @Test
    void run_addsChunksToContext() {
        AgentContext ctx = new AgentContext("query");
        ctx.query = "search query";

        List<ChunkHit> mockHits = List.of(
                new ChunkHit(1L, 100L, "Doc1", 0, "Content 1", 0.95),
                new ChunkHit(2L, 100L, "Doc1", 1, "Content 2", 0.90));

        when(vectorStoreService.search("search query", 5))
                .thenReturn(Mono.just(mockHits));

        StepVerifier.create(retrieverAgent.run(ctx))
                .verifyComplete();

        assertEquals(2, ctx.retrieved.size());
        assertEquals("Content 1", ctx.retrieved.get(0).content());
    }

    @Test
    void run_usesQuestionWhenQueryIsBlank() {
        AgentContext ctx = new AgentContext("What is the revenue?");
        ctx.query = "";

        when(vectorStoreService.search("What is the revenue?", 5))
                .thenReturn(Mono.just(List.of()));

        StepVerifier.create(retrieverAgent.run(ctx))
                .verifyComplete();

        verify(vectorStoreService).search("What is the revenue?", 5);
    }

    @Test
    void run_usesQuestionWhenQueryIsNull() {
        AgentContext ctx = new AgentContext("Original question");
        ctx.query = null;

        when(vectorStoreService.search("Original question", 5))
                .thenReturn(Mono.just(List.of()));

        StepVerifier.create(retrieverAgent.run(ctx))
                .verifyComplete();

        verify(vectorStoreService).search("Original question", 5);
    }

    @Test
    void run_appendsToExistingRetrieved() {
        AgentContext ctx = new AgentContext("query");
        ctx.query = "query";
        ctx.retrieved.add(new ChunkHit(0L, 50L, "PreExisting", 0, "Old content", 0.5));

        List<ChunkHit> newHits = List.of(
                new ChunkHit(1L, 100L, "New", 0, "New content", 0.9));

        when(vectorStoreService.search("query", 5))
                .thenReturn(Mono.just(newHits));

        StepVerifier.create(retrieverAgent.run(ctx))
                .verifyComplete();

        assertEquals(2, ctx.retrieved.size());
        assertEquals("Old content", ctx.retrieved.get(0).content());
        assertEquals("New content", ctx.retrieved.get(1).content());
    }

    @Test
    void run_handlesEmptySearchResults() {
        AgentContext ctx = new AgentContext("obscure query");
        ctx.query = "obscure query";

        when(vectorStoreService.search("obscure query", 5))
                .thenReturn(Mono.just(List.of()));

        StepVerifier.create(retrieverAgent.run(ctx))
                .verifyComplete();

        assertTrue(ctx.retrieved.isEmpty());
    }

    @Test
    void run_usesConfiguredK() {
        RetrieverAgent agent = new RetrieverAgent("k10", vectorStoreService, 10);
        AgentContext ctx = new AgentContext("query");
        ctx.query = "query";

        when(vectorStoreService.search("query", 10))
                .thenReturn(Mono.just(List.of()));

        StepVerifier.create(agent.run(ctx))
                .verifyComplete();

        verify(vectorStoreService).search("query", 10);
    }
}
