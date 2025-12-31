package com.ai.agenticrag.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HealthController.
 */
class HealthControllerTest {

    @Test
    void health_returnsOkTrue() {
        HealthController controller = new HealthController();

        var result = controller.health();

        assertNotNull(result);
        assertEquals(true, result.get("ok"));
    }

    @Test
    void health_returnsSingleEntry() {
        HealthController controller = new HealthController();

        var result = controller.health();

        assertEquals(1, result.size());
        assertTrue(result.containsKey("ok"));
    }

    @Test
    void health_consistentResults() {
        HealthController controller = new HealthController();

        var result1 = controller.health();
        var result2 = controller.health();

        assertEquals(result1, result2);
    }
}
