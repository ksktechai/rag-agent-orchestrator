package com.ai.agenticrag.api.dto;

import java.time.Instant;
import java.util.Map;

public record AgentEvent(String agent, String type, Instant ts, Map<String, Object> data) {
    public static AgentEvent of(String agent, String type, Map<String, Object> data) {
        return new AgentEvent(agent, type, Instant.now(), data);
    }
}
