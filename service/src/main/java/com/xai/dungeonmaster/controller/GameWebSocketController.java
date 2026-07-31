package com.xai.dungeonmaster.controller;

import com.xai.dungeonmaster.Choice;
import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.auth.StompAuthChannelInterceptor;
import com.xai.dungeonmaster.dto.ActionRequest;
import com.xai.dungeonmaster.service.GameInstanceService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.stream.Collectors;

/**
 * STOMP WebSocket controller for player actions.
 *
 * Clients SEND to {@code /app/action} with {@code { "choiceLabel": "Attack" }}.
 * When the connection was authenticated (Bearer JWT on STOMP CONNECT), the
 * action runs against that session's isolated engine and the ack is published
 * to {@code /topic/narrative/{sessionId}}. Unauthenticated connections use the
 * process-default engine and {@code /topic/narrative}.
 */
@Controller
public class GameWebSocketController {

    private final GameInstanceService games;
    private final SimpMessagingTemplate messaging;

    public GameWebSocketController(GameInstanceService games, SimpMessagingTemplate messaging) {
        this.games = games;
        this.messaging = messaging;
    }

    @MessageMapping("/action")
    public void handleAction(ActionRequest req, StompHeaderAccessor accessor) {
        String sessionId = StompAuthChannelInterceptor.sessionIdOf(accessor);
        DungeonMasterEngine engine = games.forSession(sessionId);
        String topic = GameInstanceService.narrativeTopic(sessionId);

        if (req == null || req.getChoiceLabel() == null || req.getChoiceLabel().isBlank()) {
            messaging.convertAndSend(topic, "[WS] Empty action received — ignored.");
            return;
        }

        String label = req.getChoiceLabel().trim();
        Choice matched = engine.getCurrentAvailableChoices()
                .stream()
                .filter(c -> c.getLabel().equalsIgnoreCase(label))
                .findFirst()
                .orElse(null);

        if (matched == null) {
            String available = engine.getCurrentAvailableChoices().stream()
                    .map(Choice::getLabel)
                    .collect(Collectors.joining(", "));
            messaging.convertAndSend(topic,
                    "[WS] Unknown action: '" + label + "'. Available: " + available);
            return;
        }

        engine.handleChoice(matched);
        // Engine uiListener already pushed narrative events to the same topic.
        messaging.convertAndSend(topic, "[WS] Action processed: " + label);
    }
}
