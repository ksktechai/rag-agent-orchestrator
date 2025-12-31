package com.ai.agenticrag.api;

import com.ai.agenticrag.api.dto.IngestProgressEvent;
import com.ai.agenticrag.api.dto.IngestTextRequest;
import com.ai.agenticrag.api.dto.ReembedRequest;
import com.ai.agenticrag.ingest.IngestService;
import com.ai.agenticrag.ingest.IngestionJobService;
import com.ai.agenticrag.rag.VectorStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for IngestController.
 */
@ExtendWith(MockitoExtension.class)
class IngestControllerTest {

    @Mock
    private IngestService ingestService;

    @Mock
    private IngestionJobService jobs;

    private IngestController controller;

    @BeforeEach
    void setUp() {
        controller = new IngestController(ingestService, jobs);
    }

    @Nested
    class IngestTextTests {

        @Test
        void ingestText_returnsMetadata() {
            IngestTextRequest request = new IngestTextRequest(
                    "test-source", "Test Title", "Text content", "logical-123", true);
            VectorStoreService.IngestResult result = new VectorStoreService.IngestResult(1L, "logical-123", 1);

            when(ingestService.ingestText("test-source", "Test Title", "Text content", "logical-123", true, null))
                    .thenReturn(result);

            var response = controller.ingestText(request);

            assertEquals(1L, response.get("documentId"));
            assertEquals("logical-123", response.get("logicalId"));
            assertEquals(1, response.get("version"));
        }

        @Test
        void ingestText_defaultsUpsertToTrue() {
            IngestTextRequest request = new IngestTextRequest(
                    "source", "title", "text", null, null);
            VectorStoreService.IngestResult result = new VectorStoreService.IngestResult(1L, "lid", 1);

            when(ingestService.ingestText("source", "title", "text", null, true, null))
                    .thenReturn(result);

            controller.ingestText(request);

            verify(ingestService).ingestText("source", "title", "text", null, true, null);
        }

        @Test
        void ingestText_respectsUpsertFalse() {
            IngestTextRequest request = new IngestTextRequest(
                    "source", "title", "text", null, false);
            VectorStoreService.IngestResult result = new VectorStoreService.IngestResult(1L, "lid", 1);

            when(ingestService.ingestText("source", "title", "text", null, false, null))
                    .thenReturn(result);

            controller.ingestText(request);

            verify(ingestService).ingestText("source", "title", "text", null, false, null);
        }
    }

    @Nested
    class ProgressTests {

        @Test
        void progress_streamsEvents() {
            String jobId = "job-123";
            IngestProgressEvent event = IngestProgressEvent.of(jobId, "parsing", 1, 2, "Parsing");

            when(jobs.stream(jobId)).thenReturn(Flux.just(event));

            StepVerifier.create(controller.progress(jobId))
                    .assertNext(sse -> {
                        assertEquals("progress", sse.event());
                        assertEquals("parsing", sse.data().stage());
                    })
                    .verifyComplete();
        }

        @Test
        void progress_handlesMultipleEvents() {
            String jobId = "job-456";
            IngestProgressEvent event1 = IngestProgressEvent.of(jobId, "step1", 1, 3, "Step 1");
            IngestProgressEvent event2 = IngestProgressEvent.of(jobId, "step2", 2, 3, "Step 2");

            when(jobs.stream(jobId)).thenReturn(Flux.just(event1, event2));

            StepVerifier.create(controller.progress(jobId))
                    .expectNextCount(2)
                    .verifyComplete();
        }

        @Test
        void progress_handlesEmptyStream() {
            when(jobs.stream("unknown")).thenReturn(Flux.empty());

            StepVerifier.create(controller.progress("unknown"))
                    .verifyComplete();
        }
    }

    @Nested
    class ReembedTests {

        @Test
        void reembed_returnsUpdatedCount() {
            ReembedRequest request = new ReembedRequest("latest", "nomic-embed-text");

            when(ingestService.reembed("latest", "nomic-embed-text"))
                    .thenReturn(42);

            StepVerifier.create(controller.reembed(request))
                    .assertNext(result -> assertEquals(42, result.get("updatedChunks")))
                    .verifyComplete();
        }

        @Test
        void reembed_handlesNullScope() {
            ReembedRequest request = new ReembedRequest(null, "model");

            when(ingestService.reembed(null, "model"))
                    .thenReturn(10);

            StepVerifier.create(controller.reembed(request))
                    .assertNext(result -> assertEquals(10, result.get("updatedChunks")))
                    .verifyComplete();
        }

        @Test
        void reembed_handlesNullModel() {
            ReembedRequest request = new ReembedRequest("all", null);

            when(ingestService.reembed("all", null))
                    .thenReturn(5);

            StepVerifier.create(controller.reembed(request))
                    .assertNext(result -> assertEquals(5, result.get("updatedChunks")))
                    .verifyComplete();
        }

        @Test
        void reembed_handlesZeroChunks() {
            ReembedRequest request = new ReembedRequest("scope", "model");

            when(ingestService.reembed("scope", "model"))
                    .thenReturn(0);

            StepVerifier.create(controller.reembed(request))
                    .assertNext(result -> assertEquals(0, result.get("updatedChunks")))
                    .verifyComplete();
        }
    }
}
