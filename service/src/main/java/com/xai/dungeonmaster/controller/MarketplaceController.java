package com.xai.dungeonmaster.controller;

import com.xai.dungeonmaster.auth.JwtAuthFilter;
import com.xai.dungeonmaster.auth.RateLimitFilter;
import com.xai.dungeonmaster.auth.SecurityAudit;
import com.xai.dungeonmaster.auth.SessionService;
import com.xai.dungeonmaster.dto.Envelope;
import com.xai.dungeonmaster.dto.ErrorPayload;
import com.xai.dungeonmaster.dto.MarketplaceInstallJob;
import com.xai.dungeonmaster.dto.MarketplaceListing;
import com.xai.dungeonmaster.dto.MarketplacePayload;
import com.xai.dungeonmaster.service.MarketplaceJobStore;
import com.xai.dungeonmaster.service.MarketplaceService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Local content-pack marketplace / discovery API.
 *
 * <ul>
 *   <li>{@code GET  /v2/marketplace} — list packs</li>
 *   <li>{@code GET  /v2/marketplace/{id}} — pack detail</li>
 *   <li>{@code POST /v2/marketplace/{id}/install} — sync install (default)</li>
 *   <li>{@code POST /v2/marketplace/{id}/install?async=true} — background job (202)</li>
 *   <li>{@code POST /v2/marketplace/{id}/install-async} — same as async=true (typed clients)</li>
 *   <li>{@code GET  /v2/marketplace/jobs} — list caller's install jobs</li>
 *   <li>{@code GET  /v2/marketplace/jobs/{jobId}} — install progress (owner only)</li>
 *   <li>{@code DELETE /v2/marketplace/jobs/{jobId}} — cancel install (owner only)</li>
 * </ul>
 */
@RestController
@RequestMapping("/v2/marketplace")
public class MarketplaceController {

    private final MarketplaceService marketplace;

    public MarketplaceController(MarketplaceService marketplace) {
        this.marketplace = marketplace;
    }

    @GetMapping
    public Envelope<MarketplacePayload> list(
            @RequestParam(value = "q", required = false) String query,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return Envelope.of("marketplace", marketplace.list(query), requestId);
    }

    /**
     * List install jobs owned by the caller (most recent first). Empty when
     * unauthenticated or no jobs for this session.
     */
    @GetMapping("/jobs")
    public Envelope<Map<String, Object>> listJobs(
            @RequestParam(value = "limit", required = false, defaultValue = "20") int limit,
            @RequestAttribute(value = JwtAuthFilter.SESSION_ATTR, required = false) SessionService.Session session,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        String sid = session == null ? null : session.id();
        var jobs = marketplace.listJobsForSession(sid, limit);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("count", jobs.size());
        payload.put("limit", Math.min(100, Math.max(1, limit <= 0 ? 20 : limit)));
        payload.put("jobs", jobs);
        return Envelope.of("marketplace_install_jobs", payload, requestId);
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<Envelope<?>> job(
            @PathVariable("jobId") String jobId,
            @RequestAttribute(value = JwtAuthFilter.SESSION_ATTR, required = false) SessionService.Session session,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            HttpServletRequest request) {
        return authorizeJob(jobId, session, requestId, request, "GET")
                .orElseGet(() -> {
                    Optional<MarketplaceInstallJob> job = marketplace.job(jobId);
                    return ResponseEntity.ok(Envelope.of("marketplace_install_job", job.orElse(null), requestId));
                });
    }

    @DeleteMapping("/jobs/{jobId}")
    public ResponseEntity<Envelope<?>> cancelJob(
            @PathVariable("jobId") String jobId,
            @RequestAttribute(value = JwtAuthFilter.SESSION_ATTR, required = false) SessionService.Session session,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            HttpServletRequest request) {
        Optional<ResponseEntity<Envelope<?>>> denied = authorizeJob(jobId, session, requestId, request, "DELETE");
        if (denied.isPresent()) {
            return denied.get();
        }
        if (!marketplace.cancelJob(jobId)) {
            return ResponseEntity.status(404).body(
                    Envelope.of("error", new ErrorPayload("Unknown install job: " + jobId), requestId));
        }
        MarketplaceInstallJob job = marketplace.job(jobId).orElse(null);
        return ResponseEntity.ok(Envelope.of("marketplace_install_job", job, requestId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Envelope<?>> get(
            @PathVariable("id") String id,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        Optional<MarketplaceListing> listing = marketplace.get(id);
        if (listing.isEmpty()) {
            return ResponseEntity.status(404).body(
                    Envelope.of("error", new ErrorPayload("Unknown marketplace pack: " + id), requestId));
        }
        return ResponseEntity.ok(Envelope.of("marketplace_pack", listing.get(), requestId));
    }

    /**
     * Explicit async install path for typed SDKs (always HTTP 202 + job envelope).
     * Equivalent to {@code POST …/install?async=true}.
     */
    @PostMapping("/{id}/install-async")
    public ResponseEntity<Envelope<?>> installAsync(
            @PathVariable("id") String id,
            @RequestAttribute(value = JwtAuthFilter.SESSION_ATTR, required = false) SessionService.Session session,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return startAsync(id, session, requestId);
    }

    @PostMapping("/{id}/install")
    public ResponseEntity<Envelope<?>> install(
            @PathVariable("id") String id,
            @RequestParam(value = "async", defaultValue = "false") boolean async,
            @RequestAttribute(value = JwtAuthFilter.SESSION_ATTR, required = false) SessionService.Session session,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        if (async) {
            return startAsync(id, session, requestId);
        }
        MarketplaceService.InstallResult result = marketplace.install(id);
        if (!result.ok()) {
            int code = result.message() != null && result.message().startsWith("Unknown") ? 404 : 400;
            return ResponseEntity.status(code).body(
                    Envelope.of("error", new ErrorPayload(result.message()), requestId));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packId", result.packId());
        payload.put("alreadyInstalled", result.alreadyInstalled());
        payload.put("message", result.message());
        payload.put("marketplace", marketplace.list(null));
        return ResponseEntity.status(result.alreadyInstalled() ? 200 : 201)
                .body(Envelope.of("marketplace_install", payload, requestId));
    }

    private ResponseEntity<Envelope<?>> startAsync(
            String id, SessionService.Session session, String requestId) {
        String owner = sessionId(session);
        if (owner == null || owner.isBlank()) {
            return ResponseEntity.status(401).body(
                    Envelope.of("error", new ErrorPayload(
                            "Async marketplace install requires a session (Bearer JWT)."), requestId));
        }
        try {
            MarketplaceInstallJob job = marketplace.startInstallAsync(id, owner);
            return ResponseEntity.accepted()
                    .body(Envelope.of("marketplace_install_job", job, requestId));
        } catch (IllegalArgumentException e) {
            int code = e.getMessage() != null && e.getMessage().startsWith("Unknown") ? 404 : 400;
            return ResponseEntity.status(code).body(
                    Envelope.of("error", new ErrorPayload(e.getMessage()), requestId));
        }
    }

    /**
     * @return empty when authorized; otherwise a 404 response (no existence leak)
     */
    private Optional<ResponseEntity<Envelope<?>>> authorizeJob(
            String jobId,
            SessionService.Session session,
            String requestId,
            HttpServletRequest request,
            String method) {
        Optional<MarketplaceJobStore.JobRecord> rec = marketplace.jobRecord(jobId);
        if (rec.isEmpty()) {
            return Optional.of(ResponseEntity.status(404).body(
                    Envelope.of("error", new ErrorPayload("Unknown install job: " + jobId), requestId)));
        }
        String caller = sessionId(session);
        if (!rec.get().ownedBy(caller)) {
            String path = "/v2/marketplace/jobs/" + jobId;
            SecurityAudit.log(
                    "forbidden",
                    path,
                    RateLimitFilter.clientIp(request, false),
                    requestId,
                    "method=" + method
                            + " caller=" + (caller == null ? "none" : caller)
                            + " owner=" + nullToNone(rec.get().ownerSessionId()));
            // Same 404 as missing — do not confirm job existence to other tenants.
            return Optional.of(ResponseEntity.status(404).body(
                    Envelope.of("error", new ErrorPayload("Unknown install job: " + jobId), requestId)));
        }
        return Optional.empty();
    }

    private static String nullToNone(String s) {
        return s == null || s.isBlank() ? "none" : s;
    }

    private static String sessionId(SessionService.Session session) {
        return session == null ? null : session.id();
    }
}
