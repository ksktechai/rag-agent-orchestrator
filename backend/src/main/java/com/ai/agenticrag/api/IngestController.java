package com.ai.agenticrag.api;

import com.ai.agenticrag.api.dto.IngestJobStartResponse;
import com.ai.agenticrag.api.dto.IngestProgressEvent;
import com.ai.agenticrag.api.dto.IngestTextRequest;
import com.ai.agenticrag.api.dto.ReembedRequest;
import com.ai.agenticrag.ingest.IngestService;
import com.ai.agenticrag.ingest.IngestionJobService;
import org.apache.tika.Tika;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * The {@code IngestController} class provides REST endpoints for handling text and file ingestion,
 * monitoring ingestion progress, and reembedding data chunks. It interacts with the {@code IngestService}
 * for ingestion operations and the {@code IngestionJobService} for managing asynchronous ingestion jobs.
 * <p>
 * This controller supports operations like:
 * - Ingesting text and returning its metadata
 * - Ingesting files asynchronously and monitoring progress
 * - Streaming progress events for long-running ingestion jobs
 * - Reembedding data chunks and returning the updated count
 */
@RestController
@RequestMapping("/api/ingest")
public class IngestController {
    private final IngestService ingestService;
    private final IngestionJobService jobs;
    private final Tika tika = new Tika();

    /**
     * Constructs an instance of {@code IngestController} to provide REST APIs for ingestion, progress monitoring,
     * and reembedding functionalities. This controller handles the orchestration of ingestion-related operations
     * by interacting with the {@code IngestService} for core ingestion logic and the {@code IngestionJobService}
     * for managing asynchronous ingestion job tracking and progress reporting.
     *
     * @param ingestService the service responsible for handling text ingestion, embedding, and storage operations
     * @param jobs          the service responsible for managing ingestion jobs, emitting progress events, and tracking the
     *                      state of asynchronous ingestion processes
     */
    public IngestController(IngestService ingestService, IngestionJobService jobs) {
        this.ingestService = ingestService;
        this.jobs = jobs;
    }

    /**
     * Handles the ingestion of text input provided in the request body and returns metadata about the ingested document.
     *
     * @param req the request body containing the source, title, text, logical ID, and an optional upsert flag
     * @return a map containing the metadata of the ingested document, including "documentId", "logicalId", and "version"
     */
    @PostMapping("/text")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> ingestText(@RequestBody IngestTextRequest req) {
        boolean upsert = req.upsertBySourceTitle() == null || req.upsertBySourceTitle();
        var res = ingestService.ingestText(req.source(), req.title(), req.text(), req.logicalId(), upsert, null);
        return Map.of("documentId", res.documentId(), "logicalId", res.logicalId(), "version", res.version());
    }

    /**
     * Handles the ingestion of a file provided in a multipart/form-data request. The method processes
     * the file asynchronously, parses its content, chunks and embeds the data, and stores the resulting
     * information. Ingestion progress is monitored via events, and an appropriate response with a job ID
     * is returned for tracking the ingestion process.
     *
     * @param file   the file to be ingested, provided as a part of the multipart request
     * @param source an optional source identifier for the file; if not provided or blank, defaults to "file"
     * @return a {@code Mono<IngestJobStartResponse>} containing the job ID for tracking the ingestion
     */
    @PostMapping(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<IngestJobStartResponse> ingestFile(
            @RequestPart("file") FilePart file,
            @RequestPart(value = "source", required = false) String source
    ) {
        String jobId = jobs.newJob();
        String src = (source == null || source.isBlank()) ? "file" : source;
        String title = file.filename();

        jobs.emit(IngestProgressEvent.of(jobId, "received", 0, 1, "Upload received: " + title));

        // Parses file; emits progress; performs auditable ingestion
        Mono.fromCallable(() -> {
                    jobs.emit(IngestProgressEvent.of(jobId, "parse", 0, 1, "Parsing with Apache Tika"));
                    Path tmp = Files.createTempFile("agenticrag-", "-" + title);
                    file.transferTo(tmp).block();
                    String text = tika.parseToString(tmp.toFile());
                    Files.deleteIfExists(tmp);

                    if (text == null || text.trim().isEmpty()) {
                        throw new IllegalStateException("Parsed text is empty for " + title);
                    }

                    jobs.emit(IngestProgressEvent.of(jobId, "chunk_embed_store", 0, 1, "Chunking + embedding + storing"));
                    var res = ingestService.ingestText(src, title, text, null, true, jobId);
                    jobs.emit(IngestProgressEvent.done(jobId, res.documentId(), res.logicalId(), res.version(), "Ingestion complete"));
                    return true;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(ex -> {
                    jobs.emit(IngestProgressEvent.error(jobId, "Ingestion failed", ex.getMessage()));
                    return Mono.empty();
                })
                .subscribe();

        return Mono.just(new IngestJobStartResponse(jobId));
    }

    /**
     * Streams progress updates for an ongoing ingestion job identified by the provided job ID.
     * This method leverages Server-Sent Events (SSE) to emit real-time progress updates to the client.
     *
     * @param jobId the unique identifier of the ingestion job for which progress updates are requested
     * @return a {@code Flux<ServerSentEvent<IngestProgressEvent>>} where each event contains details
     * about the current state of the ingestion process
     */
    @GetMapping(value = "/progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<IngestProgressEvent>> progress(@RequestParam("jobId") String jobId) {
        return jobs.stream(jobId).map(ev -> ServerSentEvent.builder(ev).event("progress").build());
    }

    /**
     * Reembeds data chunks within the specified scope and using the given model. This operation updates
     * the embeddings of the selected data chunks and returns the count of updated chunks.
     *
     * @param req the {@code ReembedRequest} containing details about the scope and model to use for reembedding
     * @return a {@code Mono<Map<String, Integer>>} containing a map with a single entry, where the key is
     * "updatedChunks" and the value is the count of successfully reembedded chunks
     */
    @PostMapping(value = "/reembed", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Integer>> reembed(@RequestBody ReembedRequest req) {
        return Mono.fromCallable(() -> {
            int n = ingestService.reembed(req.scope(), req.model());
            return Map.of("updatedChunks", n);
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
