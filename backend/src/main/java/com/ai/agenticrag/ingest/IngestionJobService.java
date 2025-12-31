package com.ai.agenticrag.ingest;

import com.ai.agenticrag.api.dto.IngestProgressEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This service manages ingestion jobs, allowing for the creation, progress tracking,
 * and completion of jobs while providing a stream of progress events to consumers.
 * The class uses reactive programming constructs to ensure non-blocking and efficient
 * event delivery.
 * <p>
 * Responsibilities of this service include:
 * 1. Creating new ingestion jobs and generating their unique identifiers.
 * 2. Emitting progress events for ongoing ingestion jobs.
 * 3. Allowing clients to stream real-time progress events for specific jobs.
 * 4. Marking ingestion jobs as complete when processing finishes.
 * <p>
 * Reactive programming is leveraged via Reactor's {@code Sinks.Many} and {@code Flux}
 * to handle job events and streams.
 */
@Service
public class IngestionJobService {
    private final Map<String, Sinks.Many<IngestProgressEvent>> sinks = new ConcurrentHashMap<>();
    private final Map<String, IngestProgressEvent> last = new ConcurrentHashMap<>();

    /**
     * Creates a new ingestion job and registers it in the system.
     * A unique identifier is generated for the job, and it is associated with a
     * reactive sink to allow for real-time streaming of progress events.
     *
     * @return a unique string identifier for the newly created ingestion job
     */
    public String newJob() {
        String id = UUID.randomUUID().toString();
        sinks.put(id, Sinks.many().multicast().onBackpressureBuffer());
        return id;
    }

    /**
     * Emits an {@link IngestProgressEvent} to the reactive sink associated with its job ID.
     * This method updates the latest progress event for the job, emits the event to its
     * respective sink if it exists, and completes the job's sink if the event marks the
     * job as done.
     *
     * @param ev the progress event to emit, containing details such as job ID, stage,
     *           progress metrics, a message, and completion status
     */
    public void emit(IngestProgressEvent ev) {
        last.put(ev.jobId(), ev);
        Sinks.Many<IngestProgressEvent> sink = sinks.get(ev.jobId());
        if (sink != null) sink.tryEmitNext(ev);
        if (ev.done()) complete(ev.jobId());
    }

    /**
     * Marks the ingestion job with the specified job ID as complete and closes its
     * associated event stream. If a reactive sink exists for the given job ID, it is
     * removed from the internal registry and a completion signal is emitted to any
     * subscribers of the sink.
     *
     * @param jobId the unique identifier of the ingestion job to mark as complete
     */
    public void complete(String jobId) {
        Sinks.Many<IngestProgressEvent> sink = sinks.remove(jobId);
        if (sink != null) sink.tryEmitComplete();
    }

    /**
     * Provides a reactive stream of {@link IngestProgressEvent} for the specified ingestion job.
     * The method retrieves the last emitted progress event for the job (if available) and
     * concatenates it with a live stream of events from the reactive sink associated with the job.
     * If no events are available for the job, an empty stream is returned.
     *
     * @param jobId the unique identifier of the ingestion job for which to stream progress events
     * @return a {@link Flux} of {@link IngestProgressEvent} representing the progress events
     * for the specified ingestion job
     */
    public Flux<IngestProgressEvent> stream(String jobId) {
        IngestProgressEvent lastEv = last.get(jobId);
        Flux<IngestProgressEvent> replay = lastEv == null ? Flux.empty() : Flux.just(lastEv);
        Sinks.Many<IngestProgressEvent> sink = sinks.get(jobId);
        if (sink == null) return replay;
        return Flux.concat(replay, sink.asFlux());
    }
}
