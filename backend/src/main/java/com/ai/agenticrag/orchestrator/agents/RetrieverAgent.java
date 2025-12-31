package com.ai.agenticrag.orchestrator.agents;

import com.ai.agenticrag.api.dto.AgentEvent;
import com.ai.agenticrag.orchestrator.Agent;
import com.ai.agenticrag.orchestrator.AgentContext;
import com.ai.agenticrag.rag.VectorStoreService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * The RetrieverAgent class is a concrete implementation of the Agent interface.
 * It is responsible for retrieving relevant chunks of information from a vector store
 * based on a given query and a specified number of results (k). The agent integrates
 * with a VectorStoreService to perform the retrieval process and keeps track of its
 * operations by emitting traceable events.
 */
public class RetrieverAgent implements Agent {
    private final VectorStoreService store;
    private final int k;
    private final String id;

    /**
     * Constructs a new instance of the RetrieverAgent.
     *
     * @param id    the unique identifier for the agent
     * @param store the vector store service used for searching and retrieving information
     * @param k     the number of most relevant results to be retrieved
     */
    public RetrieverAgent(String id, VectorStoreService store, int k) {
        this.id = id;
        this.store = store;
        this.k = k;
    }

    @Override
    public String name() {
        return "retriever-" + id;
    }

    /**
     * Generates a trace event for the current state of the {@code RetrieverAgent}. The trace contains
     * metadata about the agent's activity and relevant context information, such as the retrieval query
     * and the number of results requested.
     *
     * @param ctx The shared state provided to the agent, containing details such as the query for
     *            retrieving information and associated metadata.
     * @return A Flux stream emitting a single {@code AgentEvent} object with the agent's name,
     * event type ("retrieve"), and metadata about the query and the number of results requested.
     */
    @Override
    public Flux<AgentEvent> trace(AgentContext ctx) {
        return Flux.just(AgentEvent.of(name(), "retrieve", Map.of("k", k, "query", ctx.query)));
    }

    /**
     * Executes the retrieval process for finding relevant information chunks based on the query
     * and updates the shared context state with the retrieved results. The method uses the
     * search functionality of the VectorStoreService to fetch the most relevant chunks.
     *
     * @param ctx The mutable shared state containing the retrieval query, results, and other
     *            related contextual information. The retrieved chunks will be added to the
     *            {@code ctx.retrieved} list.
     * @return A reactive {@code Mono<Void>} that signals completion once the retrieval process
     * and context update are finalized.
     */
    @Override
    public Mono<Void> run(AgentContext ctx) {
        String searchQuery =
                (ctx.query != null && !ctx.query.isBlank())
                        ? ctx.query
                        : ctx.question;
        return store.search(searchQuery, k)
                .doOnNext(ctx.retrieved::addAll)
                .then();
    }
}
