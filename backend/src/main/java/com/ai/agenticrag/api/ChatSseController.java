package com.ai.agenticrag.api;

import com.ai.agenticrag.orchestrator.MultiAgentOrchestrator;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * REST controller for handling server-sent event (SSE) based chat interactions.
 * Provides an endpoint for streaming events from multiple agents, followed by a final response.
 */
@RestController
public class ChatSseController {
    private final MultiAgentOrchestrator orchestrator;

    /**
     * Constructs an instance of {@code ChatSseController}.
     *
     * @param orchestrator the orchestrator responsible for handling chat events
     */
    public ChatSseController(MultiAgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /**
     * Streams events related to the processing of a user's question using server-sent events (SSE).
     * This method combines intermediate events from orchestrator processing with a final response event.
     *
     * @param question the input question submitted by the user, used as the basis for generating events
     * @param jobId an optional parameter representing the job identifier for tracking the processing request
     * @return a Flux that streams server-sent events encapsulating intermediate events and the final response
     */
    @GetMapping(value = "/api/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> stream(@RequestParam("question") String question,
                                                @RequestParam(value = "jobId", required = false) String jobId
    ) {
        Flux<ServerSentEvent<Object>> events = orchestrator.events(question, jobId)
                .map(ev -> ServerSentEvent.builder((Object) ev).event("citations").event("agent").build());
        Flux<ServerSentEvent<Object>> done = orchestrator.finalAnswer(question, jobId)
                .map(ans -> ServerSentEvent.builder((Object) ans).event("final").build())
                .flux();
        return Flux.concat(events, done);
    }
}
