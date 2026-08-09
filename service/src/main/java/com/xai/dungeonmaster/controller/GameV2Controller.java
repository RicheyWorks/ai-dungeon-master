package com.xai.dungeonmaster.controller;

import com.xai.dungeonmaster.Choice;
import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.PartyState;
import com.xai.dungeonmaster.auth.JwtAuthFilter;
import com.xai.dungeonmaster.auth.SessionService;
import com.xai.dungeonmaster.plugin.LLMProvider;
import com.xai.dungeonmaster.dto.ActionRequest;
import com.xai.dungeonmaster.dto.Envelope;
import com.xai.dungeonmaster.dto.ErrorPayload;
import com.xai.dungeonmaster.dto.GameStatusV2;
import com.xai.dungeonmaster.dto.NarrateRequest;
import com.xai.dungeonmaster.dto.NarrativePayload;
import com.xai.dungeonmaster.service.GameInstanceService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Versioned v2 REST API.
 *
 * Base path: /v2
 * ─────────────────────────────────────────────────────────────────────────────
 * GET  /v2/status   — envelope { type:"game_status", payload: structured state }
 * POST /v2/action   — apply a choice; returns the updated game_status envelope
 * POST /v2/narrate  — LLM narration for the caller's engine
 * POST /v2/save     — persist the caller's engine to a session-scoped file
 * GET  /v2/save     — save file presence / size / mtime (no engine load)
 * DELETE /v2/save   — delete the caller's save file
 * POST /v2/load     — restore the caller's engine from its save file
 * POST /v2/reset    — mint a fresh engine (or restart the default)
 *
 * Authenticated callers (Bearer JWT from {@code POST /v2/session}) each get an
 * isolated {@link DungeonMasterEngine}. Unauthenticated calls share the
 * process-default engine (legacy single-player behaviour).
 */
@RestController
@RequestMapping("/v2")
// tighten in production
public class GameV2Controller {

    private static final int RECENT_HISTORY_LIMIT = 30;

    private final GameInstanceService games;

    @org.springframework.beans.factory.annotation.Autowired
    public GameV2Controller(GameInstanceService games) {
        this.games = games;
    }

    /** Test helper: wrap a single shared engine (no multi-session). */
    public GameV2Controller(DungeonMasterEngine engine) {
        this(GameInstanceService.singleton(engine));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /v2/status
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/status")
    public Envelope<GameStatusV2> status(
            HttpServletRequest request,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return Envelope.of("game_status", snapshot(engine(request)), requestId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /v2/action   body: { "choiceLabel": "Attack" }
    // ─────────────────────────────────────────────────────────────────────────
    @PostMapping("/action")
    public ResponseEntity<Envelope<?>> action(
            HttpServletRequest request,
            @RequestBody(required = false) ActionRequest req,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {

        if (req == null || req.getChoiceLabel() == null || req.getChoiceLabel().isBlank()) {
            return ResponseEntity.badRequest().body(
                    Envelope.of("error", new ErrorPayload("choiceLabel must not be blank."), requestId));
        }

        DungeonMasterEngine engine = engine(request);
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
        return ResponseEntity.ok(Envelope.of("game_status", snapshot(engine), requestId));
    }

    // POST /v2/narrate   body: { "prompt": "I search the altar for traps" }
    @PostMapping("/narrate")
    public ResponseEntity<Envelope<?>> narrate(
            HttpServletRequest request,
            @RequestBody(required = false) NarrateRequest req,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {

        DungeonMasterEngine engine = engine(request);
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

    // POST /v2/save — session-scoped file under game.saves.dir
    @PostMapping("/save")
    public Envelope<Map<String, Object>> save(
            HttpServletRequest request,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        SessionService.Session session = session(request);
        DungeonMasterEngine engine = games.resolve(session);
        Path path = games.savePath(session);
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (Exception e) {
            // saveGame will report its own I/O failure
        }
        engine.saveGame(path.toString());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("saved", true);
        payload.put("path", path.toString());
        payload.put("sessionScoped", session != null);
        return Envelope.of("game_save", payload, requestId);
    }

    /**
     * Metadata for the caller's save slot — does not load the engine.
     * {@code exists=false} when no file is present.
     */
    @GetMapping("/save")
    public Envelope<Map<String, Object>> saveMeta(
            HttpServletRequest request,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        SessionService.Session session = session(request);
        Path path = games.savePath(session);
        Map<String, Object> payload = new LinkedHashMap<>();
        boolean exists = Files.isRegularFile(path);
        payload.put("exists", exists);
        payload.put("path", path.toString());
        payload.put("sessionScoped", session != null);
        if (exists) {
            try {
                payload.put("bytes", Files.size(path));
                payload.put("modifiedAtEpochMs", Files.getLastModifiedTime(path).toMillis());
            } catch (Exception e) {
                payload.put("bytes", 0L);
            }
        } else {
            payload.put("bytes", 0L);
        }
        return Envelope.of("game_save_meta", payload, requestId);
    }

    /** Delete the caller's save file. Idempotent when no file exists. */
    @DeleteMapping("/save")
    public Envelope<Map<String, Object>> deleteSave(
            HttpServletRequest request,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        SessionService.Session session = session(request);
        Path path = games.savePath(session);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("path", path.toString());
        payload.put("sessionScoped", session != null);
        boolean deleted = false;
        if (Files.isRegularFile(path)) {
            try {
                deleted = Files.deleteIfExists(path);
            } catch (Exception e) {
                deleted = false;
                payload.put("error", e.getMessage() == null ? "delete failed" : e.getMessage());
            }
        }
        payload.put("deleted", deleted);
        payload.put("exists", Files.isRegularFile(path));
        return Envelope.of("game_save_delete", payload, requestId);
    }

    // POST /v2/load
    @PostMapping("/load")
    public ResponseEntity<Envelope<?>> load(
            HttpServletRequest request,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        SessionService.Session session = session(request);
        DungeonMasterEngine engine = games.resolve(session);
        Path path = games.savePath(session);
        if (!Files.isRegularFile(path)) {
            return ResponseEntity.badRequest().body(
                    Envelope.of("error",
                            new ErrorPayload("No save found at " + path),
                            requestId));
        }
        engine.loadGame(path.toString());
        return ResponseEntity.ok(Envelope.of("game_status", snapshot(engine), requestId));
    }

    // POST /v2/reset — fresh engine for the caller
    @PostMapping("/reset")
    public Envelope<GameStatusV2> reset(
            HttpServletRequest request,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        SessionService.Session session = session(request);
        DungeonMasterEngine engine = games.reset(session);
        return Envelope.of("game_status", snapshot(engine), requestId);
    }

    private DungeonMasterEngine engine(HttpServletRequest request) {
        return games.resolve(session(request));
    }

    private static SessionService.Session session(HttpServletRequest request) {
        if (request == null) return null;
        Object s = request.getAttribute(JwtAuthFilter.SESSION_ATTR);
        return (s instanceof SessionService.Session se) ? se : null;
    }

    /** Build the structured status payload from a live engine. */
    private GameStatusV2 snapshot(DungeonMasterEngine engine) {
        PartyState party = engine.getPartyState();

        List<String> choices = engine.getCurrentAvailableChoices().stream()
                .map(Choice::getLabel)
                .collect(Collectors.toList());

        List<com.xai.dungeonmaster.dto.ChoiceDetail> choiceDetails = engine.getCurrentAvailableChoices().stream()
                .map(com.xai.dungeonmaster.dto.ChoiceDetail::from)
                .collect(Collectors.toList());

        List<String> history = engine.getTurnHistory();
        int start = Math.max(0, history.size() - RECENT_HISTORY_LIMIT);
        List<String> recent = new ArrayList<>(history.subList(start, history.size()));

        return new GameStatusV2(
                party.members(),
                engine.getChaosLevel(),
                engine.getCombatState().isActive(),
                choices,
                recent,
                com.xai.dungeonmaster.dto.QuestInfo.from(engine.getCurrentQuest()),
                engine.getChronicle().renderFacts(6),
                engine.getWorldMap().getCurrentLocation(),
                List.copyOf(engine.getWorldMap().getDiscoveredRifts()),
                choiceDetails);
    }
}
