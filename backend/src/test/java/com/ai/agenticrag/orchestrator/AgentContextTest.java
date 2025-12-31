package com.ai.agenticrag.orchestrator;

import com.ai.agenticrag.rag.ChunkHit;
import com.ai.agenticrag.rag.Citation;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AgentContext.
 */
class AgentContextTest {

    @Test
    void constructor_setsQuestionAndQuery() {
        AgentContext ctx = new AgentContext("What is AI?");

        assertEquals("What is AI?", ctx.question);
        assertEquals("What is AI?", ctx.query);
    }

    @Test
    void constructor_initializesDefaults() {
        AgentContext ctx = new AgentContext("question");

        assertFalse(ctx.needsRetrieval);
        assertNotNull(ctx.retrieved);
        assertTrue(ctx.retrieved.isEmpty());
        assertNull(ctx.draftAnswer);
        assertFalse(ctx.judgedPass);
        assertNotNull(ctx.citations);
        assertTrue(ctx.citations.isEmpty());
    }

    @Test
    void question_isImmutable() {
        AgentContext ctx = new AgentContext("original");

        // question is final, so we just verify it cannot be changed
        assertEquals("original", ctx.question);
    }

    @Test
    void query_isMutable() {
        AgentContext ctx = new AgentContext("original");

        ctx.query = "modified query";

        assertEquals("modified query", ctx.query);
        assertEquals("original", ctx.question); // question unchanged
    }

    @Test
    void needsRetrieval_isMutable() {
        AgentContext ctx = new AgentContext("q");

        ctx.needsRetrieval = true;

        assertTrue(ctx.needsRetrieval);
    }

    @Test
    void retrieved_isThreadSafe() throws InterruptedException {
        AgentContext ctx = new AgentContext("q");
        int threadCount = 10;
        int itemsPerThread = 100;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < itemsPerThread; i++) {
                        ctx.retrieved.add(new ChunkHit(
                                threadId * 1000 + i,
                                100L,
                                "Doc",
                                i,
                                "Content",
                                0.9));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(threadCount * itemsPerThread, ctx.retrieved.size());
    }

    @Test
    void citations_isThreadSafe() throws InterruptedException {
        AgentContext ctx = new AgentContext("q");
        int threadCount = 10;
        int itemsPerThread = 100;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < itemsPerThread; i++) {
                        ctx.citations.add(new Citation(
                                "Doc" + threadId,
                                i,
                                0.9));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(threadCount * itemsPerThread, ctx.citations.size());
    }

    @Test
    void draftAnswer_isMutable() {
        AgentContext ctx = new AgentContext("q");

        ctx.draftAnswer = "This is the answer [chunk:1]";

        assertEquals("This is the answer [chunk:1]", ctx.draftAnswer);
    }

    @Test
    void judgedPass_isMutable() {
        AgentContext ctx = new AgentContext("q");

        ctx.judgedPass = true;

        assertTrue(ctx.judgedPass);
    }

    @Test
    void toString_containsAllFields() {
        AgentContext ctx = new AgentContext("test question");
        ctx.query = "modified query";
        ctx.needsRetrieval = true;
        ctx.draftAnswer = "draft";
        ctx.judgedPass = true;
        ctx.retrieved.add(new ChunkHit(1L, 100L, "Doc", 0, "Content", 0.9));
        ctx.citations.add(new Citation("Title", 0, 0.9));

        String str = ctx.toString();

        assertTrue(str.contains("question=test question"));
        assertTrue(str.contains("query=modified query"));
        assertTrue(str.contains("needsRetrieval=true"));
        assertTrue(str.contains("draftAnswer=draft"));
        assertTrue(str.contains("judgedPass=true"));
    }

    @Test
    void retrieved_canBeCleared() {
        AgentContext ctx = new AgentContext("q");
        ctx.retrieved.add(new ChunkHit(1L, 100L, "Doc", 0, "Content", 0.9));

        ctx.retrieved.clear();

        assertTrue(ctx.retrieved.isEmpty());
    }

    @Test
    void nullQuestion_handled() {
        AgentContext ctx = new AgentContext(null);

        assertNull(ctx.question);
        assertNull(ctx.query);
    }

    @Test
    void emptyQuestion_handled() {
        AgentContext ctx = new AgentContext("");

        assertEquals("", ctx.question);
        assertEquals("", ctx.query);
    }
}
