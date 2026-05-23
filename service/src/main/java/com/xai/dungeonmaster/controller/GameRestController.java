package com.xai.dungeonmaster.controller;

import com.xai.dungeonmaster.Choice;
import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.dto.ActionRequest;
import com.xai.dungeonmaster.dto.GameStatusResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST API for the Dungeon Master game engine.
 *
 * Base path: /api/game
 *
 * Endpoints
 * ─────────────────────────────────────────────────────────────────────────────
 * GET  /api/game/status      — full game snapshot (party, chaos, choices, log)
 * GET  /api/game/choices     — just the available choice labels
 * POST /api/game/action      — body: { "choiceLabel": "Attack" }
 * POST /api/game/start       — (re)starts the quest and returns the opening text
 * POST /api/game/save        — persists game state to savegame.json
 * POST /api/game/load        — restores game state from savegame.json
 *
 * Narrative events (combat, flavor text, XP gains) are pushed in real time
 * over WebSocket to /topic/narrative — polling /status is only needed for
 * initial page load or reconnects.
 */
@RestController
@RequestMapping("/api/game")
@CrossOrigin(origins = "*")   // tighten in production
public class GameRestController {

    private final DungeonMasterEngine engine;

    public GameRestController(DungeonMasterEngine engine) {
        this.engine = engine;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/game/status
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/status")
    public GameStatusResponse getStatus() {
        GameStatusResponse resp = new GameStatusResponse();
        resp.setPartySummary(engine.getPartySummary());
        resp.setChaosLevel(engine.getChaosLevel());
        resp.setCombatActive(engine.getCombatState().isActive());

        List<String> labels = engine.getCurrentAvailableChoices()
                .stream()
                .map(Choice::getLabel)
                .collect(Collectors.toList());
        resp.setAvailableChoices(labels);

        List<String> history = engine.getTurnHistory();
        int start = Math.max(0, history.size() - 30);
        resp.setRecentHistory(history.subList(start, history.size()));

        return resp;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/game/choices
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/choices")
    public List<String> getChoices() {
        return engine.getCurrentAvailableChoices()
                .stream()
                .map(Choice::getLabel)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/game/action  { "choiceLabel": "Attack" }
    // ─────────────────────────────────────────────────────────────────────────
    @PostMapping("/action")
    public ResponseEntity<String> submitAction(@RequestBody ActionRequest req) {
        if (req == null || req.getChoiceLabel() == null || req.getChoiceLabel().isBlank()) {
            return ResponseEntity.badRequest().body("choiceLabel must not be blank.");
        }

        String label = req.getChoiceLabel().trim();

        Choice matched = engine.getCurrentAvailableChoices()
                .stream()
                .filter(c -> c.getLabel().equalsIgnoreCase(label))
                .findFirst()
                .orElse(null);

        if (matched == null) {
            return ResponseEntity.badRequest()
                    .body("Unknown choice: '" + label + "'. Available: " +
                          engine.getCurrentAvailableChoices().stream()
                                .map(Choice::getLabel)
                                .collect(Collectors.joining(", ")));
        }

        engine.handleChoice(matched);
        return ResponseEntity.ok("Action accepted: " + label);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/game/start
    // ─────────────────────────────────────────────────────────────────────────
    @PostMapping("/start")
    public String startQuest() {
        return engine.startQuest();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/game/save
    // ─────────────────────────────────────────────────────────────────────────
    @PostMapping("/save")
    public String saveGame() {
        engine.saveGame("savegame.json");
        return "Timeline persisted.";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/game/load
    // ─────────────────────────────────────────────────────────────────────────
    @PostMapping("/load")
    public String loadGame() {
        engine.loadGame("savegame.json");
        return "Timeline restored.";
    }
}
