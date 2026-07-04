package com.xai.dungeonmaster.controller;

import com.xai.dungeonmaster.Choice;
import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.PartyState;
import com.xai.dungeonmaster.plugin.LLMProvider;
import com.xai.dungeonmaster.dto.ActionRequest;
import com.xai.dungeonmaster.dto.Envelope;
import com.xai.dungeonmaster.dto.ErrorPayload;
import com.xai.dungeonmaster.dto.GameStatusV2;
import com.xai.dungeonmaster.dto.NarrateRequest;
import com.xai.dungeonmaster.dto.NarrativePayload;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Versioned v2 REST API.
 *
 * Base path: /v2
 * ─────────────────────────────────────────────────────────────────────────────
 * GET  /v2/status   — envelope { type:"game_status", payload: structured state }
 * POST /v2/action   — apply a choice; returns the updated game_status envelope,
 *                     or a { type:"error" } envelope with 400 on bad input
 *
 * Every response is wrapped in a typed {@link Envelope}. The legacy
 * /api/game/* controller is intentionally left untouched for existing clients.
 */
@RestController
@RequestMapping("/v2")
@CrossOrigin(origins = "*")   // tighten in production
public class GameV2Controller {

    private static final int RECENT_HISTORY_LIMIT = 30;

    private final DungeonMasterEngine engine;

    public GameV2Controller(DungeonMasterEngine engine) {
        this.engine = engine;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /v2/status
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/status")
    public Envelope<GameStatusV2> status(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return Envelope.of("game_status", snapshot(), requestId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /v2/action   body: { "choiceLabel": "Attack" }
    // ─────────────────────────────────────────────────────────────────────────
    @PostMapping("/action")
    public ResponseEntity<Envelope<?>> action(
            @RequestBody(required = false) ActionRequest req,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {

        if (req == null || req.getChoiceLabel() == null || req.getChoiceLabel().isBlank()) {
            return ResponseEntity.badRequest().body(
                    Envelope.of("error", new ErrorPayload("choiceLabel must not be blank."), requestId));
        }

        String label = req.getChoiceLabel().trim();
        Choice matched = engine.getCurrentAvailableChoices().stream()
                .filter(c -> c.getLabel().equalsIgnoreCase(label))
                .findFirst()
                .orElse(null);

        if (matched == null) {
            String available = engine.getCurrentAvailableChoices().stream()
                    .map(Choice::getLabel)
                    .collect(Collectors.joining(", "));
            return ResponseEntity.badRequest().body(
                    Envelope.of("error",
                            new ErrorPayload("Unknown choice: '" + label + "'. Available: " + available),
                            requestId));
        }

        engine.handleChoice(matched);
        return ResponseEntity.ok(Envelope.of("game_status", snapshot(), requestId));
    }

    // POST /v2/narrate   body: { "prompt": "I search the altar for traps" }
    @PostMapping("/narrate")
    public ResponseEntity<Envelope<?>> narrate(
            @RequestBody(required = false) NarrateRequest req,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {

        String userPrompt = (req == null || req.prompt() == null) ? "" : req.prompt();
        LLMProvider.NarrativeResponse response = engine.narrate(userPrompt);
        LLMProvider active = engine.getNarrator();

        NarrativePayload payload = new NarrativePayload(
                response.text,
                active != null ? active.id() : "unknown",
                response.tokensUsed,
                response.wasFallback);
        return ResponseEntity.ok(Envelope.of("narrative_update", payload, requestId));
    }

    /** Build the structured status payload from the live engine state. */
    private GameStatusV2 snapshot() {
        PartyState party = engine.getPartyState();

        List<String> choices = engine.getCurrentAvailableChoices().stream()
                .map(Choice::getLabel)
                .collect(Collectors.toList());

        List<String> history = engine.getTurnHistory();
        int start = Math.max(0, history.size() - RECENT_HISTORY_LIMIT);
        // Copy the tail so we serialize a stable list, not a live sub-view.
        List<String> recent = new ArrayList<>(history.subList(start, history.size()));

        return new GameStatusV2(
                party.members(),
                engine.getChaosLevel(),
                engine.getCombatState().isActive(),
                choices,
                recent);
    }
}
