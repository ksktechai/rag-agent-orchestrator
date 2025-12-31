package com.ai.agenticrag.rag;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository class for managing and querying document versioning data in a relational database.
 * <p>
 * This class interacts with a database to perform operations related to document versioning,
 * including fetching the latest document version based on its source and title,
 * and marking specific versions of a document as not being the latest.
 * The repository relies on Spring's JdbcTemplate for interacting with the database.
 * <p>
 * Methods:
 * <p>
 * - findLatestBySourceTitle(String source, String title): Queries and retrieves the
 * latest version of a document specified by its source and title, if it exists.
 * <p>
 * - markNotLatest(UUID logicalId): Updates the database to mark all versions of
 * a document with the specified logical ID as no longer being the latest version.
 */
@Repository
public class DocumentVersioningRepository {
    /**
     * A Spring JdbcTemplate instance used to interact with the underlying relational database.
     * <p>
     * This variable is employed for executing SQL queries and updates, managing transactions,
     * and processing result sets in a streamlined and consistent manner. It provides a high-level
     * API that simplifies database interactions while abstracting away the complexities of
     * directly using JDBC.
     * <p>
     * Typical operations performed using this JdbcTemplate object include querying records,
     * updating existing data, and handling database transactions related to document versioning.
     */
    private final JdbcTemplate jdbc;

    /**
     * Constructs an instance of the DocumentVersioningRepository class using the given {@code JdbcTemplate}.
     * This repository is responsible for operations related to document versioning in the database.
     *
     * @param jdbc an instance of {@code JdbcTemplate} used to execute database queries and updates
     */
    public DocumentVersioningRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Queries the database to retrieve the latest version of a document identified by its source and title.
     * The method uses the provided source and title as parameters to locate the document
     * and returns an {@code Optional} containing the latest version if it exists.
     *
     * @param source the source of the document, typically used to group or categorize documents.
     * @param title  the title of the document, used to uniquely identify the document within the given source.
     * @return an {@code Optional} containing the {@code DocVersionRow} object representing the latest version of the document,
     * or an empty {@code Optional} if no document matching the source and title is found.
     */
    public Optional<DocVersionRow> findLatestBySourceTitle(String source, String title) {
        String sql = "SELECT id, logical_id, version FROM documents WHERE source=? AND title=? AND is_latest=true ORDER BY version DESC LIMIT 1";
        return jdbc.query(sql, rs -> {
            if (!rs.next()) return Optional.empty();
            return Optional.of(new DocVersionRow(
                    rs.getLong("id"),
                    (UUID) rs.getObject("logical_id"),
                    rs.getInt("version")
            ));
        }, source, title);
    }

    /**
     * Marks the document identified by the specified logical ID as not being the latest version
     * in the database. The method updates the `is_latest` flag to `false` for the document that
     * matches the given logical ID and has its `is_latest` flag currently set to `true`.
     *
     * @param logicalId the UUID representing the logical identifier of the document
     *                  to be marked as not the latest version.
     */
    public void markNotLatest(UUID logicalId) {
        jdbc.update("UPDATE documents SET is_latest=false WHERE logical_id=? AND is_latest=true", logicalId);
    }

    /**
     * Represents a version entry for a document in the versioning system.
     * <p>
     * Each instance of this record encapsulates key information about a specific
     * version of a document, identified by its database ID, logical identifier,
     * and version number. Typically used in the context of document versioning
     * to manage and track changes or updates to documents.
     * <p>
     * Fields:
     * - id: The unique database identifier for the version entry.
     * - logicalId: The unique logical identifier (UUID) for the document across versions.
     * - version: The version number of the document.
     */
    public record DocVersionRow(long id, UUID logicalId, int version) {
    }
}
