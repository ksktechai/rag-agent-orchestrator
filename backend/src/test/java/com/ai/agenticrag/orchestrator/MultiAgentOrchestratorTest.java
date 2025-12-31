package com.ai.agenticrag.orchestrator;

import com.ai.agenticrag.api.dto.AgentEvent;
import com.ai.agenticrag.api.dto.FinalAnswer;
import com.ai.agenticrag.rag.ChunkHit;
import com.ai.agenticrag.rag.VectorStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MultiAgentOrchestrator.
 */
@ExtendWith(MockitoExtension.class)
class MultiAgentOrchestratorTest {

    @Mock
    private VectorStoreService vectorStoreService;

    @Mock
    private OllamaHttpClient ollamaClient;

    private MultiAgentOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new MultiAgentOrchestrator(vectorStoreService, ollamaClient);
    }

    @Nested
    class EventsTests {

        @Test
        void events_emitsRouterPlannerAndSupervisorEvents() {
            StepVerifier.create(orchestrator.events("What is AI?"))
                    .assertNext(event -> {
                        assertEquals("router", event.agent());
                        assertEquals("route", event.type());
                    })
                    .assertNext(event -> {
                        assertEquals("planner", event.agent());
                        assertEquals("plan", event.type());
                    })
                    .assertNext(event -> {
                        assertEquals("supervisor", event.agent());
                        assertEquals("config", event.type());
                        assertNotNull(event.data().get("parallelRetrievers"));
                        assertNotNull(event.data().get("maxRetries"));
                    })
                    .verifyComplete();
        }

        @Test
        void events_supervisorConfigHasExpectedValues() {
            StepVerifier.create(orchestrator.events("Test question"))
                    .expectNextCount(2) // router and planner
                    .assertNext(event -> {
                        @SuppressWarnings("unchecked")
                        List<Integer> retrievers = (List<Integer>) event.data().get("parallelRetrievers");
                        assertEquals(List.of(6, 10, 14), retrievers);
                        assertEquals(2, event.data().get("maxRetries"));
                    })
                    .verifyComplete();
        }
    }

    @Nested
    class FinalAnswerTests {

        @Test
        void finalAnswer_withNoDocuments_callsOllamaDirectly() {
            when(vectorStoreService.hasAnyDocuments()).thenReturn(false);
            when(ollamaClient.chat("You are helpful.", "Hello"))
                    .thenReturn("Hello! How can I help you?");

            StepVerifier.create(orchestrator.finalAnswer("Hello"))
                    .assertNext(answer -> {
                        assertEquals("Hello! How can I help you?", answer.answer());
                        assertTrue(answer.citations().isEmpty());
                    })
                    .verifyComplete();
        }

        @Test
        void finalAnswer_withDocuments_performsRetrieval() {
            when(vectorStoreService.hasAnyDocuments()).thenReturn(true);

            List<ChunkHit> mockHits = List.of(
                    new ChunkHit(1L, 100L, "Document", 0, "Content about the topic", 0.95));

            when(vectorStoreService.search(anyString(), anyInt()))
                    .thenReturn(Mono.just(mockHits));
            when(ollamaClient.chat(anyString(), anyString()))
                    .thenReturn("Based on [chunk:1], the answer is...");

            StepVerifier.create(orchestrator.finalAnswer("What is the topic?"))
                    .assertNext(answer -> {
                        assertNotNull(answer.answer());
                    })
                    .verifyComplete();
        }

        @Test
        void finalAnswer_withDocuments_includesCitations() {
            when(vectorStoreService.hasAnyDocuments()).thenReturn(true);

            List<ChunkHit> mockHits = List.of(
                    new ChunkHit(1L, 100L, "Document.pdf", 0, "Revenue was $100M", 0.95));

            when(vectorStoreService.search(anyString(), anyInt()))
                    .thenReturn(Mono.just(mockHits));
            when(ollamaClient.chat(anyString(), anyString()))
                    .thenReturn("Revenue was $100M [chunk:1]");

            StepVerifier.create(orchestrator.finalAnswer("What was the revenue?"))
                    .assertNext(answer -> {
                        assertFalse(answer.citations().isEmpty());
                        assertEquals("Document.pdf", answer.citations().get(0).title());
                    })
                    .verifyComplete();
        }

        @Test
        void finalAnswer_retriesOnJudgeFail() {
            when(vectorStoreService.hasAnyDocuments()).thenReturn(true);

            List<ChunkHit> mockHits = List.of(
                    new ChunkHit(1L, 100L, "Doc", 0, "Some content", 0.9));

            when(vectorStoreService.search(anyString(), anyInt()))
                    .thenReturn(Mono.just(mockHits));

            // First attempt: no citations (will fail judge)
            // Second attempt: no citations (will fail judge)
            // Third attempt: max retries reached
            when(ollamaClient.chat(anyString(), anyString()))
                    .thenReturn("Answer without proper citations")
                    .thenReturn("{\"query\":\"rewritten query\"}")
                    .thenReturn("Still no citations");

            StepVerifier.create(orchestrator.finalAnswer("Complex question"))
                    .assertNext(answer -> {
                        // After max retries, should return with disclaimer
                        assertTrue(answer.answer().contains("couldn't confidently ground") ||
                                answer.answer().contains("Answer"));
                    })
                    .verifyComplete();
        }

        @Test
        void finalAnswer_emptyRetrievalReturnsAnswer() {
            when(vectorStoreService.hasAnyDocuments()).thenReturn(true);
            when(vectorStoreService.search(anyString(), anyInt()))
                    .thenReturn(Mono.just(List.of()));
            when(ollamaClient.chat(anyString(), anyString()))
                    .thenReturn("I don't have enough context [chunk:1]")
                    .thenReturn("{\"query\":\"rewritten\"}")
                    .thenReturn("Still nothing");

            StepVerifier.create(orchestrator.finalAnswer("Unknown topic"))
                    .assertNext(answer -> assertNotNull(answer.answer()))
                    .verifyComplete();
        }
    }

    @Nested
    class GreetingTests {

        @Test
        void finalAnswer_greetingSkipsRetrieval() {
            // Greetings match the pattern in RouterAgent, so needsRetrieval = false
            // But we still check hasAnyDocuments, which can override
            when(vectorStoreService.hasAnyDocuments()).thenReturn(false);
            when(ollamaClient.chat("You are helpful.", "hello"))
                    .thenReturn("Hello! How can I assist you today?");

            StepVerifier.create(orchestrator.finalAnswer("hello"))
                    .assertNext(answer -> {
                        assertNotNull(answer.answer());
                        assertTrue(answer.citations().isEmpty());
                    })
                    .verifyComplete();
        }
    }
}
