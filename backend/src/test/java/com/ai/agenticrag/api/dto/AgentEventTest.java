package com.ai.agenticrag.api.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AgentEvent record.
 */
class AgentEventTest {

    @Test
    void of_createsEventWithCurrentTimestamp() {
        Instant before = Instant.now();
        AgentEvent event = AgentEvent.of("testAgent", "testType", Map.of("key", "value"));
        Instant after = Instant.now();

        assertEquals("testAgent", event.agent());
        assertEquals("testType", event.type());
        assertEquals(Map.of("key", "value"), event.data());
        assertNotNull(event.ts());
        assertFalse(event.ts().isBefore(before));
        assertFalse(event.ts().isAfter(after));
    }

    @Test
    void of_createsEventWithEmptyData() {
        AgentEvent event = AgentEvent.of("agent", "type", Map.of());

        assertEquals("agent", event.agent());
        assertEquals("type", event.type());
        assertTrue(event.data().isEmpty());
    }

    @Test
    void of_createsEventWithComplexData() {
        Map<String, Object> data = Map.of(
                "intValue", 42,
                "stringValue", "hello",
                "listValue", List.of(1, 2, 3));
        AgentEvent event = AgentEvent.of("complexAgent", "complex", data);

        assertEquals(42, event.data().get("intValue"));
        assertEquals("hello", event.data().get("stringValue"));
        assertEquals(List.of(1, 2, 3), event.data().get("listValue"));
    }

    @Test
    void recordEquality() {
        Instant ts = Instant.now();
        AgentEvent event1 = new AgentEvent("agent", "type", ts, Map.of("key", "value"));
        AgentEvent event2 = new AgentEvent("agent", "type", ts, Map.of("key", "value"));

        assertEquals(event1, event2);
        assertEquals(event1.hashCode(), event2.hashCode());
    }

    @Test
    void toString_containsAllFields() {
        AgentEvent event = AgentEvent.of("testAgent", "testType", Map.of("key", "value"));
        String str = event.toString();

        assertTrue(str.contains("testAgent"));
        assertTrue(str.contains("testType"));
        assertTrue(str.contains("key"));
    }
}
