package com.ai.agenticrag.api.dto;

import java.time.Instant;

public record IngestProgressEvent(
        String jobId,
        String stage,
        int current,
        int total,
        String message,
        Long documentId,
        String logicalId,
        Integer version,
        boolean done,
        String error,
        Instant ts
) {
    public static IngestProgressEvent of(String jobId, String stage, int current, int total, String message) {
        return new IngestProgressEvent(jobId, stage, current, total, message, null, null, null, false, null, Instant.now());
    }

    public static IngestProgressEvent done(String jobId, Long documentId, String logicalId, Integer version, String message) {
        return new IngestProgressEvent(jobId, "done", 1, 1, message, documentId, logicalId, version, true, null, Instant.now());
    }

    public static IngestProgressEvent error(String jobId, String message, String error) {
        return new IngestProgressEvent(jobId, "error", 0, 0, message, null, null, null, true, error, Instant.now());
    }
}
