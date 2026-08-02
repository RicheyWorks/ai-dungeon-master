package com.xai.dungeonmaster.controller;

import com.xai.dungeonmaster.Choice;
import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.auth.ActionRateGuard;
import com.xai.dungeonmaster.auth.StompAuthChannelInterceptor;
import com.xai.dungeonmaster.dto.ActionRequest;
import com.xai.dungeonmaster.service.GameInstanceService;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final ActionRateGuard actionRate;

    @Autowired
    public GameWebSocketController(
            GameInstanceService games,
            SimpMessagingTemplate messaging,
            @Autowired(required = false) ActionRateGuard actionRate) {
        this.games = games;
        this.messaging = messaging;
        this.actionRate = actionRate;
    }

    /** Test helper without rate limit. */
    public GameWebSocketController(GameInstanceService games, SimpMessagingTemplate messaging) {
        this(games, messaging, null);
    }

    @MessageMapping("/action")
    public void handleAction(ActionRequest req, StompHeaderAccessor accessor) {
        String sessionId = StompAuthChannelInterceptor.sessionIdOf(accessor);
        String topic = GameInstanceService.narrativeTopic(sessionId);

        if (actionRate != null) {
            String key = (sessionId == null || sessionId.isBlank()) ? "anon" : sessionId;
            ActionRateGuard.Decision d = actionRate.check(key);
            if (!d.allowed()) {
                messaging.convertAndSend(
                        topic,
                        "[WS] Action rate limit exceeded. Retry after " + d.retryAfterSeconds() + "s.");
                return;
            }
        }

        DungeonMasterEngine engine = games.forSession(sessionId);

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
