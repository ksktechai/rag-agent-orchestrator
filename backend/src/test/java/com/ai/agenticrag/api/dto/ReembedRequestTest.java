package com.ai.agenticrag.api.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ReembedRequest record.
 */
class ReembedRequestTest {

    @Test
    void createsWithScopeAndModel() {
        ReembedRequest request = new ReembedRequest("latest", "nomic-embed-text");

        assertEquals("latest", request.scope());
        assertEquals("nomic-embed-text", request.model());
    }

    @Test
    void createsWithNullValues() {
        ReembedRequest request = new ReembedRequest(null, null);

        assertNull(request.scope());
        assertNull(request.model());
    }

    @Test
    void recordEquality() {
        ReembedRequest r1 = new ReembedRequest("all", "model1");
        ReembedRequest r2 = new ReembedRequest("all", "model1");

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void recordInequality_differentScope() {
        ReembedRequest r1 = new ReembedRequest("scope1", "model");
        ReembedRequest r2 = new ReembedRequest("scope2", "model");

        assertNotEquals(r1, r2);
    }

    @Test
    void recordInequality_differentModel() {
        ReembedRequest r1 = new ReembedRequest("scope", "model1");
        ReembedRequest r2 = new ReembedRequest("scope", "model2");

        assertNotEquals(r1, r2);
    }

    @Test
    void toString_containsFieldValues() {
        ReembedRequest request = new ReembedRequest("myScope", "myModel");
        String str = request.toString();

        assertTrue(str.contains("myScope"));
        assertTrue(str.contains("myModel"));
    }
}
