package com.ai.agenticrag.ingest;

import com.ai.agenticrag.rag.VectorStoreService;
import org.springframework.stereotype.Service;

/**
 * Service responsible for facilitating the ingestion and re-embedding of textual data
 * into a backing vector store for further processing and querying.
 * This service acts as a higher-level abstraction over the {@link VectorStoreService}.
 * <p>
 * Responsibilities of the service include:
 * - Managing ingestion of text into the vector store with optional upsert mechanism by source and title.
 * - Reembedding existing text data based on specific scope and embedding model.
 */
@Service
public class IngestService {
    private final VectorStoreService vectorStore;

    /**
     * Constructs an instance of {@code IngestService}.
     *
     * @param vectorStore the vector store service used as a backing store for text ingestion
     *                    and embedding operations
     */
    public IngestService(VectorStoreService vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * Ingests textual data into the underlying vector store for processing and querying.
     * Optionally allows for updating or inserting records based on the combination of source
     * and title.
     *
     * @param source              the source identifier of the text being ingested
     * @param title               the title of the text being ingested
     * @param text                the actual text content to be ingested
     * @param logicalId           a logical identifier for the text, used for deduplication or tracking
     * @param upsertBySourceTitle whether to perform an upsert operation using the source and title
     *                            as unique identifiers
     * @param jobId               the job identifier for tracking the ingestion process
     * @return the result of the ingestion operation, including metadata about the process
     */
    public VectorStoreService.IngestResult ingestText(String source, String title,
                                                      String text, String logicalId,
                                                      boolean upsertBySourceTitle, String jobId) {
        return vectorStore.ingestText(source, title, text, logicalId, upsertBySourceTitle, jobId).block();
    }

    /**
     * Re-embeds existing text data in the vector store based on the specified scope and embedding model.
     * The operation identifies the relevant records using the provided scope, computes their embeddings
     * using the specified model, and updates the vector store accordingly.
     *
     * @param scope the scope defining the subset of data to be re-embedded; if null, blank, or "latest",
     *              it defaults to the latest data.
     * @param model the name of the embedding model to be applied; if null or blank, the default model
     *              configured in the system is used.
     * @return the number of records successfully re-embedded, or 0 if no updates occurred.
     */
    public int reembed(String scope, String model) {
        Integer n = vectorStore.reembed(scope, model).block();
        return n == null ? 0 : n;
    }
}
