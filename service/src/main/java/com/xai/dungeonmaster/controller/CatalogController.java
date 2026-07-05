package com.xai.dungeonmaster.controller;

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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Read + light-write catalog of everything the engine has loaded — content
 * packs and plugins across all eight SPIs, plus the active narration backend.
 * Backs the in-game content-pack / mod browser.
 *
 * GET  /v2/catalog                     — full catalog envelope
 * POST /v2/catalog/packs/{id}/enable   — enable a content pack, returns the updated catalog
 * POST /v2/catalog/packs/{id}/disable  — disable a content pack, returns the updated catalog
 *
 * Sourced from the process-wide registries, so it reflects live state.
 */
@RestController
@RequestMapping("/v2/catalog")
@CrossOrigin(origins = "*")
public class CatalogController {

    @GetMapping
    public Envelope<CatalogPayload> catalog(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return Envelope.of("catalog", buildPayload(), requestId);
    }

    @PostMapping("/packs/{id}/enable")
    public ResponseEntity<Envelope<?>> enablePack(
            @PathVariable("id") String id,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return toggle(id, true, requestId);
    }

    @PostMapping("/packs/{id}/disable")
    public ResponseEntity<Envelope<?>> disablePack(
            @PathVariable("id") String id,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return toggle(id, false, requestId);
    }

    private ResponseEntity<Envelope<?>> toggle(String id, boolean enabled, String requestId) {
        if (!ContentRegistry.isKnown(id)) {
            return ResponseEntity.status(404).body(
                    Envelope.of("error", new ErrorPayload("Unknown content pack: " + id), requestId));
        }
        ContentRegistry.setEnabled(id, enabled);
        return ResponseEntity.ok(Envelope.of("catalog", buildPayload(), requestId));
    }

    private CatalogPayload buildPayload() {
        List<PackInfo> packs = new ArrayList<>();
        for (ContentPack pack : ContentRegistry.packs().values()) {
            packs.add(new PackInfo(
                    pack.id(),
                    pack.displayName(),
                    pack.version(),
                    pack.monsters().size(),
                    pack.items().size(),
                    ContentRegistry.isEnabled(pack.id())));
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
}
