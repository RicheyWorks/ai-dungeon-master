package com.xai.dungeonmaster.controller;

import com.xai.dungeonmaster.auth.JwtAuthFilter;
import com.xai.dungeonmaster.auth.SessionService;
import com.xai.dungeonmaster.dto.EntitlementPayload;
import com.xai.dungeonmaster.dto.Envelope;
import com.xai.dungeonmaster.dto.ErrorPayload;
import com.xai.dungeonmaster.dto.VerifyReceiptRequest;
import com.xai.dungeonmaster.entitlement.EntitlementService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;

/**
 * Player entitlements from validated store purchases.
 *
 * POST /v2/entitlements/verify — validate a receipt via the storefront plugin and grant the product
 * GET  /v2/entitlements        — list the caller's owned products
 *
 * Both are session-scoped: the authenticated session comes from the request
 * attribute set by {@link JwtAuthFilter}. Verification failures return 402.
 */
@RestController
@RequestMapping("/v2/entitlements")
@CrossOrigin(origins = "*")
public class EntitlementController {

    private final EntitlementService entitlements;

    public EntitlementController(EntitlementService entitlements) {
        this.entitlements = entitlements;
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
                new ArrayList<>(entitlements.entitlements(session.id())));
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
}
