package com.ai.agenticrag.rag;

/**
 * A record representing the result of a chunk search operation, often used in vector-based
 * information retrieval systems. Each instance of ChunkHit encapsulates detailed information
 * about a specific chunk matched during a search query.
 *
 * The fields in this record provide metadata and content for the specific chunk, as well as
 * a relevance score, which reflects the similarity between the query and the chunk's content.
 *
 * Fields:
 * - chunkId: The unique identifier for the chunk.
 * - documentId: The unique identifier for the document to which this chunk belongs.
 * - title: The title of the document containing the chunk.
 * - chunkIndex: The position/index of the chunk within the document.
 * - content: The raw content of the chunk.
 * - score: The similarity score of the chunk with respect to the search query (higher scores
 *   indicate better matches).
 */
public record ChunkHit(long chunkId, long documentId, String title, int chunkIndex, String content, double score) {}
