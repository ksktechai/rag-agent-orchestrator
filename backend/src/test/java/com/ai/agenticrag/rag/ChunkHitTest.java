package com.ai.agenticrag.rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ChunkHit record.
 */
class ChunkHitTest {

    @Test
    void createsWithAllFields() {
        ChunkHit hit = new ChunkHit(1L, 100L, "Test Document", 5, "This is the chunk content", 0.95);

        assertEquals(1L, hit.chunkId());
        assertEquals(100L, hit.documentId());
        assertEquals("Test Document", hit.title());
        assertEquals(5, hit.chunkIndex());
        assertEquals("This is the chunk content", hit.content());
        assertEquals(0.95, hit.score(), 0.001);
    }

    @Test
    void createsWithZeroScore() {
        ChunkHit hit = new ChunkHit(1L, 1L, "Title", 0, "Content", 0.0);

        assertEquals(0.0, hit.score(), 0.001);
    }

    @Test
    void createsWithNegativeScore() {
        ChunkHit hit = new ChunkHit(1L, 1L, "Title", 0, "Content", -0.5);

        assertEquals(-0.5, hit.score(), 0.001);
    }

    @Test
    void createsWithNullContent() {
        ChunkHit hit = new ChunkHit(1L, 1L, null, 0, null, 0.5);

        assertNull(hit.title());
        assertNull(hit.content());
    }

    @Test
    void recordEquality() {
        ChunkHit h1 = new ChunkHit(1L, 2L, "Title", 0, "Content", 0.9);
        ChunkHit h2 = new ChunkHit(1L, 2L, "Title", 0, "Content", 0.9);

        assertEquals(h1, h2);
        assertEquals(h1.hashCode(), h2.hashCode());
    }

    @Test
    void recordInequality_differentChunkId() {
        ChunkHit h1 = new ChunkHit(1L, 2L, "Title", 0, "Content", 0.9);
        ChunkHit h2 = new ChunkHit(2L, 2L, "Title", 0, "Content", 0.9);

        assertNotEquals(h1, h2);
    }

    @Test
    void recordInequality_differentScore() {
        ChunkHit h1 = new ChunkHit(1L, 2L, "Title", 0, "Content", 0.9);
        ChunkHit h2 = new ChunkHit(1L, 2L, "Title", 0, "Content", 0.8);

        assertNotEquals(h1, h2);
    }

    @Test
    void toString_containsRelevantInfo() {
        ChunkHit hit = new ChunkHit(123L, 456L, "MyDoc", 7, "MyContent", 0.85);
        String str = hit.toString();

        assertTrue(str.contains("123"));
        assertTrue(str.contains("456"));
        assertTrue(str.contains("MyDoc"));
    }
}
