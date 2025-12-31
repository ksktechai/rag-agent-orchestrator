package com.ai.agenticrag.api;

import com.ai.agenticrag.api.dto.AgentEvent;
import com.ai.agenticrag.api.dto.FinalAnswer;
import com.ai.agenticrag.orchestrator.MultiAgentOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ChatSseController.
 */
@ExtendWith(MockitoExtension.class)
class ChatSseControllerTest {

    @Mock
    private MultiAgentOrchestrator orchestrator;

    private ChatSseController controller;

    @BeforeEach
    void setUp() {
        controller = new ChatSseController(orchestrator);
    }

    @Test
    void stream_returnsEventsAndFinalAnswer() {
        String question = "What is AI?";
        AgentEvent event = AgentEvent.of("router", "route", Map.of("needsRetrieval", true));
        FinalAnswer finalAnswer = new FinalAnswer("AI is artificial intelligence", List.of());

        when(orchestrator.events(question)).thenReturn(Flux.just(event));
        when(orchestrator.finalAnswer(question)).thenReturn(Mono.just(finalAnswer));

        StepVerifier.create(controller.stream(question))
                .assertNext(sse -> {
                    assertNotNull(sse.data());
                    assertEquals("agent", sse.event());
                })
                .assertNext(sse -> {
                    assertNotNull(sse.data());
                    assertEquals("final", sse.event());
                })
                .verifyComplete();
    }

    @Test
    void stream_handlesMultipleEvents() {
        String question = "Question";
        AgentEvent event1 = AgentEvent.of("agent1", "type1", Map.of());
        AgentEvent event2 = AgentEvent.of("agent2", "type2", Map.of());
        FinalAnswer finalAnswer = new FinalAnswer("Answer", List.of());

        when(orchestrator.events(question)).thenReturn(Flux.just(event1, event2));
        when(orchestrator.finalAnswer(question)).thenReturn(Mono.just(finalAnswer));

        StepVerifier.create(controller.stream(question))
                .expectNextCount(3) // 2 events + 1 final
                .verifyComplete();
    }

    @Test
    void stream_handlesEmptyEvents() {
        String question = "Simple question";
        FinalAnswer finalAnswer = new FinalAnswer("Direct answer", List.of());

        when(orchestrator.events(question)).thenReturn(Flux.empty());
        when(orchestrator.finalAnswer(question)).thenReturn(Mono.just(finalAnswer));

        StepVerifier.create(controller.stream(question))
                .assertNext(sse -> {
                    assertEquals("final", sse.event());
                    assertTrue(sse.data() instanceof FinalAnswer);
                })
                .verifyComplete();
    }

    @Test
    void stream_sendsCorrectEventTypes() {
        String question = "Test question";
        AgentEvent event = AgentEvent.of("planner", "plan", Map.of("steps", List.of("step1")));
        FinalAnswer finalAnswer = new FinalAnswer("Result", List.of());

        when(orchestrator.events(question)).thenReturn(Flux.just(event));
        when(orchestrator.finalAnswer(question)).thenReturn(Mono.just(finalAnswer));

        Flux<ServerSentEvent<Object>> result = controller.stream(question);

        StepVerifier.create(result)
                .assertNext(sse -> assertEquals("agent", sse.event()))
                .assertNext(sse -> assertEquals("final", sse.event()))
                .verifyComplete();
    }

    @Test
    void stream_includesFinalAnswerWithCitations() {
        String question = "What is the revenue?";
        FinalAnswer finalAnswer = new FinalAnswer(
                "Revenue was $100M [chunk:1]",
                List.of(new FinalAnswer.Citation(1L, "Report", 0, 0.95)));

        when(orchestrator.events(question)).thenReturn(Flux.empty());
        when(orchestrator.finalAnswer(question)).thenReturn(Mono.just(finalAnswer));

        StepVerifier.create(controller.stream(question))
                .assertNext(sse -> {
                    FinalAnswer answer = (FinalAnswer) sse.data();
                    assertEquals("Revenue was $100M [chunk:1]", answer.answer());
                    assertEquals(1, answer.citations().size());
                })
                .verifyComplete();
    }

    @Test
    void stream_handlesQuestionWithSpecialCharacters() {
        String question = "What is 2+2? How about \"quotes\"?";

        when(orchestrator.events(question)).thenReturn(Flux.empty());
        when(orchestrator.finalAnswer(question)).thenReturn(Mono.just(new FinalAnswer("4", List.of())));

        StepVerifier.create(controller.stream(question))
                .expectNextCount(1)
                .verifyComplete();

        verify(orchestrator).events(question);
        verify(orchestrator).finalAnswer(question);
    }
}
