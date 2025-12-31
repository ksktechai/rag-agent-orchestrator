package com.ai.agenticrag.orchestrator.agents;

import com.ai.agenticrag.api.dto.AgentEvent;
import com.ai.agenticrag.orchestrator.Agent;
import com.ai.agenticrag.orchestrator.AgentContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Locale;
import java.util.Map;

/**
 * The RouterAgent class implements the Agent interface and is responsible for
 * determining if a given question requires information retrieval based on its content.
 * It uses specific keywords and heuristics to make this decision, updating the shared
 * state accordingly.
 */
public class RouterAgent implements Agent {
    @Override
    public String name() {
        return "router";
    }

    /**
     * Generates a trace event for the RouterAgent indicating whether the current question
     * requires retrieval of additional information. The event contains metadata including
     * the agent's name, event type ("route"), and a Boolean flag for the retrieval requirement.
     *
     * @param ctx The shared state passed to the agent containing the question and the
     *            assessed need for additional information retrieval.
     * @return A Flux stream emitting a single AgentEvent object with the agent's name,
     * event type, timestamp, and metadata about whether information retrieval is required.
     */
    @Override
    public Flux<AgentEvent> trace(AgentContext ctx) {
        return Flux.just(AgentEvent.of(name(), "route", Map.of("needsRetrieval", ctx.needsRetrieval)));
    }

    /**
     * Analyzes the given question in the shared {@code AgentContext} to determine if
     * additional information retrieval is required based on the content or length
     * of the question. Updates the {@code needsRetrieval} flag in the context accordingly.
     *
     * @param ctx The mutable shared state containing the question and other associated
     *            data. The method evaluates the question and updates the {@code needsRetrieval}
     *            decision flag within this context.
     * @return A reactive {@code Mono<Void>} that completes after processing the question
     * and updating the shared state.
     */
    @Override
    public Mono<Void> run(AgentContext ctx) {
        String s = ctx.question.toLowerCase(Locale.ROOT);
        ctx.needsRetrieval = !ctx.question.matches("(?i)hello|hi|who are you");
//        ctx.needsRetrieval =
//                s.matches(".*\b(according to|from the document|pdf|report|table|figure|cite|source|evidence|compare|calculate|how many)\b.*")
//                        || s.length() > 60;
        return Mono.empty();
    }
}
