package com.ai.agenticrag.orchestrator.agents;

import com.ai.agenticrag.orchestrator.AgentContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PlannerAgent.
 */
class PlannerAgentTest {

    private PlannerAgent plannerAgent;

    @BeforeEach
    void setUp() {
        plannerAgent = new PlannerAgent();
    }

    @Test
    void name_returnsPlanner() {
        assertEquals("planner", plannerAgent.name());
    }

    @Test
    void trace_returnsEventWithSteps() {
        AgentContext ctx = new AgentContext("What is the revenue?");

        StepVerifier.create(plannerAgent.trace(ctx))
                .assertNext(event -> {
                    assertEquals("planner", event.agent());
                    assertEquals("plan", event.type());
                    assertNotNull(event.data().get("steps"));

                    @SuppressWarnings("unchecked")
                    List<String> steps = (List<String>) event.data().get("steps");
                    assertFalse(steps.isEmpty());
                    assertTrue(steps.contains("route"));
                    assertTrue(steps.contains("synthesize grounded answer"));
                    assertTrue(steps.contains("judge groundedness"));
                })
                .verifyComplete();
    }

    @Test
    void trace_stepsIncludeAllPhases() {
        AgentContext ctx = new AgentContext("question");

        StepVerifier.create(plannerAgent.trace(ctx))
                .assertNext(event -> {
                    @SuppressWarnings("unchecked")
                    List<String> steps = (List<String>) event.data().get("steps");

                    assertEquals(5, steps.size());
                    // Verify order
                    assertEquals("route", steps.get(0));
                    assertEquals("parallel retrieve (multi-agent)", steps.get(1));
                    assertEquals("synthesize grounded answer", steps.get(2));
                    assertEquals("judge groundedness", steps.get(3));
                    assertEquals("rewrite query + retry (max 2)", steps.get(4));
                })
                .verifyComplete();
    }

    @Test
    void run_completesImmediately() {
        AgentContext ctx = new AgentContext("question");

        StepVerifier.create(plannerAgent.run(ctx))
                .verifyComplete();
    }

    @Test
    void run_doesNotModifyContext() {
        AgentContext ctx = new AgentContext("original question");
        String originalQuery = ctx.query;
        boolean originalNeedsRetrieval = ctx.needsRetrieval;

        StepVerifier.create(plannerAgent.run(ctx))
                .verifyComplete();

        assertEquals(originalQuery, ctx.query);
        assertEquals(originalNeedsRetrieval, ctx.needsRetrieval);
        assertNull(ctx.draftAnswer);
    }
}
