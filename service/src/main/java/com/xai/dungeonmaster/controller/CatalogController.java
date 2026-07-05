package com.xai.dungeonmaster.controller;

import com.xai.dungeonmaster.dto.CatalogPayload;
import com.xai.dungeonmaster.dto.Envelope;
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
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Read-only catalog of everything the engine has loaded — content packs and
 * plugins across all eight SPIs, plus the active narration backend. Backs an
 * in-game content-pack / mod browser.
 *
 * GET /v2/catalog → envelope { type: "catalog", payload: { contentPacks, plugins, narration } }
 *
 * Sourced entirely from the process-wide registries, so it needs no engine
 * instance and reflects live state (built-ins plus anything packs/mods added).
 */
@RestController
@RequestMapping("/v2/catalog")
@CrossOrigin(origins = "*")
public class CatalogController {

    @GetMapping
    public Envelope<CatalogPayload> catalog(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {

        List<PackInfo> packs = new ArrayList<>();
        for (ContentPack pack : ContentRegistry.packs().values()) {
            packs.add(new PackInfo(
                    pack.id(),
                    pack.displayName(),
                    pack.version(),
                    pack.monsters().size(),
                    pack.items().size()));
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

        return Envelope.of("catalog", new CatalogPayload(packs, plugins, narration), requestId);
    }

    private static List<String> sorted(Collection<String> ids) {
        List<String> list = new ArrayList<>(ids);
        list.sort(String.CASE_INSENSITIVE_ORDER);
        return list;
    }
}
