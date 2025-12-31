package com.ai.agenticrag.ingest;

import org.apache.tika.Tika;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.File;

/**
 * Command-line application for ingesting text content from files within a folder
 * into a storage system using the IngestService. This application processes text
 * files by extracting their content and uploading it into the system with optional
 * deduplication based on source and title.
 * <p>
 * The program accepts a single argument specifying the folder path whose files are
 * to be ingested. It validates that the path corresponds to an existing directory,
 * processes each file within the folder, and uses Apache Tika to extract text content
 * from the files.
 * <p>
 * Processed text content is ingested using the IngestService, and the ingestion process
 * generates a unique document identifier for each successfully processed file. If a file
 * cannot be processed (e.g., unsupported format, parsing errors), it is skipped with an
 * error message.
 * <p>
 * Expects the AgenticRagApplication as the Spring Boot application context and
 * relies on dependency injection to retrieve the IngestService bean.
 * <p>
 * Responsibilities:
 * 1. Validate the provided folder path.
 * 2. Use Apache Tika to parse and extract text content from files.
 * 3. Ingest extracted text into the storage system using the IngestService.
 * 4. Handle and log parsing or ingestion failures gracefully.
 * 5. Output the results of the ingestion process, including document IDs.
 */
public final class IngestFolderCli {
    /**
     * CLI ingests folder contents using dependency injection
     */
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: IngestFolderCli <folderPath>");
            System.exit(1);
        }

        File folder = new File(args[0]);
        // Exits if path is not a valid folder
        if (!folder.exists() || !folder.isDirectory()) {
            System.err.println("Not a folder: " + folder.getAbsolutePath());
            System.exit(1);
        }

        try (ConfigurableApplicationContext ctx = SpringApplication.run(com.ai.agenticrag.AgenticRagApplication.class)) {
            var ingest = ctx.getBean(IngestService.class);
            var tika = new Tika();

            File[] files = folder.listFiles();
            if (files == null) return;

            // Iterates files; extracts text; persists document
            for (File f : files) {
                if (f.isDirectory()) continue;

                String text;
                try {
                    text = tika.parseToString(f);
                } catch (Exception ex) {
                    System.err.println("Skip (parse failed): " + f.getName() + " :: " + ex.getMessage());
                    continue;
                }

                if (text == null || text.trim().isEmpty()) continue;

                var res = ingest.ingestText(
                        "file",
                        f.getName(),
                        text,
                        null,      // logicalId (null = auto-generate or upsert by title)
                        true,       // upsertBySourceTitle
                        null
                );

                long id = res.documentId();
                System.out.println("✅ Ingested " + f.getName() + " -> documentId=" + id);
            }
        }

        System.exit(0);
    }
}
