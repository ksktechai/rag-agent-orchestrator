package com.ai.agenticrag.orchestrator.agents;

import com.ai.agenticrag.orchestrator.AgentContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RouterAgent.
 */
class RouterAgentTest {

    private RouterAgent routerAgent;

    @BeforeEach
    void setUp() {
        routerAgent = new RouterAgent();
    }

    @Test
    void name_returnsRouter() {
        assertEquals("router", routerAgent.name());
    }

    @Test
    void trace_returnsEventWithNeedsRetrieval() {
        AgentContext ctx = new AgentContext("What is the revenue for 2023?");
        ctx.needsRetrieval = true;

        StepVerifier.create(routerAgent.trace(ctx))
                .assertNext(event -> {
                    assertEquals("router", event.agent());
                    assertEquals("route", event.type());
                    assertEquals(true, event.data().get("needsRetrieval"));
                })
                .verifyComplete();
    }

    @Test
    void run_setsNeedsRetrievalFalseForHello() {
        AgentContext ctx = new AgentContext("hello");

        StepVerifier.create(routerAgent.run(ctx))
                .verifyComplete();

        assertFalse(ctx.needsRetrieval);
    }

    @Test
    void run_setsNeedsRetrievalFalseForHi() {
        AgentContext ctx = new AgentContext("hi");

        StepVerifier.create(routerAgent.run(ctx))
                .verifyComplete();

        assertFalse(ctx.needsRetrieval);
    }

    @Test
    void run_setsNeedsRetrievalFalseForWhoAreYou() {
        AgentContext ctx = new AgentContext("who are you");

        StepVerifier.create(routerAgent.run(ctx))
                .verifyComplete();

        assertFalse(ctx.needsRetrieval);
    }

    @Test
    void run_setsNeedsRetrievalFalseForHelloUppercase() {
        AgentContext ctx = new AgentContext("HELLO");

        StepVerifier.create(routerAgent.run(ctx))
                .verifyComplete();

        assertFalse(ctx.needsRetrieval);
    }

    @Test
    void run_setsNeedsRetrievalTrueForDataQuestion() {
        AgentContext ctx = new AgentContext("What was the revenue in 2023?");

        StepVerifier.create(routerAgent.run(ctx))
                .verifyComplete();

        assertTrue(ctx.needsRetrieval);
    }

    @Test
    void run_setsNeedsRetrievalTrueForComplexQuestion() {
        AgentContext ctx = new AgentContext("Compare the profit margins between Q1 and Q4");

        StepVerifier.create(routerAgent.run(ctx))
                .verifyComplete();

        assertTrue(ctx.needsRetrieval);
    }

    @Test
    void run_setsNeedsRetrievalTrueForDocumentQuestion() {
        AgentContext ctx = new AgentContext("According to the document, what are the key findings?");

        StepVerifier.create(routerAgent.run(ctx))
                .verifyComplete();

        assertTrue(ctx.needsRetrieval);
    }

    @Test
    void run_setsNeedsRetrievalTrueForRandomQuestion() {
        AgentContext ctx = new AgentContext("What is machine learning?");

        StepVerifier.create(routerAgent.run(ctx))
                .verifyComplete();

        assertTrue(ctx.needsRetrieval);
    }

    @Test
    void run_handlesEmptyQuestion() {
        AgentContext ctx = new AgentContext("");

        StepVerifier.create(routerAgent.run(ctx))
                .verifyComplete();

        // Empty doesn't match greeting pattern, so needs retrieval
        assertTrue(ctx.needsRetrieval);
    }

    @Test
    void run_caseInsensitiveGreeting() {
        AgentContext ctx = new AgentContext("HeLLo");

        StepVerifier.create(routerAgent.run(ctx))
                .verifyComplete();

        assertFalse(ctx.needsRetrieval);
    }
}
