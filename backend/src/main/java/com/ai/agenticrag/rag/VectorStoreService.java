package com.ai.agenticrag.rag;

import com.ai.agenticrag.ingest.Chunker;
import com.pgvector.PGvector;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The VectorStoreService is responsible for managing the vectorized storage of documents
 * and their chunks for efficient retrieval and search. It provides methods for ingesting
 * text documents, performing vector-based searches, and re-embedding document chunks using
 * an embedding model.
 * <p>
 * This service interacts with a relational database, leveraging the JdbcTemplate for
 * data access, and uses an embedding model to generate vector representations of text.
 * Document versioning is also supported to maintain and track different versions of
 * ingested documents.
 * <p>
 * The service offers reactive, non-blocking operations wrapped in {@code Mono} from the
 * Project Reactor library.
 */
@Service
public class VectorStoreService {
    private final JdbcTemplate jdbc;
    private final EmbeddingModel embedding;
    private final DocumentVersioningRepository versioning;
    private final String configuredEmbeddingModelName;

    /**
     * Constructs an instance of {@code VectorStoreService}, which manages the storage
     * and retrieval of vectorized document chunks, and provides embedding and versioning functionality.
     *
     * @param jdbc                         the {@link JdbcTemplate} used for database operations
     * @param embedding                    the {@link EmbeddingModel} used for generating vector embeddings
     * @param versioning                   the {@link DocumentVersioningRepository} used for document versioning operations
     * @param configuredEmbeddingModelName the default embedding model name configured through properties
     */
    public VectorStoreService(
            JdbcTemplate jdbc,
            EmbeddingModel embedding,
            DocumentVersioningRepository versioning,
            @Value("${spring.ai.ollama.embedding.model:nomic-embed-text}") String configuredEmbeddingModelName
    ) {
        this.jdbc = jdbc;
        this.embedding = embedding;
        this.versioning = versioning;
        this.configuredEmbeddingModelName = configuredEmbeddingModelName;
    }

    public Mono<List<ChunkHit>> search(String query, int k, String jobId) {

        if (query == null || query.isBlank()) {
            System.out.println("VectorStoreService.search(): query is NULL/blank. jobId=" + jobId);
            return Mono.empty();
        }

        return Mono.fromCallable(() -> {
            float[] vec = embedding.embed(query);

            // quick debug (remove later)
            System.out.println("QUERY_EMB_DIM=" + vec.length);

            String v = toPgVectorLiteral(vec);

            String sql =
                    "SELECT c.id, c.document_id, d.title, c.chunk_index, c.content, " +
                            "       (1 - (c.embedding <=> ?::vector)) AS score " +
                            "FROM chunks c " +
                            "JOIN documents d ON d.id = c.document_id " +
                            "WHERE c.embedding IS NOT NULL " +
                            "  AND d.is_latest=true " +
                            "  AND c.embedding_model = ? " +
                            "ORDER BY c.embedding <=> ?::vector " +
                            "LIMIT ?";

            return jdbc.query(sql,
                    (rs, rowNum) -> new ChunkHit(
                            rs.getLong(1),
                            rs.getLong(2),
                            rs.getString(3),
                            rs.getInt(4),
                            rs.getString(5),
                            rs.getDouble(6)
                    ),
                    v, configuredEmbeddingModelName, v, k
            );
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private static String toPgVectorLiteral(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 8);
        sb.append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        sb.append(']');
        return sb.toString();
    }


    /**
     * Ingests the provided text into the storage system, handling versioning, chunking, and embedding operations.
     * If logicalId is provided, it associates the text with an existing logical document; otherwise, a new logical document
     * is created. If upsertBySourceTitle is true and logicalId is not provided, an attempt is made to look up an
     * existing logical document by source and title to determine versioning details.
     *
     * @param source              The source identifier for the text being ingested.
     * @param title               The title of the document being ingested.
     * @param text                The content of the document to be ingested.
     * @param logicalId           The logical document ID to associate with the text. If null or blank,
     *                            a new logical document ID is created unless upsertBySourceTitle is true.
     * @param upsertBySourceTitle Whether to look up an existing logical document by source and title and
     *                            amend it with a new version if logicalId is null or blank.
     * @param jobId               The job identifier for tracking the ingestion process.
     * @return A Mono emitting an IngestResult that contains the document ID, logical ID,
     * and version number of the ingested document.
     */
    public Mono<IngestResult> ingestText(String source, String title, String text, String logicalId, boolean upsertBySourceTitle, String jobId) {
        // Ingests document text; chunks and embeds content; persists metadata
        return Mono.fromCallable(() -> {
            UUID lid = (logicalId == null || logicalId.isBlank()) ? null : UUID.fromString(logicalId);
            int nextVersion = 1;

            // Finds or creates logical ID; computes next version
            if (lid == null && upsertBySourceTitle) {
                Optional<DocumentVersioningRepository.DocVersionRow> latest = versioning.findLatestBySourceTitle(source, title);
                // Updates versioning metadata when document exists
                if (latest.isPresent()) {
                    lid = latest.get().logicalId();
                    nextVersion = latest.get().version() + 1;
                    versioning.markNotLatest(lid);
                }
            } else if (lid != null) {
                versioning.markNotLatest(lid);
                Integer v = jdbc.queryForObject("SELECT COALESCE(MAX(version),0) FROM documents WHERE logical_id=?", Integer.class, lid);
                nextVersion = (v == null ? 0 : v) + 1;
            }

            if (lid == null) lid = UUID.randomUUID();

            Long docId = jdbc.queryForObject(
                    "INSERT INTO documents(source, title, text, logical_id, version, is_latest) VALUES (?,?,?,?,?,true) RETURNING id",
                    Long.class, source, title, text, lid, nextVersion
            );
            if (docId == null) throw new IllegalStateException("Failed to insert document");

            List<String> chunks = Chunker.smartChunk(text);

            int idx = 0;
            for (String c : chunks) {
                float[] vec = embedding.embed(c);
                jdbc.update("INSERT INTO chunks(document_id, chunk_index, content, embedding, embedding_model) VALUES (?,?,?,?,?)",
                        docId, idx++, c, new PGvector(vec), configuredEmbeddingModelName);
            }

            jdbc.update("INSERT INTO ingest_jobs(job_id, document_id) VALUES (?, ?)",
                    jobId, docId);
            return new IngestResult(docId, lid.toString(), nextVersion);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Recalculates and updates vector embeddings for document chunks based on the given scope and model name.
     *
     * @param scope     The scope specifying the subset of documents to process. If null, blank, or "latest", only
     *                  the latest documents will be targeted; otherwise, all documents will be processed.
     * @param modelName The name of the embedding model to use. If null or blank, the configured default model
     *                  name will be used.
     * @return A {@code Mono<Integer>} representing the total count of chunks that were updated with new
     * embeddings.
     */
    public Mono<Integer> reembed(String scope, String modelName) {
        return Mono.fromCallable(() -> {
            String where = (scope == null || scope.isBlank() || "latest".equalsIgnoreCase(scope))
                    ? "WHERE d.is_latest=true"
                    : "";
            var docIds = jdbc.queryForList("SELECT d.id FROM documents d " + where + " ORDER BY d.id", Long.class);

            int updated = 0;
            for (Long docId : docIds) {
                var rows = jdbc.query("SELECT id, content FROM chunks WHERE document_id=? ORDER BY chunk_index",
                        (rs, rowNum) -> new Row(rs.getLong(1), rs.getString(2)), docId);

                for (Row row : rows) {
                    float[] vec = embedding.embed(row.content);
                    jdbc.update("UPDATE chunks SET embedding=?, embedding_model=? WHERE id=?",
                            new PGvector(vec),
                            (modelName == null || modelName.isBlank()) ? configuredEmbeddingModelName : modelName,
                            row.id
                    );
                    updated++;
                }
            }
            return updated;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Checks if there are any documents marked as the latest version in the database.
     * <p>
     * This method queries the database to count the number of rows in the 'documents'
     * table where the 'is_latest' field is set to true.
     *
     * @return true if there is at least one document marked as the latest version; false otherwise
     */
    public boolean hasAnyDocuments() {
        Integer n = jdbc.queryForObject("select count(*) from documents where is_latest=true", Integer.class);
        return n != null && n > 0;
    }

    /**
     * Represents the result of an ingestion operation, containing details about the ingested document.
     *
     * @param documentId The unique identifier of the ingested document.
     * @param logicalId  The logical identifier associated with the document.
     * @param version    The version number of the ingested document.
     */
    public record IngestResult(long documentId, String logicalId, int version) {
    }

    /**
     * Represents a row with a unique identifier and associated content.
     * <p>
     * This class is an immutable data structure used to encapsulate a unique
     * identifier (id) and a string representing the content of the row.
     * <p>
     * It uses the record feature of Java to provide a compact and concise
     * representation of its properties.
     * <p>
     * Properties:
     * - id: A unique long value identifying the row.
     * - content: A string representing the content associated with this row.
     */
    private record Row(long id, String content) {
    }
}
