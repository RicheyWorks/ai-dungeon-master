package com.xai.dungeonmaster.controller;

import com.xai.dungeonmaster.auth.JwtAuthFilter;
import com.xai.dungeonmaster.auth.SessionService;
import com.xai.dungeonmaster.dto.Envelope;
import com.xai.dungeonmaster.dto.ErrorPayload;
import com.xai.dungeonmaster.dto.SessionPayload;
import com.xai.dungeonmaster.dto.SessionRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Session identity endpoints for the v2 API.
 *
 * POST /v2/session      — create a guest session, returns { sessionId, token, ... }
 * GET  /v2/session/me   — echo the caller's session (requires a valid Bearer token)
 *
 * The login endpoint is intentionally public so a fresh client can obtain a
 * token; {@link JwtAuthFilter} enforces auth on every other /v2 route when
 * {@code game.auth.enabled=true}.
 */
@RestController
@RequestMapping("/v2/session")
@CrossOrigin(origins = "*")
public class SessionController {

    private final SessionService sessions;

    public SessionController(SessionService sessions) {
        this.sessions = sessions;
    }

    @PostMapping
    public Envelope<SessionPayload> create(
            @RequestBody(required = false) SessionRequest req,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {

        String displayName = (req == null) ? null : req.displayName();
        SessionService.Issued issued = sessions.createSession(displayName);
        SessionPayload payload = new SessionPayload(
                issued.session().id(),
                issued.token(),
                issued.session().displayName(),
                issued.expiresAtEpochSeconds(),
                issued.session().createdAtEpoch());
        return Envelope.of("session", payload, requestId);
    }

    @GetMapping("/me")
    public ResponseEntity<Envelope<?>> me(
            @RequestAttribute(value = JwtAuthFilter.SESSION_ATTR, required = false) SessionService.Session session,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {

        if (session == null) {
            return ResponseEntity.status(401).body(
                    Envelope.of("error", new ErrorPayload("Not authenticated."), requestId));
        }
        SessionPayload payload = new SessionPayload(
                session.id(),
                null, // never reflect a token back
                session.displayName(),
                0L,
                session.createdAtEpoch());
        return ResponseEntity.ok(Envelope.of("session", payload, requestId));
    }
}
