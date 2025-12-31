package com.ai.agenticrag.ingest;

import com.ai.agenticrag.rag.VectorStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for IngestService.
 */
@ExtendWith(MockitoExtension.class)
class IngestServiceTest {

    @Mock
    private VectorStoreService vectorStore;

    private IngestService ingestService;

    @BeforeEach
    void setUp() {
        ingestService = new IngestService(vectorStore);
    }

    @Test
    void ingestText_delegatesToVectorStore() {
        var expectedResult = new VectorStoreService.IngestResult(1L, "logical-123", 1);
        when(vectorStore.ingestText("source", "title", "text", "logicalId", true, "jobId"))
                .thenReturn(Mono.just(expectedResult));

        var result = ingestService.ingestText("source", "title", "text", "logicalId", true, "jobId");

        assertEquals(1L, result.documentId());
        assertEquals("logical-123", result.logicalId());
        assertEquals(1, result.version());
        verify(vectorStore).ingestText("source", "title", "text", "logicalId", true, "jobId");
    }

    @Test
    void ingestText_withUpsertFalse() {
        var expectedResult = new VectorStoreService.IngestResult(2L, "logical-456", 2);
        when(vectorStore.ingestText("src", "ttl", "txt", null, false, null))
                .thenReturn(Mono.just(expectedResult));

        var result = ingestService.ingestText("src", "ttl", "txt", null, false, null);

        assertEquals(2L, result.documentId());
        assertEquals(2, result.version());
        verify(vectorStore).ingestText("src", "ttl", "txt", null, false, null);
    }

    @Test
    void ingestText_withNullLogicalId() {
        var expectedResult = new VectorStoreService.IngestResult(3L, "auto-generated-id", 1);
        when(vectorStore.ingestText("s", "t", "x", null, true, "job"))
                .thenReturn(Mono.just(expectedResult));

        var result = ingestService.ingestText("s", "t", "x", null, true, "job");

        assertNotNull(result);
        assertEquals("auto-generated-id", result.logicalId());
    }

    @Test
    void reembed_delegatesToVectorStore() {
        when(vectorStore.reembed("latest", "nomic-embed-text"))
                .thenReturn(Mono.just(42));

        int count = ingestService.reembed("latest", "nomic-embed-text");

        assertEquals(42, count);
        verify(vectorStore).reembed("latest", "nomic-embed-text");
    }

    @Test
    void reembed_withNullScope() {
        when(vectorStore.reembed(null, "model"))
                .thenReturn(Mono.just(10));

        int count = ingestService.reembed(null, "model");

        assertEquals(10, count);
    }

    @Test
    void reembed_withNullModel() {
        when(vectorStore.reembed("scope", null))
                .thenReturn(Mono.just(5));

        int count = ingestService.reembed("scope", null);

        assertEquals(5, count);
    }

    @Test
    void reembed_returnsZeroWhenNullResult() {
        when(vectorStore.reembed("scope", "model"))
                .thenReturn(Mono.empty());

        int count = ingestService.reembed("scope", "model");

        assertEquals(0, count);
    }

    @Test
    void reembed_returnsZeroWhenMonoReturnsNull() {
        when(vectorStore.reembed("scope", "model"))
                .thenReturn(Mono.justOrEmpty(null));

        int count = ingestService.reembed("scope", "model");

        assertEquals(0, count);
    }
}
