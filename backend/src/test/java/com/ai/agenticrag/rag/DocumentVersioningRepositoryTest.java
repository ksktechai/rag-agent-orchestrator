package com.ai.agenticrag.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DocumentVersioningRepository.
 */
@ExtendWith(MockitoExtension.class)
class DocumentVersioningRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ResultSet resultSet;

    private DocumentVersioningRepository repository;

    @BeforeEach
    void setUp() {
        repository = new DocumentVersioningRepository(jdbcTemplate);
    }

    @Test
    void findLatestBySourceTitle_returnsDocVersionRow() throws SQLException {
        UUID logicalId = UUID.randomUUID();

        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), eq("source"), eq("title")))
                .thenAnswer(invocation -> {
                    ResultSetExtractor<Optional<DocumentVersioningRepository.DocVersionRow>> extractor = invocation
                            .getArgument(1);

                    when(resultSet.next()).thenReturn(true);
                    when(resultSet.getLong("id")).thenReturn(123L);
                    when(resultSet.getObject("logical_id")).thenReturn(logicalId);
                    when(resultSet.getInt("version")).thenReturn(5);

                    return extractor.extractData(resultSet);
                });

        Optional<DocumentVersioningRepository.DocVersionRow> result = repository.findLatestBySourceTitle("source",
                "title");

        assertTrue(result.isPresent());
        assertEquals(123L, result.get().id());
        assertEquals(logicalId, result.get().logicalId());
        assertEquals(5, result.get().version());
    }

    @Test
    void findLatestBySourceTitle_returnsEmptyWhenNoResult() throws SQLException {
        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), eq("source"), eq("title")))
                .thenAnswer(invocation -> {
                    ResultSetExtractor<Optional<DocumentVersioningRepository.DocVersionRow>> extractor = invocation
                            .getArgument(1);

                    when(resultSet.next()).thenReturn(false);

                    return extractor.extractData(resultSet);
                });

        Optional<DocumentVersioningRepository.DocVersionRow> result = repository.findLatestBySourceTitle("source",
                "title");

        assertTrue(result.isEmpty());
    }

    @Test
    void findLatestBySourceTitle_queriesWithCorrectParameters() {
        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), anyString(), anyString()))
                .thenReturn(Optional.empty());

        repository.findLatestBySourceTitle("my-source", "my-title");

        verify(jdbcTemplate).query(
                contains("SELECT id, logical_id, version FROM documents"),
                any(ResultSetExtractor.class),
                eq("my-source"),
                eq("my-title"));
    }

    @Test
    void markNotLatest_executesUpdate() {
        UUID logicalId = UUID.randomUUID();

        when(jdbcTemplate.update(anyString(), any(UUID.class))).thenReturn(1);

        repository.markNotLatest(logicalId);

        verify(jdbcTemplate).update(
                contains("UPDATE documents SET is_latest=false"),
                eq(logicalId));
    }

    @Test
    void markNotLatest_handlesNoRowsUpdated() {
        UUID logicalId = UUID.randomUUID();

        when(jdbcTemplate.update(anyString(), any(UUID.class))).thenReturn(0);

        // Should not throw when no rows updated
        assertDoesNotThrow(() -> repository.markNotLatest(logicalId));
    }

    @Test
    void docVersionRow_recordEquality() {
        UUID id = UUID.randomUUID();
        DocumentVersioningRepository.DocVersionRow row1 = new DocumentVersioningRepository.DocVersionRow(1L, id, 1);
        DocumentVersioningRepository.DocVersionRow row2 = new DocumentVersioningRepository.DocVersionRow(1L, id, 1);

        assertEquals(row1, row2);
        assertEquals(row1.hashCode(), row2.hashCode());
    }

    @Test
    void docVersionRow_fieldAccessors() {
        UUID logicalId = UUID.randomUUID();
        DocumentVersioningRepository.DocVersionRow row = new DocumentVersioningRepository.DocVersionRow(
                100L, logicalId, 3);

        assertEquals(100L, row.id());
        assertEquals(logicalId, row.logicalId());
        assertEquals(3, row.version());
    }

    @Test
    void docVersionRow_toString() {
        UUID logicalId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        DocumentVersioningRepository.DocVersionRow row = new DocumentVersioningRepository.DocVersionRow(
                1L, logicalId, 2);

        String str = row.toString();
        assertTrue(str.contains("123e4567"));
    }
}
