package com.ai.agenticrag.api.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for IngestProgressEvent record.
 */
class IngestProgressEventTest {

    @Test
    void of_createsProgressEvent() {
        Instant before = Instant.now();
        IngestProgressEvent event = IngestProgressEvent.of("job-123", "parsing", 5, 10, "Parsing file");
        Instant after = Instant.now();

        assertEquals("job-123", event.jobId());
        assertEquals("parsing", event.stage());
        assertEquals(5, event.current());
        assertEquals(10, event.total());
        assertEquals("Parsing file", event.message());
        assertNull(event.documentId());
        assertNull(event.logicalId());
        assertNull(event.version());
        assertFalse(event.done());
        assertNull(event.error());
        assertNotNull(event.ts());
        assertFalse(event.ts().isBefore(before));
        assertFalse(event.ts().isAfter(after));
    }

    @Test
    void done_createsDoneEvent() {
        Instant before = Instant.now();
        IngestProgressEvent event = IngestProgressEvent.done("job-456", 100L, "logical-abc", 3, "Ingestion complete");
        Instant after = Instant.now();

        assertEquals("job-456", event.jobId());
        assertEquals("done", event.stage());
        assertEquals(1, event.current());
        assertEquals(1, event.total());
        assertEquals("Ingestion complete", event.message());
        assertEquals(100L, event.documentId());
        assertEquals("logical-abc", event.logicalId());
        assertEquals(3, event.version());
        assertTrue(event.done());
        assertNull(event.error());
        assertFalse(event.ts().isBefore(before));
        assertFalse(event.ts().isAfter(after));
    }

    @Test
    void error_createsErrorEvent() {
        Instant before = Instant.now();
        IngestProgressEvent event = IngestProgressEvent.error("job-789", "Failed to parse",
                "IOException: File not found");
        Instant after = Instant.now();

        assertEquals("job-789", event.jobId());
        assertEquals("error", event.stage());
        assertEquals(0, event.current());
        assertEquals(0, event.total());
        assertEquals("Failed to parse", event.message());
        assertNull(event.documentId());
        assertNull(event.logicalId());
        assertNull(event.version());
        assertTrue(event.done());
        assertEquals("IOException: File not found", event.error());
        assertFalse(event.ts().isBefore(before));
        assertFalse(event.ts().isAfter(after));
    }

    @Test
    void recordEquality() {
        Instant ts = Instant.now();
        IngestProgressEvent e1 = new IngestProgressEvent("job", "stage", 1, 2, "msg", 1L, "lid", 1, false, null, ts);
        IngestProgressEvent e2 = new IngestProgressEvent("job", "stage", 1, 2, "msg", 1L, "lid", 1, false, null, ts);

        assertEquals(e1, e2);
        assertEquals(e1.hashCode(), e2.hashCode());
    }

    @Test
    void toString_containsRelevantInfo() {
        IngestProgressEvent event = IngestProgressEvent.of("jobId", "stage", 1, 2, "message");
        String str = event.toString();

        assertTrue(str.contains("jobId"));
        assertTrue(str.contains("stage"));
        assertTrue(str.contains("message"));
    }
}
