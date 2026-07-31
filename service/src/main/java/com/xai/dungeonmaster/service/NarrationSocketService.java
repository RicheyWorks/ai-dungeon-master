package com.xai.dungeonmaster.service;

import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.plugin.LLMProvider;
import com.xai.dungeonmaster.dto.Envelope;
import com.xai.dungeonmaster.dto.NarrativeChunkPayload;
import com.xai.dungeonmaster.dto.NarrativePayload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Streams dungeon-master narration to WebSocket subscribers as typed v2
 * envelopes: one {@code narrative_chunk} per partial chunk, then a final
 * {@code narrative_update}. Authenticated sessions stream to
 * {@code /topic/narrative/{sessionId}}; the legacy global topic is used when
 * no session is bound.
 */
@Service
public class NarrationSocketService {

    /** Legacy global topic (unauthenticated / default engine). */
    public static final String TOPIC = "/topic/narrative";

    private final GameInstanceService games;
    private final SimpMessagingTemplate messaging;

    public NarrationSocketService(GameInstanceService games, SimpMessagingTemplate messaging) {
        this.games = games;
        this.messaging = messaging;
    }

    /** Test helper: single shared engine. */
    public NarrationSocketService(DungeonMasterEngine engine, SimpMessagingTemplate messaging) {
        this(GameInstanceService.singleton(engine), messaging);
    }

    /**
     * Stream narration for the process-default engine to {@link #TOPIC}.
     */
    public LLMProvider.NarrativeResponse streamNarration(String prompt, String requestId) {
        return streamNarration(null, prompt, requestId);
    }

    /**
     * Stream narration for the given player session (null = default engine +
     * global topic).
     */
    public LLMProvider.NarrativeResponse streamNarration(String sessionId, String prompt, String requestId) {
        DungeonMasterEngine engine = games.forSession(sessionId);
        String topic = GameInstanceService.narrativeTopic(sessionId);

        LLMProvider.NarrativeResponse response = engine.narrateStreaming(prompt, chunk ->
                messaging.convertAndSend(topic,
                        Envelope.of("narrative_chunk", new NarrativeChunkPayload(chunk), requestId)));

        LLMProvider active = engine.getNarrator();
        NarrativePayload payload = new NarrativePayload(
                response.text,
                active != null ? active.id() : "unknown",
                response.tokensUsed,
                response.wasFallback);
        messaging.convertAndSend(topic, Envelope.of("narrative_update", payload, requestId));
        return response;
    }
}
