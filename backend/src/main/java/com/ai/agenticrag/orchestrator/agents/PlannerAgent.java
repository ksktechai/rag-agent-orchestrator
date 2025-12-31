package com.ai.agenticrag.orchestrator.agents;

import com.ai.agenticrag.api.dto.AgentEvent;
import com.ai.agenticrag.orchestrator.Agent;
import com.ai.agenticrag.orchestrator.AgentContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * The PlannerAgent class implements the Agent interface and is responsible
 * for orchestrating a high-level plan to process a given query. It defines
 * a trace of planned steps that the system should take and does not perform
 * any additional execution itself.
 */
public class PlannerAgent implements Agent {
    @Override public String name() { return "planner"; }

    /**
     * Generates a trace event for the current state of the PlannerAgent. The trace event is used
     * to document the steps involved in the planning process for handling a query. This method
     * does not execute the plan but only describes the outlined steps.
     *
     * @param ctx The shared state passed to the agent, containing details about the query and
     *            other context information relevant to processing the plan.
     * @return A Flux stream emitting a single AgentEvent object. The event captures the agent's
     * name, event type ("plan"), and metadata about the sequence of planned steps to be executed.
     */
    @Override
    public Flux<AgentEvent> trace(AgentContext ctx) {
        return Flux.just(AgentEvent.of(name(), "plan", Map.of("steps", List.of(
                "route",
                "parallel retrieve (multi-agent)",
                "synthesize grounded answer",
                "judge groundedness",
                "rewrite query + retry (max 2)"
        ))));
    }

    /**
     * Executes the high-level logic of the agent, potentially performing actions
     * based on the provided shared state. This method does not modify the state
     * or carry out any significant operations for the PlannerAgent; it serves
     * as a no-op placeholder.
     *
     * @param ctx The mutable shared state passed between agents, containing
     *            details such as the query, retrieved context, draft answer,
     *            and other relevant information.
     * @return A reactive {@code Mono<Void>} that completes immediately, indicating
     *         that no operations were performed.
     */
    @Override
    public Mono<Void> run(AgentContext ctx) { return Mono.empty(); }
}
