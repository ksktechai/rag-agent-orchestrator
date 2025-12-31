package com.ai.agenticrag.ingest;

import com.ai.agenticrag.api.dto.IngestProgressEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for IngestionJobService.
 */
class IngestionJobServiceTest {

    private IngestionJobService jobService;

    @BeforeEach
    void setUp() {
        jobService = new IngestionJobService();
    }

    @Test
    void newJob_createsUniqueId() {
        String id1 = jobService.newJob();
        String id2 = jobService.newJob();

        assertNotNull(id1);
        assertNotNull(id2);
        assertNotEquals(id1, id2);
    }

    @Test
    void newJob_returnsValidUUID() {
        String id = jobService.newJob();

        // UUID format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
        assertTrue(id.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"));
    }

    @Test
    void emit_updatesProgressEvent() {
        String jobId = jobService.newJob();
        IngestProgressEvent event = IngestProgressEvent.of(jobId, "parsing", 1, 10, "Parsing file");

        jobService.emit(event);

        // Verify by streaming
        StepVerifier.create(jobService.stream(jobId).take(1))
                .expectNextMatches(e -> e.stage().equals("parsing") && e.current() == 1)
                .verifyComplete();
    }

    @Test
    void emit_doneEventCompletesStream() {
        String jobId = jobService.newJob();

        jobService.emit(IngestProgressEvent.of(jobId, "processing", 1, 2, "Step 1"));
        jobService.emit(IngestProgressEvent.done(jobId, 100L, "lid", 1, "Complete"));

        // After done event, stream should complete
        StepVerifier.create(jobService.stream(jobId))
                .expectNextMatches(e -> e.stage().equals("done"))
                .verifyComplete();
    }

    @Test
    void complete_closesStream() {
        String jobId = jobService.newJob();

        jobService.emit(IngestProgressEvent.of(jobId, "start", 0, 1, "Starting"));
        jobService.complete(jobId);

        // Streaming after complete should only return last event (no sink)
        StepVerifier.create(jobService.stream(jobId).take(1))
                .expectNextMatches(e -> e.stage().equals("start"))
                .verifyComplete();
    }

    @Test
    void stream_returnsEmptyForUnknownJob() {
        StepVerifier.create(jobService.stream("unknown-job-id"))
                .verifyComplete();
    }

    @Test
    void stream_returnsLastEventAndLiveUpdates() {
        String jobId = jobService.newJob();

        // Emit initial event
        jobService.emit(IngestProgressEvent.of(jobId, "init", 0, 1, "Initializing"));

        // Stream should get the last emitted event first
        StepVerifier.create(jobService.stream(jobId).take(1))
                .expectNextMatches(e -> e.stage().equals("init"))
                .verifyComplete();
    }

    @Test
    void stream_receivesMultipleUpdates() {
        String jobId = jobService.newJob();

        // Start streaming
        var stream = jobService.stream(jobId)
                .take(3)
                .timeout(Duration.ofSeconds(1));

        // Emit events
        jobService.emit(IngestProgressEvent.of(jobId, "step1", 1, 3, "Step 1"));
        jobService.emit(IngestProgressEvent.of(jobId, "step2", 2, 3, "Step 2"));
        jobService.emit(IngestProgressEvent.of(jobId, "step3", 3, 3, "Step 3"));

        StepVerifier.create(stream)
                .expectNextCount(3)
                .verifyComplete();
    }

    @Test
    void emit_errorEventCompletesStream() {
        String jobId = jobService.newJob();

        jobService.emit(IngestProgressEvent.error(jobId, "Failed", "IOException"));

        StepVerifier.create(jobService.stream(jobId))
                .expectNextMatches(e -> e.error() != null && e.done())
                .verifyComplete();
    }

    @Test
    void complete_idempotent() {
        String jobId = jobService.newJob();

        // Complete multiple times should not throw
        assertDoesNotThrow(() -> {
            jobService.complete(jobId);
            jobService.complete(jobId);
        });
    }

    @Test
    void complete_unknownJobDoesNotThrow() {
        assertDoesNotThrow(() -> jobService.complete("unknown-job"));
    }

    @Test
    void emit_toUnknownJobDoesNotThrow() {
        // Emitting to a job that doesn't exist should not throw
        IngestProgressEvent event = IngestProgressEvent.of("unknown", "stage", 1, 1, "msg");
        assertDoesNotThrow(() -> jobService.emit(event));
    }
}
