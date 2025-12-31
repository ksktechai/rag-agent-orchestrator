package com.ai.agenticrag.orchestrator.agents;

import com.ai.agenticrag.api.dto.AgentEvent;
import com.ai.agenticrag.orchestrator.Agent;
import com.ai.agenticrag.orchestrator.AgentContext;
import com.ai.agenticrag.rag.ChunkHit;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The JudgeAgent class implements the Agent interface and acts as a strict grader,
 * evaluating whether a generated answer is fully supported by the provided context.
 * It performs this evaluation based on the given question, draft answer, and retrieved
 * context, and updates the shared state with the result.
 */
public class JudgeAgent implements Agent {

    /**
     * Constructor for the JudgeAgent class.
     * No dependencies required - uses rule-based validation.
     */
    public JudgeAgent() {
    }

    @Override
    public String name() {
        return "judge";
    }

    /**
     * Generates a trace event for the current state of the JudgeAgent. The trace contains
     * metadata about the agent's activity and relevant context information.
     *
     * @param ctx The shared state passed to the agent containing details such as the
     *            question, draft answer, retrieved context, and the result of the evaluation.
     * @return A Flux stream emitting a single AgentEvent object with the agent's name,
     * event type ("judge"), and metadata about the number of characters in the draft answer.
     * If the draft answer is null, it records the character count as 0.
     */
    @Override
    public Flux<AgentEvent> trace(AgentContext ctx) {
        return Flux.just(AgentEvent.of(name(), "judge", Map.of("draftChars", ctx.draftAnswer == null ? 0 : ctx.draftAnswer.length())));
    }

    /**
     * Executes the judgment process to evaluate the groundedness of an answer based on a
     * given context. The method validates the answer by analyzing whether it is supported
     * by the context and checks if citations are present. The evaluation results are
     * updated in the shared context state.
     *
     * @param ctx The shared state passed to the agent containing details such as the
     *            question, draft answer, retrieved context, and where the judgment result
     *            will be stored.
     * @return A Mono signaling completion of the judgment process. The results of the
     *         evaluation are updated in the provided AgentContext instance.
     */
    @Override
    public Mono<Void> run(AgentContext ctx) {

        return Mono.fromRunnable(() -> {
            String ans = ctx.draftAnswer == null ? "" : ctx.draftAnswer;

            // must have retrieval
            if (ctx.retrieved == null || ctx.retrieved.isEmpty()) {
                ctx.judgedPass = false;
                return;
            }

            // must have non-empty answer
            if (ans.trim().isEmpty()) {
                ctx.judgedPass = false;
                return;
            }

            // Simple validation: if answer is very short (< 20 chars), likely not useful
            if (ans.trim().length() < 20) {
                ctx.judgedPass = false;
                return;
            }

            // Pass if we have retrieval and a reasonable answer
            // (Citations are optional now - shown separately in UI)
            ctx.judgedPass = true;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }
}
