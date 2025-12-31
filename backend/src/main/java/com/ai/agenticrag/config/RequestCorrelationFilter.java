package com.ai.agenticrag.config;

import org.slf4j.MDC;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

/**
 * A servlet filter that ensures every request has a unique correlation ID. This ID is used for
 * traceability across different parts of a distributed application, enabling better debugging
 * and monitoring by correlating logs for a specific request.
 * <p>
 * If the request does not already contain the correlation ID in the header "X-Correlation-Id",
 * the filter generates a new UUID and attaches it. The correlation ID is stored in the Mapped
 * Diagnostic Context (MDC), which allows it to be included in logging output.
 * <p>
 * The filter ensures that the MDC is cleaned up after the request is processed to prevent data
 * leakage between threads.
 */
@Component
public class RequestCorrelationFilter implements WebFilter {

    public static final String HEADER = "X-Correlation-Id";
    private static final String MDC_KEY = "correlationId";

//    /**
//     * Filter to add a unique correlation ID to each request for logging and tracing purposes.
//     *
//     * @param request  The request to process
//     * @param response The response associated with the request
//     * @param chain    Provides access to the next filter in the chain for this filter to pass the request and response
//     *                 to for further processing
//     * @throws IOException      Thrown if an I/O error occurs during the processing of the request or response
//     * @throws ServletException Thrown if a servlet-specific error occurs during the processing of the request or response
//     */
//    @Override
//    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
//            throws IOException, ServletException {
//        HttpServletRequest req = (HttpServletRequest) request;
//        String cid = req.getHeader(HEADER);
//        if (cid == null || cid.isBlank()) cid = UUID.randomUUID().toString();
//        MDC.put("correlationId", cid);
//        try {
//            chain.doFilter(request, response);
//        } finally {
//            MDC.remove("correlationId");
//        }
//    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest req = exchange.getRequest();

        String corrId = Optional.ofNullable(req.getHeaders().getFirst(HEADER))
                .filter(s -> !s.isBlank())
                .orElse(UUID.randomUUID().toString());

        // Add it to response header too
        exchange.getResponse().getHeaders().set(HEADER, corrId);

        // Put in MDC for logs (best-effort in reactive flows)
        MDC.put(MDC_KEY, corrId);

        return chain.filter(exchange)
                .doFinally(sig -> MDC.remove(MDC_KEY));
    }
}
