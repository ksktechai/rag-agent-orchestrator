package com.ai.agenticrag.orchestrator;

import com.ai.agenticrag.api.dto.AgentEvent;
import com.ai.agenticrag.api.dto.FinalAnswer;
import com.ai.agenticrag.orchestrator.agents.*;
import com.ai.agenticrag.rag.ChunkHit;
import com.ai.agenticrag.rag.VectorStoreService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The MultiAgentOrchestrator class orchestrates multiple agents to process user queries
 * and generate answers based on relevant data retrieved from a vector store. It employs
 * a series of specialized agents to retrieve, synthesize, and evaluate information for
 * structured and accurate responses.
 * <p>
 * This orchestrator integrates multiple steps of query processing:a
 * - Routing of queries to specific agents.
 * - Planning and retrieval using parallel retrievers.
 * - Synthesizing and judging the quality of the retrieved context.
 * - Iterative query rewriting if further retrieval is required.
 * <p>
 * The class offers the following main functionalities:
 * <p>
 * - Emitting a stream of events during the query processing lifecycle for monitoring purposes.
 * - Providing a final synthesized answer to a given query, with supporting citations if applicable.
 * <p>
 * Dependencies:
 * - VectorStoreService: Provides access to a vectorized storage system for content retrieval.
 * - ChatClient: Handles communication with a chat-based system for synthesizing and evaluating results.
 */
@Service
public class MultiAgentOrchestrator {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MultiAgentOrchestrator.class);
    /**
     * A service providing access to a vectorized storage system for the retrieval of content
     * pertinent to processing user queries. The `VectorStoreService` serves as a critical
     * dependency in the `MultiAgentOrchestrator` for enabling agents to fetch relevant
     * data chunks based on vector similarity, facilitating the generation of accurate and
     * contextually grounded responses.
     * <p>
     * The vectorized storage system is utilized for:
     * - Performing similarity searches to identify chunks of stored content that are
     * semantically close to the input queries.
     * - Supporting multi-agent processes in planning and synthesizing answers.
     */
    private final VectorStoreService vectorStore;

    /**
     * The `ollamaClient` field represents an instance of `OllamaHttpClient` used for direct
     * communication with Ollama API. It is a core dependency of the `MultiAgentOrchestrator` class.
     * <p>
     * This client facilitates the synthesis and evaluation of user queries, enabling:
     * - Formulation of system and user prompts.
     * - Interaction with multi-agent processes for generating accurate and coherent responses.
     * - Iterative query refinement and context validation.
     * <p>
     * This bypasses Spring AI ChatClient to work around integration issues in Spring AI 2.0.0-M1.
     */
    private final OllamaHttpClient ollamaClient;

    /**
     * Constructs an instance of MultiAgentOrchestrator, initializing the required dependencies
     * for orchestrating multiple agents in the processing and synthesis of user-generated queries.
     *
     * @param vectorStore A service providing access to a vectorized storage system for data retrieval.
     * @param ollamaClient Direct HTTP client for Ollama API communication.
     */
    public MultiAgentOrchestrator(VectorStoreService vectorStore, OllamaHttpClient ollamaClient) {
        this.vectorStore = vectorStore;
        this.ollamaClient = ollamaClient;
    }

    /**
     * Executes the series of agent operations and generates a stream of {@link AgentEvent} instances.
     * The method initializes the agent context and utilizes RouterAgent and PlannerAgent
     * to produce events related to routing, planning, and supervisor configuration.
     *
     * @param question the input question or query to be processed by the agents
     * @param jobId the unique identifier for the job or task being executed
     * @return a Flux stream of {@link AgentEvent} emitted by the agents during execution
     */
    public Flux<AgentEvent> events(String question, String jobId) {
        AgentContext ctx = new AgentContext(question, jobId);
        RouterAgent router = new RouterAgent();
        PlannerAgent planner = new PlannerAgent();

        // Runs router; emits traces; provides static supervisor config
        return router.run(ctx)
                .thenMany(Flux.concat(
                        router.trace(ctx),
                        planner.trace(ctx),
                        Flux.just(AgentEvent.of("supervisor", "config",
                                Map.of("parallelRetrievers", List.of(6, 10, 14), "maxRetries", 2)))
                ));
    }

    /**
     * Processes the given question and job identifier to produce a final answer.
     * The method decides whether to retrieve additional information or directly generate a response
     * based on the availability of ingested documents and contextual needs.
     *
     * @param question the question to be answered
     * @param jobId the identifier for the job associated with the question
     * @return a {@code Mono<FinalAnswer>} representing the asynchronous computation of the final answer
     */
    public Mono<FinalAnswer> finalAnswer(String question, String jobId) {
        AgentContext ctx = new AgentContext(question, jobId);
        RouterAgent router = new RouterAgent();

        // NEW: if we have ingested docs, prefer retrieval
        // implement this
        return router.run(ctx).then(
                Mono.fromCallable(vectorStore::hasAnyDocuments)
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMap(hasDocs -> {
                            boolean forceRetrieval = hasDocs; // or smarter heuristic
                            if (forceRetrieval) ctx.needsRetrieval = true;

                            return ctx.needsRetrieval
                                    ? attempt(ctx, 0)
                                    : Mono.fromCallable(() -> ollamaClient.chat("You are helpful.", question))
                                    .subscribeOn(Schedulers.boundedElastic())
                                    .map(a -> new FinalAnswer(a, List.of()));
                        })
        );
    }

    /**
     * Attempts to generate a final answer by performing retrieval, synthesis, evaluation, and potential query re-writing.
     * This method orchestrates multiple agents (retrievers, synthesizer, judge, and rewriter) to process a given
     * query context and iteratively refines the result until a satisfactory final answer is produced or the maximum
     * number of attempts is reached.
     *
     * @param ctx     the context object containing data about the query and intermediate results, shared across agents
     * @param attempt the current attempt count, used to control recursion and terminate after a certain number of retries
     * @return a Mono that emits the final answer as a {@link FinalAnswer} object, including the answer and citations
     */
    private Mono<FinalAnswer> attempt(AgentContext ctx, int attempt) {
        List<Agent> retrievers = List.of(
                new RetrieverAgent("a", vectorStore, 6),
                new RetrieverAgent("b", vectorStore, 10),
                new RetrieverAgent("c", vectorStore, 14)
        );

        SynthesizerAgent synth = new SynthesizerAgent(ollamaClient);
        JudgeAgent judge = new JudgeAgent();
        QueryRewriteAgent rewriter = new QueryRewriteAgent(ollamaClient);

        ctx.retrieved.clear();

        Mono<Void> retrieveAll = Flux.fromIterable(retrievers)
                .flatMap(a -> a.run(ctx), 3) // parallel
                .then();

        // Retrieves content then synthesizes initial answer
        return retrieveAll
                .then(Mono.defer(() -> {
                    log.info("Retrieved {} chunks from parallel retrievers", ctx.retrieved.size());
                    if (!ctx.retrieved.isEmpty()) {
                        ChunkHit first = ctx.retrieved.get(0);
                        log.debug("Sample chunk: chunkId={}, title={}, score={}",
                                first.chunkId(), first.title(), first.score());
                    }
                    // Selects top‑scoring unique chunks for synthesis (reduced to 3 for speed)
                    List<ChunkHit> top = ctx.retrieved.stream()
                            .collect(Collectors.toMap(ChunkHit::chunkId, h -> h, (x, y) -> x))
                            .values().stream()
                            .sorted(Comparator.comparingDouble(ChunkHit::score).reversed())
                            .limit(3)
                            .toList();
                    log.info("Selected top {} unique chunks for synthesis", top.size());
                    if (!top.isEmpty()) {
                        ChunkHit best = top.getFirst();
                        log.info("Best chunk: chunkId={}, title={}, score={}",
                                best.chunkId(), best.title(), best.score());
                    }
                    ctx.retrieved.clear();
                    ctx.retrieved.addAll(top);
                    log.info("TOP_CHUNKS_FOR_SYNTH size={}", ctx.retrieved.size());
                    for (int i = 0; i < Math.min(5, ctx.retrieved.size()); i++) {
                        ChunkHit h = ctx.retrieved.get(i);
                        String txt = h.content() == null ? "NULL" : h.content();
                        txt = txt.replace("\r", "").replace("\n", "\\n");
                        log.info("TOP_CHUNK[{}] id={} idx={} title={} score={} text={}",
                                i + 1, h.chunkId(), h.chunkIndex(), h.title(), h.score(),
                                txt.substring(0, Math.min(600, txt.length())));
                    }
                    log.info("DEBUG: About to return from defer block, ctx.retrieved.size={}", ctx.retrieved.size());
                    return Mono.empty();
                }))
                .then(Mono.fromRunnable(() -> log.info("DEBUG: BEFORE synth.run(), ctx.retrieved.size={}", ctx.retrieved.size())))
                .then(synth.run(ctx))
                .then(Mono.fromRunnable(() -> log.info("DEBUG: AFTER synth.run(), ctx.retrieved.size={}", ctx.retrieved.size())))
                .then(judge.run(ctx))
                // Constructs final answer; retries if needed
                .then(Mono.defer(() -> {
                    FinalAnswer ans = new FinalAnswer(
                            ctx.draftAnswer == null ? "" : ctx.draftAnswer,
                            // Maps retrieved chunks to citation records
                            ctx.retrieved.stream().map(h -> new FinalAnswer.Citation(h.chunkId(), h.title(), h.chunkIndex(), h.score())).toList()
                    );

                    if (ctx.judgedPass) return Mono.just(ans);
                    if (attempt >= 2) {
                        return Mono.just(new FinalAnswer(
                                "I couldn't confidently ground a complete answer from the retrieved context.\n\n" + ans.answer(),
                                ans.citations()
                        ));
                    }
                    return rewriter.run(ctx).then(attempt(ctx, attempt + 1));
                }));
    }
}
