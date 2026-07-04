package com.xai.dungeonmaster.service;

import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.plugin.LLMProvider;
import com.xai.dungeonmaster.dto.Envelope;
import com.xai.dungeonmaster.dto.NarrativeChunkPayload;
import com.xai.dungeonmaster.dto.NarrativePayload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Streams dungeon-master narration to WebSocket subscribers of
 * {@code /topic/narrative} as typed v2 envelopes: one
 * {@code { type: "narrative_chunk" }} per partial chunk as it is generated,
 * then a final {@code { type: "narrative_update" }} carrying the complete text
 * and provider/token metadata.
 *
 * This realizes the typed-envelope narration path declared in
 * {@code docs/api/asyncapi.yaml}, alongside the existing plain-text broadcasts.
 */
@Service
public class NarrationSocketService {

    public static final String TOPIC = "/topic/narrative";

    private final DungeonMasterEngine engine;
    private final SimpMessagingTemplate messaging;

    public NarrationSocketService(DungeonMasterEngine engine, SimpMessagingTemplate messaging) {
        this.engine = engine;
        this.messaging = messaging;
    }

    /**
     * Generate narration for {@code prompt} and stream it to {@link #TOPIC}.
     *
     * @param prompt    player prompt (may be null/blank)
     * @param requestId optional correlation id echoed in every envelope
     * @return the full aggregated narration response
     */
    public LLMProvider.NarrativeResponse streamNarration(String prompt, String requestId) {
        LLMProvider.NarrativeResponse response = engine.narrateStreaming(prompt, chunk ->
                messaging.convertAndSend(TOPIC,
                        Envelope.of("narrative_chunk", new NarrativeChunkPayload(chunk), requestId)));

        LLMProvider active = engine.getNarrator();
        NarrativePayload payload = new NarrativePayload(
                response.text,
                active != null ? active.id() : "unknown",
                response.tokensUsed,
                response.wasFallback);
        messaging.convertAndSend(TOPIC, Envelope.of("narrative_update", payload, requestId));
        return response;
    }
}
