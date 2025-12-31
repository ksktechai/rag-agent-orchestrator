package com.ai.agenticrag.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Component
public class RequestResponseLoggingFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestResponseLoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long start = System.currentTimeMillis();

        ServerHttpRequest req = exchange.getRequest();
        String method = req.getMethod() == null ? "?" : req.getMethod().name();
        String path = req.getURI().getPath();
        String query = decode(req.getURI().getRawQuery());

        log.info("API INCOMING [{} {}] query={}", method, path, query);

        return chain.filter(exchange)
                .doFinally(sig -> {
                    long ms = System.currentTimeMillis() - start;
                    Integer status = exchange.getResponse().getStatusCode() == null
                            ? null
                            : exchange.getResponse().getStatusCode().value();
                    log.info("API RESPONSE  [{} {}] status={} timeMs={}", method, path, status, ms);
                });
    }

    private String decode(String query) {
        if (query == null) return "";
        return URLDecoder.decode(query, StandardCharsets.UTF_8);
    }


/**
 * This filter logs the details of each HTTP request and response, including their bodies,
 * while processing through the filter chain. It wraps the original request and response
 * using {@link ContentCachingRequestWrapper} and {@link ContentCachingResponseWrapper}
 * to allow multiple reads of their content for logging purposes without affecting
 * the actual processing by the downstream components.
 *
 * The class is implemented as a Spring-managed component and extends {@link OncePerRequestFilter}
 * to ensure it is executed only once per request within a single dispatch.
 *
 * Key features:
 * - Logs the HTTP method, URI, query parameters, and request body.
 * - Logs the HTTP status, response body, and the time taken to process the request.
 * - Limits the maximum cache size for request and response bodies to prevent memory issues
 *   with large payloads.
 *
 * Note: This filter must ensure responses are properly written back to the HTTP client
 * by invoking {@code copyBodyToResponse()} after logging the response.
 */
//@Component
//public class RequestResponseLoggingFilter extends OncePerRequestFilter {
//
//    private static final Logger logger = LoggerFactory.getLogger(RequestResponseLoggingFilter.class);
//
//    /**
//     * Filter that logs every API request and response body.
//     * Wrapping requests/responses is necessary to read the body multiple times (for logging)
//     * without consuming it for the actual endpoint.
//     * Limits cache to 10MB to avoid memory issues with huge uploads (though we prefer small chat messages)
//     *
//     * @param request     The request to process
//     * @param response    The response associated with the request
//     * @param filterChain Provides access to the next filter in the chain for this filter to pass the request and response
//     *                    to for further processing
//     * @throws ServletException Thrown if a servlet-specific error occurs during the processing of the request or response
//     * @throws IOException      Thrown if an I/O error occurs during the processing of the request or response
//     */
//    @Override
//    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
//            throws ServletException, IOException {
//
//        // Use cached wrappers so we can read the body multiple times (for logging)
//        // without consuming it for the actual endpoint.
//        // Limit cache to 10MB to avoid memory issues with huge uploads (though we
//        // prefer small chat messages)
//        // Limit cache to 10MB to avoid memory issues with huge uploads
//        ContentCachingRequestWrapper reqWrapper = new ContentCachingRequestWrapper(request, 10000);
//        ContentCachingResponseWrapper respWrapper = new ContentCachingResponseWrapper(response);
//
//        logger.info(
//                "API INCOMING [{} {}] query={} params={}",
//                request.getMethod(),
//                request.getRequestURI(),
//                decodeQueryString(request.getQueryString()),
//                formatParams(request)
//        );
//
//        long startTime = System.currentTimeMillis();
//        try {
//            // Proceed with the chain (this executes the controller)
//            filterChain.doFilter(reqWrapper, respWrapper);
//        } finally {
//            long duration = System.currentTimeMillis() - startTime;
//
//            // Log Request
//            String requestBody = new String(reqWrapper.getContentAsByteArray(), StandardCharsets.UTF_8);
//            // Replace newlines to keep logs clean-ish? Or keep them for readability. Let's
//            // simple trim.
//            logger.info("API REQUEST [{} {}] Body: {}", request.getMethod(), request.getRequestURI(), requestBody);
//
//            // Log Response
//            String responseBody = new String(respWrapper.getContentAsByteArray(), StandardCharsets.UTF_8);
//            logger.info("API RESPONSE [{} {}] Status: {} Time: {} ms Body: {}",
//                    request.getMethod(), request.getRequestURI(), response.getStatus(), duration, responseBody);
//
//            // IMPORTANT: Copy content back to the original response so the client receives
//            // it!
//            respWrapper.copyBodyToResponse();
//        }
//    }
//
//    /**
//     * Decode a query string, handling null, and decoding with UTF-8.
//     *
//     * @param query The query string to decode
//     * @return The decoded query string, or an empty string if the input is null
//     */
//    private String decodeQueryString(String query) {
//        if (query == null) return "";
//        return URLDecoder.decode(query, StandardCharsets.UTF_8);
//    }
//
//    /**
//     * Format the request parameters into a string representation.
//     *
//     * @param request The request to format parameters for
//     * @return A string representation of the request parameters
//     */
//    private String formatParams(HttpServletRequest request) {
//        Map<String, String[]> params = request.getParameterMap();
//        if (params.isEmpty()) return "{}";
//
//        return params.entrySet().stream()
//                .map(e -> e.getKey() + "=" + String.join(",", e.getValue()))
//                .collect(Collectors.joining(", ", "{", "}"));
//    }
}
