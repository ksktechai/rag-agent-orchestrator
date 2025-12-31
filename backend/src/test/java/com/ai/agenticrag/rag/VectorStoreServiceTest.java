package com.ai.agenticrag.rag;

import com.pgvector.PGvector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for VectorStoreService.
 */
@ExtendWith(MockitoExtension.class)
class VectorStoreServiceTest {

        @Mock
        private JdbcTemplate jdbcTemplate;

        @Mock
        private EmbeddingModel embeddingModel;

        @Mock
        private DocumentVersioningRepository versioningRepository;

        private VectorStoreService vectorStoreService;

        @BeforeEach
        void setUp() {
                vectorStoreService = new VectorStoreService(
                                jdbcTemplate,
                                embeddingModel,
                                versioningRepository,
                                "nomic-embed-text");
        }

        @Nested
        class SearchTests {

                @Test
                void search_returnsChunkHits() {
                        String query = "What is AI?";
                        float[] embedding = new float[] { 0.1f, 0.2f, 0.3f };

                        when(embeddingModel.embed(query)).thenReturn(embedding);
                        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any(), anyInt()))
                                        .thenReturn(List.of(
                                                        new ChunkHit(1L, 100L, "AI Document", 0, "Content about AI",
                                                                        0.95)));

                        StepVerifier.create(vectorStoreService.search(query, 5))
                                        .assertNext(hits -> {
                                                assertEquals(1, hits.size());
                                                assertEquals("AI Document", hits.get(0).title());
                                        })
                                        .verifyComplete();
                }

                @Test
                void search_returnsEmptyForBlankQuery() {
                        StepVerifier.create(vectorStoreService.search("", 5))
                                        .verifyComplete();

                        StepVerifier.create(vectorStoreService.search("   ", 5))
                                        .verifyComplete();
                }

                @Test
                void search_returnsEmptyForNullQuery() {
                        StepVerifier.create(vectorStoreService.search(null, 5))
                                        .verifyComplete();
                }

                @Test
                void search_returnsEmptyListWhenNoResults() {
                        String query = "obscure query";
                        float[] embedding = new float[] { 0.1f, 0.2f, 0.3f };

                        when(embeddingModel.embed(query)).thenReturn(embedding);
                        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any(), anyInt()))
                                        .thenReturn(List.of());

                        StepVerifier.create(vectorStoreService.search(query, 5))
                                        .assertNext(hits -> assertTrue(hits.isEmpty()))
                                        .verifyComplete();
                }
        }

        @Nested
        class IngestTextTests {

                @Test
                void ingestText_createsNewDocument() {
                        String source = "test-source";
                        String title = "Test Title";
                        String text = "Short text";
                        float[] embedding = new float[] { 0.1f, 0.2f, 0.3f };

                        when(versioningRepository.findLatestBySourceTitle(source, title))
                                        .thenReturn(Optional.empty());
                        when(jdbcTemplate.queryForObject(contains("INSERT INTO documents"), eq(Long.class), any(),
                                        any(), any(),
                                        any(), anyInt()))
                                        .thenReturn(1L);
                        when(embeddingModel.embed(anyString())).thenReturn(embedding);

                        StepVerifier.create(vectorStoreService.ingestText(source, title, text, null, true, "job-1"))
                                        .assertNext(result -> {
                                                assertEquals(1L, result.documentId());
                                                assertNotNull(result.logicalId());
                                                assertEquals(1, result.version());
                                        })
                                        .verifyComplete();
                }

                @Test
                void ingestText_incrementsVersionForExistingDocument() {
                        String source = "source";
                        String title = "title";
                        String text = "Content";
                        UUID existingLogicalId = UUID.randomUUID();
                        float[] embedding = new float[] { 0.1f, 0.2f, 0.3f };

                        when(versioningRepository.findLatestBySourceTitle(source, title))
                                        .thenReturn(Optional.of(new DocumentVersioningRepository.DocVersionRow(1L,
                                                        existingLogicalId, 2)));
                        when(jdbcTemplate.queryForObject(contains("INSERT INTO documents"), eq(Long.class), any(),
                                        any(), any(),
                                        any(), anyInt()))
                                        .thenReturn(2L);
                        when(embeddingModel.embed(anyString())).thenReturn(embedding);

                        StepVerifier.create(vectorStoreService.ingestText(source, title, text, null, true, null))
                                        .assertNext(result -> {
                                                assertEquals(2L, result.documentId());
                                                assertEquals(existingLogicalId.toString(), result.logicalId());
                                                assertEquals(3, result.version());
                                        })
                                        .verifyComplete();

                        verify(versioningRepository).markNotLatest(existingLogicalId);
                }

                @Test
                void ingestText_usesProvidedLogicalId() {
                        String logicalId = UUID.randomUUID().toString();
                        float[] embedding = new float[] { 0.1f, 0.2f };

                        when(jdbcTemplate.queryForObject(contains("MAX(version)"), eq(Integer.class), any(UUID.class)))
                                        .thenReturn(1);
                        when(jdbcTemplate.queryForObject(contains("INSERT INTO documents"), eq(Long.class), any(),
                                        any(), any(),
                                        any(), anyInt()))
                                        .thenReturn(1L);
                        when(embeddingModel.embed(anyString())).thenReturn(embedding);

                        StepVerifier.create(vectorStoreService.ingestText("s", "t", "x", logicalId, false, null))
                                        .assertNext(result -> {
                                                assertEquals(logicalId, result.logicalId());
                                                assertEquals(2, result.version());
                                        })
                                        .verifyComplete();
                }
        }

        @Nested
        class ReembedTests {

                @Test
                void reembed_updatesChunksForLatestDocuments() {
                        float[] embedding = new float[] { 0.1f, 0.2f };

                        when(jdbcTemplate.queryForList(contains("is_latest=true"), eq(Long.class)))
                                        .thenReturn(List.of(1L, 2L));
                        when(jdbcTemplate.query(contains("SELECT id, content FROM chunks"), any(RowMapper.class),
                                        eq(1L)))
                                        .thenReturn(List.of());
                        when(jdbcTemplate.query(contains("SELECT id, content FROM chunks"), any(RowMapper.class),
                                        eq(2L)))
                                        .thenReturn(List.of());

                        StepVerifier.create(vectorStoreService.reembed("latest", null))
                                        .assertNext(count -> assertEquals(0, count))
                                        .verifyComplete();
                }

                @Test
                void reembed_processsesAllDocumentsForNonLatestScope() {
                        when(jdbcTemplate.queryForList(eq("SELECT d.id FROM documents d  ORDER BY d.id"),
                                        eq(Long.class)))
                                        .thenReturn(List.of(1L));
                        when(jdbcTemplate.query(contains("SELECT id, content FROM chunks"), any(RowMapper.class),
                                        eq(1L)))
                                        .thenReturn(List.of());

                        StepVerifier.create(vectorStoreService.reembed("all", "custom-model"))
                                        .assertNext(count -> assertEquals(0, count))
                                        .verifyComplete();
                }

                @Test
                void reembed_withNullScope_usesLatest() {
                        when(jdbcTemplate.queryForList(contains("is_latest=true"), eq(Long.class)))
                                        .thenReturn(List.of());

                        StepVerifier.create(vectorStoreService.reembed(null, null))
                                        .assertNext(count -> assertEquals(0, count))
                                        .verifyComplete();
                }

                @Test
                void reembed_withBlankScope_usesLatest() {
                        when(jdbcTemplate.queryForList(contains("is_latest=true"), eq(Long.class)))
                                        .thenReturn(List.of());

                        StepVerifier.create(vectorStoreService.reembed("  ", null))
                                        .assertNext(count -> assertEquals(0, count))
                                        .verifyComplete();
                }
        }

        @Nested
        class HasAnyDocumentsTests {

                @Test
                void hasAnyDocuments_returnsTrueWhenDocumentsExist() {
                        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class)))
                                        .thenReturn(5);

                        assertTrue(vectorStoreService.hasAnyDocuments());
                }

                @Test
                void hasAnyDocuments_returnsFalseWhenNoDocuments() {
                        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class)))
                                        .thenReturn(0);

                        assertFalse(vectorStoreService.hasAnyDocuments());
                }

                @Test
                void hasAnyDocuments_returnsFalseWhenNull() {
                        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class)))
                                        .thenReturn(null);

                        assertFalse(vectorStoreService.hasAnyDocuments());
                }
        }

        @Nested
        class IngestResultTests {

                @Test
                void ingestResult_fieldAccessors() {
                        VectorStoreService.IngestResult result = new VectorStoreService.IngestResult(
                                        123L, "logical-id-456", 3);

                        assertEquals(123L, result.documentId());
                        assertEquals("logical-id-456", result.logicalId());
                        assertEquals(3, result.version());
                }

                @Test
                void ingestResult_equality() {
                        VectorStoreService.IngestResult r1 = new VectorStoreService.IngestResult(1L, "lid", 1);
                        VectorStoreService.IngestResult r2 = new VectorStoreService.IngestResult(1L, "lid", 1);

                        assertEquals(r1, r2);
                        assertEquals(r1.hashCode(), r2.hashCode());
                }
        }

        @Nested
        class AdditionalCoverageTests {

                @Test
                void ingestText_throwsWhenDocIdIsNull() {
                        String source = "source";
                        String title = "title";
                        String text = "text";
                        float[] embedding = new float[] { 0.1f, 0.2f };

                        when(versioningRepository.findLatestBySourceTitle(source, title))
                                        .thenReturn(Optional.empty());
                        when(jdbcTemplate.queryForObject(contains("INSERT INTO documents"), eq(Long.class), any(),
                                        any(), any(),
                                        any(), anyInt()))
                                        .thenReturn(null); // Simulate failure

                        StepVerifier.create(vectorStoreService.ingestText(source, title, text, null, true, null))
                                        .expectError(IllegalStateException.class)
                                        .verify();
                }

                @Test
                void ingestText_handlesNullMaxVersion() {
                        String logicalId = UUID.randomUUID().toString();
                        float[] embedding = new float[] { 0.1f, 0.2f };

                        when(jdbcTemplate.queryForObject(contains("MAX(version)"), eq(Integer.class), any(UUID.class)))
                                        .thenReturn(null); // NULL case for v
                        when(jdbcTemplate.queryForObject(contains("INSERT INTO documents"), eq(Long.class), any(),
                                        any(), any(),
                                        any(), anyInt()))
                                        .thenReturn(1L);
                        when(embeddingModel.embed(anyString())).thenReturn(embedding);

                        StepVerifier.create(vectorStoreService.ingestText("s", "t", "x", logicalId, false, null))
                                        .assertNext(result -> {
                                                assertEquals(1, result.version()); // (null ? 0 : v) + 1 = 1
                                        })
                                        .verifyComplete();
                }

                @Test
                void ingestText_upsertFalseWithNullLogicalId_createsNewId() {
                        float[] embedding = new float[] { 0.1f, 0.2f };

                        when(jdbcTemplate.queryForObject(contains("INSERT INTO documents"), eq(Long.class), any(),
                                        any(), any(),
                                        any(), anyInt()))
                                        .thenReturn(1L);
                        when(embeddingModel.embed(anyString())).thenReturn(embedding);

                        StepVerifier.create(vectorStoreService.ingestText("s", "t", "x", null, false, null))
                                        .assertNext(result -> {
                                                assertNotNull(result.logicalId());
                                                assertEquals(1, result.version());
                                        })
                                        .verifyComplete();
                }

                @Test
                void reembed_updatesChunksWithDefaultModel() {
                        when(jdbcTemplate.queryForList(contains("is_latest=true"), eq(Long.class)))
                                        .thenReturn(List.of(1L));

                        // Simulate no chunks for this document
                        when(jdbcTemplate.query(contains("SELECT id, content FROM chunks"), any(RowMapper.class),
                                        eq(1L)))
                                        .thenReturn(List.of());

                        StepVerifier.create(vectorStoreService.reembed("latest", null))
                                        .assertNext(count -> assertEquals(0, count))
                                        .verifyComplete();
                }

                @Test
                void reembed_usesCustomModelWhenProvided() {
                        when(jdbcTemplate.queryForList(contains("is_latest=true"), eq(Long.class)))
                                        .thenReturn(List.of());

                        StepVerifier.create(vectorStoreService.reembed("latest", "custom-model"))
                                        .assertNext(count -> assertEquals(0, count))
                                        .verifyComplete();
                }

                @Test
                void reembed_usesDefaultModelWhenModelNameBlank() {
                        when(jdbcTemplate.queryForList(contains("is_latest=true"), eq(Long.class)))
                                        .thenReturn(List.of());

                        StepVerifier.create(vectorStoreService.reembed("latest", "   "))
                                        .assertNext(count -> assertEquals(0, count))
                                        .verifyComplete();
                }

                @Test
                void search_multipleResults() {
                        String query = "test query";
                        float[] embedding = new float[] { 0.1f, 0.2f, 0.3f };

                        when(embeddingModel.embed(query)).thenReturn(embedding);
                        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any(), anyInt()))
                                        .thenReturn(List.of(
                                                        new ChunkHit(1L, 100L, "Doc1", 0, "Content 1", 0.95),
                                                        new ChunkHit(2L, 100L, "Doc1", 1, "Content 2", 0.90),
                                                        new ChunkHit(3L, 101L, "Doc2", 0, "Content 3", 0.85)));

                        StepVerifier.create(vectorStoreService.search(query, 10))
                                        .assertNext(hits -> {
                                                assertEquals(3, hits.size());
                                                assertEquals(0.95, hits.get(0).score(), 0.001);
                                        })
                                        .verifyComplete();
                }

                @Test
                void ingestText_chunksAndEmbedsMultipleChunks() {
                        String source = "source";
                        String title = "title";
                        // Create text that will result in multiple chunks
                        String text = "First paragraph with enough content. " +
                                        "Second paragraph with more content. " +
                                        "Third paragraph completing the text.";
                        float[] embedding = new float[] { 0.1f, 0.2f, 0.3f };

                        when(versioningRepository.findLatestBySourceTitle(source, title))
                                        .thenReturn(Optional.empty());
                        when(jdbcTemplate.queryForObject(contains("INSERT INTO documents"), eq(Long.class), any(),
                                        any(), any(),
                                        any(), anyInt()))
                                        .thenReturn(1L);
                        when(embeddingModel.embed(anyString())).thenReturn(embedding);

                        StepVerifier.create(vectorStoreService.ingestText(source, title, text, null, true, "job"))
                                        .assertNext(result -> {
                                                assertEquals(1L, result.documentId());
                                                assertEquals(1, result.version());
                                        })
                                        .verifyComplete();

                        // Verify embedding was called at least once for chunks
                        verify(embeddingModel, atLeastOnce()).embed(anyString());
                }

                @Test
                void ingestText_blankLogicalId_treatedAsNull() {
                        float[] embedding = new float[] { 0.1f, 0.2f };

                        when(versioningRepository.findLatestBySourceTitle("s", "t"))
                                        .thenReturn(Optional.empty());
                        when(jdbcTemplate.queryForObject(contains("INSERT INTO documents"), eq(Long.class), any(),
                                        any(), any(),
                                        any(), anyInt()))
                                        .thenReturn(1L);
                        when(embeddingModel.embed(anyString())).thenReturn(embedding);

                        // Blank logicalId should be treated as null
                        StepVerifier.create(vectorStoreService.ingestText("s", "t", "x", "   ", true, null))
                                        .assertNext(result -> {
                                                assertNotNull(result.logicalId());
                                                assertEquals(1, result.version());
                                        })
                                        .verifyComplete();
                }
        }
}
