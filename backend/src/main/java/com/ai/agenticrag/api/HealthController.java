package com.ai.agenticrag.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controller providing endpoints for application health checks.
 */
@RestController
public class HealthController {

    /**
     * Provides a health check endpoint.
     *
     * @return a map containing the key "ok" with a value of true, indicating that the service is operational
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("ok", true);
    }
}
