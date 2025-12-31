package com.ai.agenticrag.rag;

/**
 * Represents a citation extracted from a document or a chunk search result.
 * This record encapsulates key metadata associated with a citation, such as
 * the title of the document, the index of the chunk where the relevant
 * information is found, and a score indicating the significance or relevance
 * of the citation.
 *
 * Fields:
 * - title: The title of the document or source associated with the citation.
 * - chunkIndex: The position or index of the chunk within the document.
 * - score: A numerical score representing the relevance or quality of the citation.
 */
public record Citation(String title,
                       int chunkIndex,
                       double score) {
}
