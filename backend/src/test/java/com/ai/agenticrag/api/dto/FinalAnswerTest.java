package com.ai.agenticrag.api.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FinalAnswer record and nested Citation record.
 */
class FinalAnswerTest {

    @Test
    void finalAnswer_createsWithAnswerAndCitations() {
        List<FinalAnswer.Citation> citations = List.of(
                new FinalAnswer.Citation(1L, "Doc1", 0, 0.95),
                new FinalAnswer.Citation(2L, "Doc2", 1, 0.85));
        FinalAnswer answer = new FinalAnswer("This is the answer", citations);

        assertEquals("This is the answer", answer.answer());
        assertEquals(2, answer.citations().size());
    }

    @Test
    void finalAnswer_createsWithEmptyCitations() {
        FinalAnswer answer = new FinalAnswer("Answer without citations", List.of());

        assertEquals("Answer without citations", answer.answer());
        assertTrue(answer.citations().isEmpty());
    }

    @Test
    void finalAnswer_createsWithNullAnswer() {
        FinalAnswer answer = new FinalAnswer(null, List.of());

        assertNull(answer.answer());
    }

    @Test
    void citation_createsWithAllFields() {
        FinalAnswer.Citation citation = new FinalAnswer.Citation(123L, "Test Document", 5, 0.789);

        assertEquals(123L, citation.chunkId());
        assertEquals("Test Document", citation.title());
        assertEquals(5, citation.chunkIndex());
        assertEquals(0.789, citation.score(), 0.001);
    }

    @Test
    void citation_equality() {
        FinalAnswer.Citation c1 = new FinalAnswer.Citation(1L, "Title", 0, 0.9);
        FinalAnswer.Citation c2 = new FinalAnswer.Citation(1L, "Title", 0, 0.9);

        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
    }

    @Test
    void finalAnswer_equality() {
        List<FinalAnswer.Citation> citations = List.of(new FinalAnswer.Citation(1L, "Doc", 0, 0.9));
        FinalAnswer a1 = new FinalAnswer("Answer", citations);
        FinalAnswer a2 = new FinalAnswer("Answer", citations);

        assertEquals(a1, a2);
        assertEquals(a1.hashCode(), a2.hashCode());
    }

    @Test
    void toString_containsRelevantInfo() {
        FinalAnswer.Citation citation = new FinalAnswer.Citation(1L, "Title", 0, 0.9);
        FinalAnswer answer = new FinalAnswer("Test answer", List.of(citation));

        assertTrue(answer.toString().contains("Test answer"));
        assertTrue(citation.toString().contains("Title"));
    }
}
