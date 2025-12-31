package com.ai.agenticrag.orchestrator.agents;

import com.ai.agenticrag.api.dto.AgentEvent;
import com.ai.agenticrag.orchestrator.Agent;
import com.ai.agenticrag.orchestrator.AgentContext;
import com.ai.agenticrag.orchestrator.OllamaHttpClient;
import com.ai.agenticrag.rag.ChunkHit;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * The SynthesizerAgent class implements the Agent interface and serves as a context-aware
 * synthesizer that generates answers to questions based on retrieved information. It
 * constructs a grounded and evidence-supported answer by leveraging a predefined set of
 * formatting rules and constraints.
 */
public class SynthesizerAgent implements Agent {
    private final OllamaHttpClient ollamaClient;

    /**
     * Constructor for the SynthesizerAgent class.
     *
     * @param ollamaClient An instance of OllamaHttpClient used for generating synthesized answers to
     *             questions based on the provided context and predefined formatting rules.
     */
    public SynthesizerAgent(OllamaHttpClient ollamaClient) {
        this.ollamaClient = ollamaClient;
    }

    @Override
    public String name() {
        return "synthesizer";
    }

    /**
     * Generates a trace event for the current state of the SynthesizerAgent. The trace contains
     * metadata about the agent's activity and relevant context information.
     *
     * @param ctx The shared state passed to the agent containing details such as the
     *            question, retrieved context, and other metadata.
     *            In particular, the size of the retrieved context is used in this method.
     * @return A Flux stream emitting a single AgentEvent object with the agent's name,
     * event type ("synthesize"), and metadata about the number of retrieved context entries.
     */
    @Override
    public Flux<AgentEvent> trace(AgentContext ctx) {
        return Flux.just(AgentEvent.of(name(), "synthesize", Map.of("retrieved", ctx.retrieved.size())));
    }

    /**
     * Executes the agent's core functionality to generate a synthesized answer
     * based on the provided context and a predefined system prompt. This method
     * uses reactive programming to interact with an external chat service and
     * updates the shared state with the drafted answer.
     *
     * @param ctx The mutable shared state containing the question, retrieved
     *            context, and other metadata. The method uses the context to
     *            construct the prompt and updates the state with the generated
     *            answer.
     * @return A reactive {@code Mono<Void>} that completes after the answer is
     * synthesized and the shared state is updated with the result.
     */
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SynthesizerAgent.class);

    @Override
    public Mono<Void> run(AgentContext ctx) {
        // CRITICAL: Wrap ALL logic in Mono.defer to ensure it executes AFTER ctx.retrieved is populated
        return Mono.defer(() -> {
            log.info("SYNTHESIZER - retrieved chunks count: {}", ctx.retrieved.size());
            log.info("SYNTHESIZER - question: {}", ctx.question);

            String context = ctx.retrieved.stream()
                    .map(h -> "[chunk:" + h.chunkId() + "] " + h.content())
                    .collect(Collectors.joining("\n\n"));

            // Extract available chunk IDs to show in prompt
            String availableChunkIds = ctx.retrieved.stream()
                    .map(h -> String.valueOf(h.chunkId()))
                    .collect(Collectors.joining(", "));

            log.info("SYNTHESIZER - context length: {}", context.length());
            log.info("SYNTHESIZER - availableChunkIds: {}", availableChunkIds);

            String system = """
                    You are a grounded RAG answer synthesizer.

                    CRITICAL CITATION RULES:
                    - Each chunk in CONTEXT starts with [chunk:ID] where ID is a number.
                    - You MUST cite chunks using ONLY the exact IDs that appear in the CONTEXT.
                    - NEVER make up chunk IDs. NEVER use IDs that don't exist in the CONTEXT.
                    - Every factual sentence MUST include citations using the exact format [chunk:ID].
                    - If extracting data/numbers, cite the chunk where you found it.

                    OTHER RULES:
                    - Use ONLY information from CONTEXT. No outside knowledge.
                    - If CONTEXT has the answer, you MUST provide it (don't say "missing information").
                    - If the question asks for a table/trend, extract numbers exactly as shown.

                    Example citation format: "Revenue grew from $35.1B to $57.0B [chunk:4]."
                    """;

            String user = """
                    QUESTION:
                    %s

                    AVAILABLE CHUNK IDs IN THIS CONTEXT: %s

                    CONTEXT:
                    %s

                    Remember: Only cite chunks using IDs from the list above. Never invent chunk IDs.
                    """.formatted(ctx.question, availableChunkIds, context);

            // Calls Ollama API directly with system and user prompts
            return Mono.fromCallable(() -> ollamaClient.chat(system, user))
                    .subscribeOn(Schedulers.boundedElastic())
                    .doOnNext(ans -> ctx.draftAnswer = ans)
                    .then();
        });
    }

    /**
     * Constructs a string that represents the context in a formatted structure.
     * The context is derived from the list of {@link ChunkHit} objects in the
     * provided {@code AgentContext}. Each chunk's information, including its ID,
     * title, index, score, and content, is appended to the string with specific formatting.
     *
     * @param ctx The shared state ({@link AgentContext}) containing the list of
     *            retrieved {@link ChunkHit} objects, which are used to construct
     *            the formatted context string.
     * @return A string representing the formatted context with information about
     * all retrieved chunks from the provided {@code AgentContext}.
     */
    private String buildContext(AgentContext ctx) {

        // Formats retrieved chunks into single context string
        return ctx.retrieved.stream()
                .map(h -> "[chunk:" + h.chunkId() + "]\n" + h.content())
                .collect(Collectors.joining("\n\n---\n\n"));
    }
}
