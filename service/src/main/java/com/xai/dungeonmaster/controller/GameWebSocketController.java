package com.xai.dungeonmaster.controller;

import com.xai.dungeonmaster.Choice;
import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.dto.ActionRequest;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import java.util.stream.Collectors;

/**
 * STOMP WebSocket controller.
 *
 * Clients that prefer a persistent connection over polling can send STOMP
 * messages to /app/action and receive narrative echoes back on /topic/narrative.
 *
 * The engine's uiUpdater (wired in GameConfig) already pushes every broadcast
 * to /topic/narrative, so the @SendTo here is just the immediate acknowledgement.
 *
 * Example STOMP client payload:
 *   SEND /app/action
 *   content-type:application/json
 *
 *   { "choiceLabel": "Attack" }
 */
@Controller
public class GameWebSocketController {

    private final DungeonMasterEngine engine;

    public GameWebSocketController(DungeonMasterEngine engine) {
        this.engine = engine;
    }

    /**
     * Routes a player action through the engine.
     * The return value is a confirmation string broadcast to all subscribers of
     * /topic/narrative (in addition to whatever the engine itself broadcasts).
     */
    @MessageMapping("/action")
    @SendTo("/topic/narrative")
    public String handleAction(ActionRequest req) {
        if (req == null || req.getChoiceLabel() == null || req.getChoiceLabel().isBlank()) {
            return "[WS] Empty action received — ignored.";
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
            return "[WS] Unknown action: '" + label + "'. Available: " + available;
        }

        engine.handleChoice(matched);

        // The engine's uiUpdater already fired the main narrative events.
        // Return a minimal ack so the sender knows the server accepted it.
        return "[WS] Action processed: " + label;
    }
}
