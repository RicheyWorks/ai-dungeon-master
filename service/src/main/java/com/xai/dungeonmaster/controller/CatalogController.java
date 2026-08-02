package com.xai.dungeonmaster.controller;

import com.xai.dungeonmaster.auth.AdminAudit;
import com.xai.dungeonmaster.auth.JwtAuthFilter;
import com.xai.dungeonmaster.auth.RateLimitFilter;
import com.xai.dungeonmaster.auth.SessionService;
import com.xai.dungeonmaster.dto.CatalogPayload;
import com.xai.dungeonmaster.dto.Envelope;
import com.xai.dungeonmaster.dto.ErrorPayload;
import com.xai.dungeonmaster.dto.NarrationInfo;
import com.xai.dungeonmaster.dto.PackInfo;
import com.xai.dungeonmaster.dto.PluginSummary;
import com.xai.dungeonmaster.plugin.ContentPack;
import com.xai.dungeonmaster.plugin.ContentRegistry;
import com.xai.dungeonmaster.plugin.EncounterTableRegistry;
import com.xai.dungeonmaster.plugin.ItemEffectRegistry;
import com.xai.dungeonmaster.plugin.LLMProvider;
import com.xai.dungeonmaster.plugin.LLMProviderRegistry;
import com.xai.dungeonmaster.plugin.LootTableRegistry;
import com.xai.dungeonmaster.plugin.QuestScriptRegistry;
import com.xai.dungeonmaster.plugin.SpellEffectRegistry;
import com.xai.dungeonmaster.plugin.StorefrontRegistry;
import com.xai.dungeonmaster.content.SessionPackService;
import com.xai.dungeonmaster.service.PackEntitlementGate;
import com.xai.dungeonmaster.service.PackUploadService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Read + light-write catalog of everything the engine has loaded — content
 * packs and plugins across all eight SPIs, plus the active narration backend.
 * Backs the in-game content-pack / mod browser.
 *
 * GET  /v2/catalog                     — full catalog envelope
 * POST /v2/catalog/packs               — upload + install a content-pack zip (multipart "file")
 * POST /v2/catalog/packs/{id}/enable   — enable a content pack (entitlement-gated when required)
 * POST /v2/catalog/packs/{id}/disable  — disable a content pack, returns the updated catalog
 */
@RestController
@RequestMapping("/v2/catalog")
public class CatalogController {

    private final PackUploadService uploads;
    private final PackEntitlementGate packGate;
    private final SessionPackService sessionPacks;
    private final boolean uploadEnabled;
    private final boolean uploadRequireAdmin;
    private final String adminToken;
    private final String previousAdminToken;

    @org.springframework.beans.factory.annotation.Autowired
    public CatalogController(
            PackUploadService uploads,
            PackEntitlementGate packGate,
            SessionPackService sessionPacks,
            @Value("${game.catalog.upload.enabled:true}") boolean uploadEnabled,
            @Value("${game.catalog.upload.require-admin:false}") boolean uploadRequireAdmin,
            @Value("${game.admin.token:}") String adminToken,
            @Value("${game.admin.token.previous:}") String previousAdminToken) {
        this.uploads = uploads;
        this.packGate = packGate;
        this.sessionPacks = sessionPacks != null ? sessionPacks : new SessionPackService();
        this.uploadEnabled = uploadEnabled;
        this.uploadRequireAdmin = uploadRequireAdmin;
        this.adminToken = adminToken == null ? "" : adminToken.trim();
        this.previousAdminToken = previousAdminToken == null ? "" : previousAdminToken.trim();
    }

    /** Test helper without session pack service (upload open). */
    public CatalogController(PackUploadService uploads, PackEntitlementGate packGate) {
        this(uploads, packGate, new SessionPackService(), true, false, "", "");
    }

    /** Test helper with session packs. */
    public CatalogController(PackUploadService uploads, PackEntitlementGate packGate, SessionPackService sessionPacks) {
        this(uploads, packGate, sessionPacks, true, false, "", "");
    }

    @GetMapping
    public Envelope<CatalogPayload> catalog(
            @RequestAttribute(value = JwtAuthFilter.SESSION_ATTR, required = false) SessionService.Session session,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return Envelope.of("catalog", buildPayload(session == null ? null : session.id()), requestId);
    }

    @PostMapping(value = "/packs", consumes = "multipart/form-data")
    public ResponseEntity<Envelope<?>> uploadPack(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value = "replace", defaultValue = "false") boolean replace,
            @RequestAttribute(value = JwtAuthFilter.SESSION_ATTR, required = false) SessionService.Session session,
            @RequestHeader(value = "X-Admin-Token", required = false) String adminHeader,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            HttpServletRequest request) {
        String ip = request == null ? "-" : RateLimitFilter.clientIp(request, false);
        if (!uploadEnabled) {
            AdminAudit.log("disabled", "/v2/catalog/packs", ip, requestId, "upload_disabled");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    Envelope.of("error", new ErrorPayload(
                            "Pack upload is disabled (game.catalog.upload.enabled=false)."), requestId));
        }
        if (uploadRequireAdmin && !adminAccepted(adminHeader)) {
            AdminAudit.log("unauthorized", "/v2/catalog/packs", ip, requestId,
                    "token=" + AdminAudit.tokenFingerprint(adminHeader));
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    Envelope.of("error", new ErrorPayload(
                            "Pack upload requires a valid X-Admin-Token."), requestId));
        }
        try {
            PackUploadService.InstalledPack installed = uploads.install(file.getBytes(), replace);
            // Gated packs stay disabled until the session owns the SKU and enables them.
            if (packGate.isGated(installed.pack().id())) {
                ContentRegistry.setEnabled(installed.pack().id(), false);
            }
            AdminAudit.log("ok", "/v2/catalog/packs", ip, requestId,
                    "packId=" + installed.pack().id()
                            + " replaced=" + installed.replaced()
                            + " token=" + AdminAudit.tokenFingerprint(adminHeader));
            return ResponseEntity.status(installed.replaced() ? 200 : 201)
                    .body(Envelope.of("catalog", buildPayload(session == null ? null : session.id()), requestId));
        } catch (PackUploadService.PackUploadException e) {
            return ResponseEntity.status(e.isConflict() ? 409 : 400)
                    .body(Envelope.of("error", new ErrorPayload(e.getMessage()), requestId));
        } catch (java.io.IOException e) {
            return ResponseEntity.badRequest()
                    .body(Envelope.of("error", new ErrorPayload("Unreadable upload: " + e.getMessage()), requestId));
        }
    }

    @PostMapping("/packs/{id}/enable")
    public ResponseEntity<Envelope<?>> enablePack(
            @PathVariable("id") String id,
            @RequestAttribute(value = JwtAuthFilter.SESSION_ATTR, required = false) SessionService.Session session,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return toggle(id, true, session, requestId);
    }

    @PostMapping("/packs/{id}/disable")
    public ResponseEntity<Envelope<?>> disablePack(
            @PathVariable("id") String id,
            @RequestAttribute(value = JwtAuthFilter.SESSION_ATTR, required = false) SessionService.Session session,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return toggle(id, false, session, requestId);
    }

    private ResponseEntity<Envelope<?>> toggle(
            String id, boolean enabled, SessionService.Session session, String requestId) {
        if (!ContentRegistry.isKnown(id)) {
            return ResponseEntity.status(404).body(
                    Envelope.of("error", new ErrorPayload("Unknown content pack: " + id), requestId));
        }
        if (enabled) {
            String deny = packGate.denyReason(session == null ? null : session.id(), id);
            if (deny != null) {
                HttpStatus code = deny.startsWith("Authentication")
                        ? HttpStatus.UNAUTHORIZED
                        : HttpStatus.PAYMENT_REQUIRED;
                return ResponseEntity.status(code).body(
                        Envelope.of("error", new ErrorPayload(deny), requestId));
            }
        }
        String sid = session == null ? null : session.id();
        sessionPacks.setEnabled(sid, id, enabled);
        return ResponseEntity.ok(Envelope.of(
                "catalog", buildPayload(sid), requestId));
    }

    private CatalogPayload buildPayload(String sessionId) {
        List<PackInfo> packs = new ArrayList<>();
        for (ContentPack pack : ContentRegistry.packs().values()) {
            List<String> required = pack.requiredProductIds() == null
                    ? List.of()
                    : pack.requiredProductIds();
            boolean locked = packGate.gatesEnabled()
                    && !required.isEmpty()
                    && !packGate.isEntitled(sessionId, pack.id());
            packs.add(new PackInfo(
                    pack.id(),
                    pack.displayName(),
                    pack.version(),
                    pack.monsters().size(),
                    pack.items().size(),
                    sessionPacks.isEnabled(sessionId, pack.id()),
                    required,
                    locked));
        }
        packs.sort((a, b) -> a.id().compareToIgnoreCase(b.id()));

        PluginSummary plugins = new PluginSummary(
                sorted(SpellEffectRegistry.registeredIds()),
                sorted(ItemEffectRegistry.registeredIds()),
                sorted(EncounterTableRegistry.registeredBiomes()),
                sorted(LootTableRegistry.registeredBiomes()),
                sorted(QuestScriptRegistry.registeredIds()),
                sorted(StorefrontRegistry.registeredIds()),
                sorted(LLMProviderRegistry.registeredIds()));

        LLMProvider active = LLMProviderRegistry.getActive();
        NarrationInfo narration = new NarrationInfo(
                active.id(),
                active.health().name(),
                sorted(LLMProviderRegistry.registeredIds()));

        return new CatalogPayload(packs, plugins, narration);
    }

    private static List<String> sorted(Collection<String> ids) {
        List<String> list = new ArrayList<>(ids);
        list.sort(String.CASE_INSENSITIVE_ORDER);
        return list;
    }

    private boolean adminAccepted(String presented) {
        if (presented == null || presented.isBlank()) return false;
        String t = presented.trim();
        if (tokenOk(adminToken, t)) return true;
        return !previousAdminToken.isEmpty() && tokenOk(previousAdminToken, t);
    }

    private static boolean tokenOk(String expected, String actual) {
        if (expected == null || expected.isEmpty() || actual == null) return false;
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = actual.getBytes(StandardCharsets.UTF_8);
        if (a.length != b.length) {
            MessageDigest.isEqual(a, a);
            return false;
        }
        return MessageDigest.isEqual(a, b);
    }
}
