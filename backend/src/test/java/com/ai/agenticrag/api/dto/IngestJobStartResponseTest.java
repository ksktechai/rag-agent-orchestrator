package com.ai.agenticrag.api.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for IngestJobStartResponse record.
 */
class IngestJobStartResponseTest {

    @Test
    void createsWithJobId() {
        IngestJobStartResponse response = new IngestJobStartResponse("job-12345");

        assertEquals("job-12345", response.jobId());
    }

    @Test
    void createsWithNullJobId() {
        IngestJobStartResponse response = new IngestJobStartResponse(null);

        assertNull(response.jobId());
    }

    @Test
    void recordEquality() {
        IngestJobStartResponse r1 = new IngestJobStartResponse("job-1");
        IngestJobStartResponse r2 = new IngestJobStartResponse("job-1");

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void recordInequality() {
        IngestJobStartResponse r1 = new IngestJobStartResponse("job-1");
        IngestJobStartResponse r2 = new IngestJobStartResponse("job-2");

        assertNotEquals(r1, r2);
    }

    @Test
    void toString_containsJobId() {
        IngestJobStartResponse response = new IngestJobStartResponse("my-job-id");
        String str = response.toString();

        assertTrue(str.contains("my-job-id"));
    }
}
