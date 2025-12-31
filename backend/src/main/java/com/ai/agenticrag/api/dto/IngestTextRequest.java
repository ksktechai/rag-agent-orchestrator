package com.ai.agenticrag.api.dto;

public record IngestTextRequest(
        String source,
        String title,
        String text,
        String logicalId,
        Boolean upsertBySourceTitle
) {}
