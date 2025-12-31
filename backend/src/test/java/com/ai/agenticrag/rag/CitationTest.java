package com.ai.agenticrag.rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Citation record.
 */
class CitationTest {

    @Test
    void createsWithAllFields() {
        Citation citation = new Citation("Document Title", 5, 0.95);

        assertEquals("Document Title", citation.title());
        assertEquals(5, citation.chunkIndex());
        assertEquals(0.95, citation.score(), 0.001);
    }

    @Test
    void createsWithZeroIndex() {
        Citation citation = new Citation("Title", 0, 0.5);

        assertEquals(0, citation.chunkIndex());
    }

    @Test
    void createsWithNullTitle() {
        Citation citation = new Citation(null, 0, 0.5);

        assertNull(citation.title());
    }

    @Test
    void createsWithZeroScore() {
        Citation citation = new Citation("Title", 0, 0.0);

        assertEquals(0.0, citation.score(), 0.001);
    }

    @Test
    void createsWithPerfectScore() {
        Citation citation = new Citation("Title", 0, 1.0);

        assertEquals(1.0, citation.score(), 0.001);
    }

    @Test
    void recordEquality() {
        Citation c1 = new Citation("Title", 1, 0.9);
        Citation c2 = new Citation("Title", 1, 0.9);

        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
    }

    @Test
    void recordInequality_differentTitle() {
        Citation c1 = new Citation("Title1", 1, 0.9);
        Citation c2 = new Citation("Title2", 1, 0.9);

        assertNotEquals(c1, c2);
    }

    @Test
    void recordInequality_differentChunkIndex() {
        Citation c1 = new Citation("Title", 1, 0.9);
        Citation c2 = new Citation("Title", 2, 0.9);

        assertNotEquals(c1, c2);
    }

    @Test
    void recordInequality_differentScore() {
        Citation c1 = new Citation("Title", 1, 0.9);
        Citation c2 = new Citation("Title", 1, 0.8);

        assertNotEquals(c1, c2);
    }

    @Test
    void toString_containsRelevantInfo() {
        Citation citation = new Citation("MyDocument", 3, 0.75);
        String str = citation.toString();

        assertTrue(str.contains("MyDocument"));
        assertTrue(str.contains("3"));
    }
}
