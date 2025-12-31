package com.ai.agenticrag.orchestrator.agents;

import com.ai.agenticrag.orchestrator.AgentContext;
import com.ai.agenticrag.rag.ChunkHit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JudgeAgent.
 */
class JudgeAgentTest {

    private JudgeAgent judgeAgent;

    @BeforeEach
    void setUp() {
        judgeAgent = new JudgeAgent();
    }

    @Test
    void name_returnsJudge() {
        assertEquals("judge", judgeAgent.name());
    }

    @Test
    void trace_returnsEventWithDraftChars() {
        AgentContext ctx = new AgentContext("test question");
        ctx.draftAnswer = "This is a draft answer";

        StepVerifier.create(judgeAgent.trace(ctx))
                .assertNext(event -> {
                    assertEquals("judge", event.agent());
                    assertEquals("judge", event.type());
                    assertEquals(22, event.data().get("draftChars"));
                })
                .verifyComplete();
    }

    @Test
    void trace_returnsZeroCharsWhenNullDraft() {
        AgentContext ctx = new AgentContext("test question");
        ctx.draftAnswer = null;

        StepVerifier.create(judgeAgent.trace(ctx))
                .assertNext(event -> {
                    assertEquals(0, event.data().get("draftChars"));
                })
                .verifyComplete();
    }

    @Test
    void run_failsWhenNoRetrieval() {
        AgentContext ctx = new AgentContext("question");
        ctx.draftAnswer = "Some answer [chunk:1]";
        // retrieved is empty

        StepVerifier.create(judgeAgent.run(ctx))
                .verifyComplete();

        assertFalse(ctx.judgedPass);
    }

    @Test
    void run_failsWhenNullRetrieved() {
        AgentContext ctx = new AgentContext("question");
        ctx.draftAnswer = "Answer [chunk:1]";
        ctx.retrieved.clear();

        StepVerifier.create(judgeAgent.run(ctx))
                .verifyComplete();

        assertFalse(ctx.judgedPass);
    }

    @Test
    void run_passesWithSubstantiveAnswerAndRetrieval() {
        AgentContext ctx = new AgentContext("question");
        ctx.draftAnswer = "The answer is based on the retrieved context.";
        ctx.retrieved.add(new ChunkHit(1L, 100L, "Doc1", 0, "Content1", 0.9));
        ctx.retrieved.add(new ChunkHit(2L, 100L, "Doc1", 1, "Content2", 0.85));

        StepVerifier.create(judgeAgent.run(ctx))
                .verifyComplete();

        assertTrue(ctx.judgedPass);
    }

    @Test
    void run_passesWithNaturalLanguageAnswer() {
        AgentContext ctx = new AgentContext("question");
        ctx.draftAnswer = "NVIDIA's revenue grew from $5.66 billion to $39.33 billion, a 595% increase.";
        ctx.retrieved.add(new ChunkHit(1L, 100L, "Doc", 0, "Content1", 0.9));
        ctx.retrieved.add(new ChunkHit(2L, 100L, "Doc", 1, "Content2", 0.85));
        ctx.retrieved.add(new ChunkHit(3L, 100L, "Doc", 2, "Content3", 0.8));

        StepVerifier.create(judgeAgent.run(ctx))
                .verifyComplete();

        assertTrue(ctx.judgedPass);
    }

    @Test
    void run_handlesNullDraftAnswer() {
        AgentContext ctx = new AgentContext("question");
        ctx.draftAnswer = null;
        ctx.retrieved.add(new ChunkHit(1L, 100L, "Doc", 0, "Content", 0.9));

        StepVerifier.create(judgeAgent.run(ctx))
                .verifyComplete();

        assertFalse(ctx.judgedPass); // Null answer fails
    }

    @Test
    void run_handlesEmptyDraftAnswer() {
        AgentContext ctx = new AgentContext("question");
        ctx.draftAnswer = "";
        ctx.retrieved.add(new ChunkHit(1L, 100L, "Doc", 0, "Content", 0.9));

        StepVerifier.create(judgeAgent.run(ctx))
                .verifyComplete();

        assertFalse(ctx.judgedPass);
    }

    @Test
    void run_failsWhenAnswerTooShort() {
        AgentContext ctx = new AgentContext("question");
        ctx.draftAnswer = "Short answer";  // Less than 20 chars
        ctx.retrieved.add(new ChunkHit(1L, 100L, "Doc", 0, "Content", 0.9));

        StepVerifier.create(judgeAgent.run(ctx))
                .verifyComplete();

        assertFalse(ctx.judgedPass);
    }
}
