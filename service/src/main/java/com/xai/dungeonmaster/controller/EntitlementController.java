package com.xai.dungeonmaster.controller;

import com.xai.dungeonmaster.auth.JwtAuthFilter;
import com.xai.dungeonmaster.auth.SessionService;
import com.xai.dungeonmaster.dto.EntitlementPayload;
import com.xai.dungeonmaster.dto.Envelope;
import com.xai.dungeonmaster.dto.ErrorPayload;
import com.xai.dungeonmaster.dto.VerifyReceiptRequest;
import com.xai.dungeonmaster.entitlement.EntitlementService;
import com.xai.dungeonmaster.plugin.StorefrontIntegration;
import com.xai.dungeonmaster.plugin.StorefrontRegistry;
import com.xai.dungeonmaster.plugin.builtin.AppStoreStorefront;
import com.xai.dungeonmaster.plugin.builtin.GooglePlayStorefront;
import com.xai.dungeonmaster.plugin.builtin.SteamStorefront;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Player entitlements from validated store purchases.
 *
 * POST /v2/entitlements/verify — validate a receipt via the storefront plugin and grant the product
 * GET  /v2/entitlements        — list the caller's owned products
 * GET  /v2/entitlements/storefronts — registered storefronts + live/sandbox mode
 */
@RestController
@RequestMapping("/v2/entitlements")
@CrossOrigin(origins = "*")
public class EntitlementController {

    private final EntitlementService entitlements;

    public EntitlementController(EntitlementService entitlements) {
        this.entitlements = entitlements;
    }

    @GetMapping("/storefronts")
    public Envelope<Map<String, Object>> storefronts(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (String id : StorefrontRegistry.registeredIds().stream().sorted().toList()) {
            StorefrontIntegration s = StorefrontRegistry.get(id);
            if (s == null) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", s.id());
            row.put("displayName", s.displayName());
            row.put("live", isLive(s));
            list.add(row);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("storefronts", list);
        payload.put("active", StorefrontRegistry.getActive().id());
        return Envelope.of("storefronts", payload, requestId);
    }

    @PostMapping("/verify")
    public ResponseEntity<Envelope<?>> verify(
            @RequestBody(required = false) VerifyReceiptRequest req,
            @RequestAttribute(value = JwtAuthFilter.SESSION_ATTR, required = false) SessionService.Session session,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {

        if (session == null) {
            return ResponseEntity.status(401).body(
                    Envelope.of("error", new ErrorPayload("Authentication required."), requestId));
        }
        if (req == null || req.productId() == null || req.productId().isBlank()) {
            return ResponseEntity.badRequest().body(
                    Envelope.of("error", new ErrorPayload("productId and receipt are required."), requestId));
        }

        EntitlementService.Grant g = entitlements.verifyAndGrant(
                session.id(), req.storefront(), req.productId(), req.receipt());
        EntitlementPayload payload = new EntitlementPayload(
                g.granted(), g.productId(), g.storefront(), g.reason(),
                new ArrayList<>(entitlements.entitlements(session.id())),
                g.enabledPacks() == null ? List.of() : g.enabledPacks());
        HttpStatus code = g.granted() ? HttpStatus.OK : HttpStatus.PAYMENT_REQUIRED;
        return ResponseEntity.status(code).body(Envelope.of("entitlement", payload, requestId));
    }

    @GetMapping
    public ResponseEntity<Envelope<?>> list(
            @RequestAttribute(value = JwtAuthFilter.SESSION_ATTR, required = false) SessionService.Session session,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {

        if (session == null) {
            return ResponseEntity.status(401).body(
                    Envelope.of("error", new ErrorPayload("Authentication required."), requestId));
        }
        EntitlementPayload payload = new EntitlementPayload(
                true, null, null, "ok", new ArrayList<>(entitlements.entitlements(session.id())));
        return ResponseEntity.ok(Envelope.of("entitlements", payload, requestId));
    }

    private static boolean isLive(StorefrontIntegration s) {
        if (s instanceof GooglePlayStorefront g) return g.isLive();
        if (s instanceof AppStoreStorefront a) return a.isLive();
        if (s instanceof SteamStorefront st) return st.isLive();
        return false;
    }
}
