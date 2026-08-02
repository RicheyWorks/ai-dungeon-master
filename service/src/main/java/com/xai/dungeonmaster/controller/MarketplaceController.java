package com.xai.dungeonmaster.controller;

import com.xai.dungeonmaster.dto.Envelope;
import com.xai.dungeonmaster.dto.ErrorPayload;
import com.xai.dungeonmaster.dto.MarketplaceInstallJob;
import com.xai.dungeonmaster.dto.MarketplaceListing;
import com.xai.dungeonmaster.dto.MarketplacePayload;
import com.xai.dungeonmaster.service.MarketplaceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
 *   <li>{@code GET  /v2/marketplace/jobs/{jobId}} — install progress</li>
 *   <li>{@code DELETE /v2/marketplace/jobs/{jobId}} — cancel install</li>
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

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<Envelope<?>> job(
            @PathVariable("jobId") String jobId,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        Optional<MarketplaceInstallJob> job = marketplace.job(jobId);
        if (job.isEmpty()) {
            return ResponseEntity.status(404).body(
                    Envelope.of("error", new ErrorPayload("Unknown install job: " + jobId), requestId));
        }
        return ResponseEntity.ok(Envelope.of("marketplace_install_job", job.get(), requestId));
    }

    @DeleteMapping("/jobs/{jobId}")
    public ResponseEntity<Envelope<?>> cancelJob(
            @PathVariable("jobId") String jobId,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
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

    @PostMapping("/{id}/install")
    public ResponseEntity<Envelope<?>> install(
            @PathVariable("id") String id,
            @RequestParam(value = "async", defaultValue = "false") boolean async,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        if (async) {
            try {
                MarketplaceInstallJob job = marketplace.startInstallAsync(id);
                return ResponseEntity.accepted()
                        .body(Envelope.of("marketplace_install_job", job, requestId));
            } catch (IllegalArgumentException e) {
                int code = e.getMessage() != null && e.getMessage().startsWith("Unknown") ? 404 : 400;
                return ResponseEntity.status(code).body(
                        Envelope.of("error", new ErrorPayload(e.getMessage()), requestId));
            }
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
}
