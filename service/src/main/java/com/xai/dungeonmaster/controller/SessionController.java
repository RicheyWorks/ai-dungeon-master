package com.xai.dungeonmaster.controller;

import com.xai.dungeonmaster.auth.JwtAuthFilter;
import com.xai.dungeonmaster.auth.JwtService;
import com.xai.dungeonmaster.auth.SessionService;
import com.xai.dungeonmaster.content.SessionPackService;
import com.xai.dungeonmaster.dto.Envelope;
import com.xai.dungeonmaster.dto.ErrorPayload;
import com.xai.dungeonmaster.dto.SessionPayload;
import com.xai.dungeonmaster.dto.SessionRequest;
import com.xai.dungeonmaster.service.SessionLogoutService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Session identity endpoints for the v2 API.
 *
 * POST   /v2/session         — create a guest session, returns { sessionId, token, ... }
 * POST   /v2/session/refresh — re-issue JWT for the current session (same id)
 * GET    /v2/session/me      — echo the caller's session (requires a valid Bearer token)
 * DELETE /v2/session         — logout: drop identity, pack prefs, and live engine
 *
 * The login endpoint is intentionally public so a fresh client can obtain a
 * token; {@link JwtAuthFilter} enforces auth on every other /v2 route when
 * {@code game.auth.enabled=true}.
 */
@RestController
@RequestMapping("/v2/session")
public class SessionController {

    private final SessionService sessions;
    private final SessionLogoutService logout;
    private final JwtService jwt;
    private final SessionPackService sessionPacks;

    @org.springframework.beans.factory.annotation.Autowired
    public SessionController(
            SessionService sessions,
            SessionLogoutService logout,
            JwtService jwt,
            SessionPackService sessionPacks) {
        this.sessions = sessions;
        this.logout = logout;
        this.jwt = jwt;
        this.sessionPacks = sessionPacks;
    }

    /** Test / embed helper without logout / packs. */
    public SessionController(SessionService sessions) {
        this(sessions, null, null, null);
    }

    /** Test helper with logout. */
    public SessionController(SessionService sessions, SessionLogoutService logout) {
        this(sessions, logout, null, null);
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

    /**
     * Extend the caller's session with a fresh JWT (same session id / identity).
     * Prefer this over minting a new guest when the token is near expiry.
     */
    @PostMapping("/refresh")
    public ResponseEntity<Envelope<?>> refresh(
            @RequestAttribute(value = JwtAuthFilter.SESSION_ATTR, required = false) SessionService.Session session,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {

        if (session == null) {
            return ResponseEntity.status(401).body(
                    Envelope.of("error", new ErrorPayload("Not authenticated."), requestId));
        }
        return sessions.refreshSession(session.id())
                .<ResponseEntity<Envelope<?>>>map(issued -> {
                    SessionPayload payload = new SessionPayload(
                            issued.session().id(),
                            issued.token(),
                            issued.session().displayName(),
                            issued.expiresAtEpochSeconds(),
                            issued.session().createdAtEpoch());
                    return ResponseEntity.ok(Envelope.of("session", payload, requestId));
                })
                .orElseGet(() -> ResponseEntity.status(401).body(
                        Envelope.of("error", new ErrorPayload("Session no longer exists."), requestId)));
    }

    /**
     * Rename the caller's display name and re-issue a JWT with the new name claim.
     */
    @PatchMapping
    public ResponseEntity<Envelope<?>> rename(
            @RequestBody(required = false) SessionRequest req,
            @RequestAttribute(value = JwtAuthFilter.SESSION_ATTR, required = false) SessionService.Session session,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {

        if (session == null) {
            return ResponseEntity.status(401).body(
                    Envelope.of("error", new ErrorPayload("Not authenticated."), requestId));
        }
        String displayName = (req == null) ? null : req.displayName();
        if (displayName == null || displayName.isBlank()) {
            return ResponseEntity.badRequest().body(
                    Envelope.of("error", new ErrorPayload("displayName is required."), requestId));
        }
        return sessions.renameSession(session.id(), displayName)
                .<ResponseEntity<Envelope<?>>>map(issued -> {
                    SessionPayload payload = new SessionPayload(
                            issued.session().id(),
                            issued.token(),
                            issued.session().displayName(),
                            issued.expiresAtEpochSeconds(),
                            issued.session().createdAtEpoch());
                    return ResponseEntity.ok(Envelope.of("session", payload, requestId));
                })
                .orElseGet(() -> ResponseEntity.status(401).body(
                        Envelope.of("error", new ErrorPayload("Session no longer exists."), requestId)));
    }

    @GetMapping("/me")
    public ResponseEntity<Envelope<?>> me(
            @RequestAttribute(value = JwtAuthFilter.SESSION_ATTR, required = false) SessionService.Session session,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {

        if (session == null) {
            return ResponseEntity.status(401).body(
                    Envelope.of("error", new ErrorPayload("Not authenticated."), requestId));
        }
        long exp = 0L;
        if (jwt != null) {
            String raw = bearerToken(authorization);
            if (raw != null) {
                exp = jwt.expiryEpochSeconds(raw).orElse(0L);
            }
        }
        List<String> packs = null;
        if (sessionPacks != null) {
            packs = new ArrayList<>(sessionPacks.enabledPackIds(session.id()));
            packs.sort(String.CASE_INSENSITIVE_ORDER);
        }
        SessionPayload payload = new SessionPayload(
                session.id(),
                null, // never reflect a token back
                session.displayName(),
                exp,
                session.createdAtEpoch(),
                session.lastSeenEpoch(),
                packs);
        return ResponseEntity.ok(Envelope.of("session", payload, requestId));
    }

    /**
     * Explicit logout. Requires a valid session (when auth is on). Clears pack
     * overrides and destroys the live game engine for the session.
     */
    @DeleteMapping
    public ResponseEntity<Envelope<?>> logout(
            @RequestAttribute(value = JwtAuthFilter.SESSION_ATTR, required = false) SessionService.Session session,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {

        if (session == null) {
            return ResponseEntity.status(401).body(
                    Envelope.of("error", new ErrorPayload("Not authenticated."), requestId));
        }
        if (logout != null) {
            logout.logout(session.id());
        } else {
            sessions.delete(session.id());
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("loggedOut", true);
        payload.put("sessionId", session.id());
        return ResponseEntity.ok(Envelope.of("session.logout", payload, requestId));
    }

    private static String bearerToken(String authorization) {
        if (authorization == null || authorization.isBlank()) return null;
        if (authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String t = authorization.substring(7).trim();
            return t.isEmpty() ? null : t;
        }
        return null;
    }
}
