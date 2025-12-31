package com.ai.agenticrag.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RequestCorrelationFilter.
 */
class RequestCorrelationFilterTest {

    private RequestCorrelationFilter filter;
    private WebFilterChain mockChain;

    @BeforeEach
    void setUp() {
        filter = new RequestCorrelationFilter();
        mockChain = mock(WebFilterChain.class);
    }

    @Test
    void filter_addsCorrelationIdToResponse() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        when(mockChain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        String correlationId = exchange.getResponse().getHeaders().getFirst("X-Correlation-Id");
        assertNotNull(correlationId);
        assertFalse(correlationId.isBlank());
    }

    @Test
    void filter_generatesUUIDFormatCorrelationId() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        when(mockChain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        String correlationId = exchange.getResponse().getHeaders().getFirst("X-Correlation-Id");
        // UUID format check
        assertTrue(correlationId.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"));
    }

    @Test
    void filter_preservesExistingCorrelationId() {
        String existingId = "existing-correlation-id-123";
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .header("X-Correlation-Id", existingId)
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        when(mockChain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        String correlationId = exchange.getResponse().getHeaders().getFirst("X-Correlation-Id");
        assertEquals(existingId, correlationId);
    }

    @Test
    void filter_generatesNewIdWhenExistingIsBlank() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .header("X-Correlation-Id", "   ")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        when(mockChain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        String correlationId = exchange.getResponse().getHeaders().getFirst("X-Correlation-Id");
        assertNotNull(correlationId);
        assertNotEquals("   ", correlationId);
    }

    @Test
    void filter_generatesNewIdWhenExistingIsEmpty() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .header("X-Correlation-Id", "")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        when(mockChain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        String correlationId = exchange.getResponse().getHeaders().getFirst("X-Correlation-Id");
        assertNotNull(correlationId);
        assertFalse(correlationId.isEmpty());
    }

    @Test
    void filter_chainsCorrectly() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        when(mockChain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        verify(mockChain, times(1)).filter(exchange);
    }

    @Test
    void filter_cleansMDCAfterCompletion() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        when(mockChain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        // MDC should be cleaned up after the filter completes
        assertNull(MDC.get("correlationId"));
    }

    @Test
    void filter_handlesPostRequest() {
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/ingest/text")
                .header("X-Correlation-Id", "post-request-id")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        when(mockChain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        assertEquals("post-request-id", exchange.getResponse().getHeaders().getFirst("X-Correlation-Id"));
    }

    @Test
    void filter_uniqueIdsForDifferentRequests() {
        MockServerHttpRequest request1 = MockServerHttpRequest.get("/api/test1").build();
        MockServerHttpRequest request2 = MockServerHttpRequest.get("/api/test2").build();
        ServerWebExchange exchange1 = MockServerWebExchange.from(request1);
        ServerWebExchange exchange2 = MockServerWebExchange.from(request2);

        when(mockChain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange1, mockChain))
                .verifyComplete();
        StepVerifier.create(filter.filter(exchange2, mockChain))
                .verifyComplete();

        String id1 = exchange1.getResponse().getHeaders().getFirst("X-Correlation-Id");
        String id2 = exchange2.getResponse().getHeaders().getFirst("X-Correlation-Id");

        assertNotEquals(id1, id2);
    }

    @Test
    void HEADER_constant_hasCorrectValue() {
        assertEquals("X-Correlation-Id", RequestCorrelationFilter.HEADER);
    }
}
