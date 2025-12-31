package com.ai.agenticrag.api.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for IngestTextRequest record.
 */
class IngestTextRequestTest {

    @Test
    void createsWithAllFields() {
        IngestTextRequest request = new IngestTextRequest(
                "test-source",
                "Test Title",
                "This is the text content",
                "logical-123",
                true);

        assertEquals("test-source", request.source());
        assertEquals("Test Title", request.title());
        assertEquals("This is the text content", request.text());
        assertEquals("logical-123", request.logicalId());
        assertTrue(request.upsertBySourceTitle());
    }

    @Test
    void createsWithNullOptionalFields() {
        IngestTextRequest request = new IngestTextRequest(
                "source",
                "title",
                "text",
                null,
                null);

        assertEquals("source", request.source());
        assertEquals("title", request.title());
        assertEquals("text", request.text());
        assertNull(request.logicalId());
        assertNull(request.upsertBySourceTitle());
    }

    @Test
    void upsertBySourceTitle_canBeFalse() {
        IngestTextRequest request = new IngestTextRequest("s", "t", "x", "id", false);

        assertFalse(request.upsertBySourceTitle());
    }

    @Test
    void recordEquality() {
        IngestTextRequest r1 = new IngestTextRequest("s", "t", "x", "id", true);
        IngestTextRequest r2 = new IngestTextRequest("s", "t", "x", "id", true);

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void recordInequality() {
        IngestTextRequest r1 = new IngestTextRequest("s", "t", "x", "id", true);
        IngestTextRequest r2 = new IngestTextRequest("s", "t", "x", "id", false);

        assertNotEquals(r1, r2);
    }

    @Test
    void toString_containsFieldValues() {
        IngestTextRequest request = new IngestTextRequest("src", "ttl", "txt", "lid", true);
        String str = request.toString();

        assertTrue(str.contains("src"));
        assertTrue(str.contains("ttl"));
        assertTrue(str.contains("txt"));
    }
}
