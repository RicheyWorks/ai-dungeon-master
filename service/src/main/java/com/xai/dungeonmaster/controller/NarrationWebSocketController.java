package com.xai.dungeonmaster.controller;

import com.xai.dungeonmaster.dto.NarrateRequest;
import com.xai.dungeonmaster.service.NarrationSocketService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

/**
 * STOMP endpoint for streaming narration.
 *
 * Clients SEND to {@code /app/narrate} with { "prompt": "..." }; the service
 * pushes a sequence of {@code narrative_chunk} envelopes followed by a final
 * {@code narrative_update} envelope to {@code /topic/narrative}.
 */
@Controller
public class NarrationWebSocketController {

    private final NarrationSocketService narration;

    public NarrationWebSocketController(NarrationSocketService narration) {
        this.narration = narration;
    }

    @MessageMapping("/narrate")
    public void narrate(NarrateRequest req) {
        String prompt = (req == null || req.prompt() == null) ? "" : req.prompt();
        narration.streamNarration(prompt, null);
    }
}
