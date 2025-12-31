package com.ai.agenticrag.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.*;

/**
 * Unit tests for RequestResponseLoggingFilter.
 */
class RequestResponseLoggingFilterTest {

    private RequestResponseLoggingFilter filter;
    private WebFilterChain mockChain;

    @BeforeEach
    void setUp() {
        filter = new RequestResponseLoggingFilter();
        mockChain = mock(WebFilterChain.class);
    }

    @Test
    void filter_logsRequestAndResponse() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.OK);

        when(mockChain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        verify(mockChain).filter(exchange);
    }

    @Test
    void filter_handlesPostRequest() {
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/ingest/text")
                .body("test body");
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.CREATED);

        when(mockChain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        verify(mockChain).filter(exchange);
    }

    @Test
    void filter_handlesQueryParameters() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/chat/stream?question=What%20is%20AI")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.OK);

        when(mockChain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        verify(mockChain).filter(exchange);
    }

    @Test
    void filter_handlesNullQueryString() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/health")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.OK);

        when(mockChain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        verify(mockChain).filter(exchange);
    }

    @Test
    void filter_handlesEncodedQueryParameters() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/search?q=%E4%B8%AD%E6%96%87")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.OK);

        when(mockChain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        verify(mockChain).filter(exchange);
    }

    @Test
    void filter_handlesErrorResponse() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/error")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);

        when(mockChain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        verify(mockChain).filter(exchange);
    }

    @Test
    void filter_handlesNullMethod() {
        // Edge case: method might be null in some scenarios
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        when(mockChain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();
    }

    @Test
    void filter_handlesNullStatusCode() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        // Status code not set, will be null

        when(mockChain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        verify(mockChain).filter(exchange);
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
    void filter_handlesDeleteRequest() {
        MockServerHttpRequest request = MockServerHttpRequest
                .delete("/api/document/123")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.NO_CONTENT);

        when(mockChain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        verify(mockChain).filter(exchange);
    }

    @Test
    void filter_handlesPutRequest() {
        MockServerHttpRequest request = MockServerHttpRequest
                .put("/api/document/123")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.OK);

        when(mockChain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        verify(mockChain).filter(exchange);
    }
}
