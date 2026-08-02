package com.xai.dungeonmaster.service;

import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.auth.NarrationRateGuard;
import com.xai.dungeonmaster.dto.Envelope;
import com.xai.dungeonmaster.dto.ErrorPayload;
import com.xai.dungeonmaster.dto.NarrativeChunkPayload;
import com.xai.dungeonmaster.dto.NarrativePayload;
import com.xai.dungeonmaster.plugin.LLMProvider;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final NarrationRateGuard rateGuard;

    @Autowired
    public NarrationSocketService(
            GameInstanceService games,
            SimpMessagingTemplate messaging,
            @Autowired(required = false) NarrationRateGuard rateGuard) {
        this.games = games;
        this.messaging = messaging;
        this.rateGuard = rateGuard;
    }

    /** Test helper: multi-session games, no rate limit. */
    public NarrationSocketService(GameInstanceService games, SimpMessagingTemplate messaging) {
        this(games, messaging, null);
    }

    /** Test helper: single shared engine, no rate limit. */
    public NarrationSocketService(DungeonMasterEngine engine, SimpMessagingTemplate messaging) {
        this(GameInstanceService.singleton(engine), messaging, null);
    }

    /**
     * Stream narration for the process-default engine to {@link #TOPIC}.
     */
    public LLMProvider.NarrativeResponse streamNarration(String prompt, String requestId) {
        return streamNarration(null, prompt, requestId);
    }

    /**
     * Stream narration for the given player session (null = default engine +
     * global topic). Rate-limited per session (or {@code anon}).
     */
    public LLMProvider.NarrativeResponse streamNarration(String sessionId, String prompt, String requestId) {
        String topic = GameInstanceService.narrativeTopic(sessionId);
        if (rateGuard != null) {
            String key = (sessionId == null || sessionId.isBlank()) ? "anon" : sessionId;
            NarrationRateGuard.Decision d = rateGuard.check(key);
            if (!d.allowed()) {
                messaging.convertAndSend(
                        topic,
                        Envelope.of(
                                "error",
                                new ErrorPayload(
                                        "Narration rate limit exceeded. Retry after "
                                                + d.retryAfterSeconds()
                                                + "s."),
                                requestId));
                return new LLMProvider.NarrativeResponse("", 0, 0.0, true);
            }
        }

        DungeonMasterEngine engine = games.forSession(sessionId);

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
