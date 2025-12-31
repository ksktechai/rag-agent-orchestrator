package com.ai.agenticrag.api.dto;

import java.util.List;

public record FinalAnswer(String answer, List<Citation> citations) {
    public record Citation(long chunkId, String title, int chunkIndex, double score) {}
}
