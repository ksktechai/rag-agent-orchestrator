package com.ai.agenticrag.orchestrator.agents;

import com.ai.agenticrag.api.dto.AgentEvent;
import com.ai.agenticrag.orchestrator.Agent;
import com.ai.agenticrag.orchestrator.AgentContext;
import com.ai.agenticrag.orchestrator.OllamaHttpClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * The QueryRewriteAgent class implements the Agent interface and is responsible for improving
 * a user's search query to enhance the retrieval of relevant information. It operates by rewriting
 * the query based on the context provided, including hints and feedback from failed responses.
 */
public class QueryRewriteAgent implements Agent {
    private final OllamaHttpClient ollamaClient;

    /**
     * Constructor for the QueryRewriteAgent class.
     *
     * @param ollamaClient An instance of OllamaHttpClient that facilitates communication and provides
     *             the capability to process and generate improved search queries by
     *             exchanging information with an external chat-based service.
     */
    public QueryRewriteAgent(OllamaHttpClient ollamaClient) {
        this.ollamaClient = ollamaClient;
    }

    @Override
    public String name() {
        return "query-rewriter";
    }

    /**
     * Generates a trace event for the current state of the QueryRewriteAgent. The trace contains
     * metadata about the agent's activity and relevant context information, specifically focusing
     * on query rewriting.
     *
     * @param ctx The shared state passed to the agent containing details such as the user's
     *            question, the current query, and other contextual information used during
     *            query rewriting.
     * @return A Flux stream emitting a single AgentEvent object. The event contains the agent's name,
     * the event type ("rewrite"), and metadata about the previous query found in the context.
     */
    @Override
    public Flux<AgentEvent> trace(AgentContext ctx) {
        return Flux.just(AgentEvent.of(name(), "rewrite", Map.of("prevQuery", ctx.query)));
    }

    /**
     * Executes the query rewriting process for the given agent context. This method generates
     * a better search query based on the user's question, hints derived from retrieved content,
     * and feedback from failed answers. The improved query is then applied back to the context.
     *
     * @param ctx The shared mutable context object containing details such as the user's
     *            initial question, the current query to improve, retrieved search results,
     *            and any draft answers. This method updates the `query` field in the context
     *            with the rewritten query if applicable.
     * @return A Mono signaling completion of the query rewriting process. The Mono completes
     * with no emitted value when all steps and modifications to the context are finished.
     */
    @Override
    public Mono<Void> run(AgentContext ctx) {
        String hints = ctx.retrieved.stream().limit(5)
                .map(h -> h.title() + " :: " + h.content())
                .collect(Collectors.joining("\n---\n"));

        String system =
                "Rewrite the user's question into a better search query for retrieving relevant chunks.\n" +
                        "Return ONLY JSON: {\"query\":\"...\"}.\n";
        String user = "QUESTION:\n" + ctx.question + "\n\nFAILED_ANSWER:\n" + ctx.draftAnswer + "\n\nHINTS:\n" + hints;

        return Mono.fromCallable(() -> ollamaClient.chat(system, user))
                .subscribeOn(Schedulers.boundedElastic())
                .map(this::extractQuery)
                .doOnNext(q -> ctx.query = (q == null || q.isBlank()) ? ctx.query : q)
                .then();
    }

    /**
     * Extracts the value associated with the "query" key from the provided JSON string. If the key is not present
     * or its value is not properly enclosed in double quotes, the method returns the original JSON string.
     *
     * @param json The input JSON string from which the query value is to be extracted.
     * @return The extracted query value if present and properly formatted; otherwise, the original JSON string.
     */
    private String extractQuery(String json) {
        int i = json.indexOf("\"query\"");
        if (i < 0) return json;
        int c = json.indexOf(":", i);
        int q1 = json.indexOf("\"", c);
        int q2 = json.indexOf("\"", q1 + 1);
        if (q1 >= 0 && q2 > q1) return json.substring(q1 + 1, q2);
        return json;
    }
}
