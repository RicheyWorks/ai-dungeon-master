package com.xai.dungeonmaster.controller;

import com.xai.dungeonmaster.auth.StompAuthChannelInterceptor;
import com.xai.dungeonmaster.dto.NarrateRequest;
import com.xai.dungeonmaster.service.NarrationSocketService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;

/**
 * STOMP endpoint for streaming narration.
 *
 * Clients SEND to {@code /app/narrate} with {@code { "prompt": "..." }}.
 * Authenticated connections stream chunks to {@code /topic/narrative/{sessionId}};
 * unauthenticated connections use the legacy {@code /topic/narrative}.
 */
@Controller
public class NarrationWebSocketController {

    private final NarrationSocketService narration;

    public NarrationWebSocketController(NarrationSocketService narration) {
        this.narration = narration;
    }

    @MessageMapping("/narrate")
    public void narrate(NarrateRequest req, StompHeaderAccessor accessor) {
        String prompt = (req == null || req.prompt() == null) ? "" : req.prompt();
        String sessionId = StompAuthChannelInterceptor.sessionIdOf(accessor);
        narration.streamNarration(sessionId, prompt, null);
    }
}
