package com.xai.dungeonmaster.config;

import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.plugin.PluginLoader;
import com.xai.dungeonmaster.plugin.SandboxPolicy;
import com.xai.dungeonmaster.plugin.LLMProvider;
import com.xai.dungeonmaster.plugin.LLMProviderRegistry;
import com.xai.dungeonmaster.plugin.builtin.ModerationProvider;
import com.xai.dungeonmaster.plugin.builtin.TokenBudgetProvider;
import com.xai.dungeonmaster.util.ResourceLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.nio.file.Paths;

/**
 * Builds the DungeonMasterEngine as a singleton Spring bean.
 *
 * Party composition and difficulty come from application.properties so you
 * never need to recompile to tweak the game parameters.
 *
 * Startup sequence (in this order — order matters):
 *   1. Register the bundled content pack + any external content packs found
 *      under {@code game.content.packs.dir} (defaults to "content-packs").
 *   2. Discover and load code-bearing plugins under {@code game.plugins.dir}
 *      (defaults to "plugins"). Each plugin registers SpellEffects /
 *      ItemEffects / ContentPacks before any engine code runs.
 *   3. Construct the DungeonMasterEngine. By the time it generates its
 *      first quest, all plugin content is already in the registries.
 *   4. Wire the WebSocket SimpMessagingTemplate as a listener so every
 *      engine.broadcast() pushes to /topic/narrative.
 */
@Configuration
public class GameConfig {

    @Value("${game.difficulty:4}")
    private int difficulty;

    @Value("${game.chaos:4}")
    private int chaos;

    /** Comma-separated, e.g. "Kael,Lira" */
    @Value("${game.party.names:Kael,Lira}")
    private String[] partyNames;

    /** Comma-separated, index-matched to partyNames, e.g. "Warrior,Mage" */
    @Value("${game.party.roles:Warrior,Mage}")
    private String[] partyRoles;

    /** Root directory for content packs. Resolved relative to the working dir. */
    @Value("${game.content.packs.dir:content-packs}")
    private String contentPacksDir;

    /** Root directory for code-bearing plugin JARs. Resolved relative to the working dir. */
    @Value("${game.plugins.dir:plugins}")
    private String pluginsDir;

    /** Per-session narration token ceiling for the cost guardrail. */
    @Value("${game.narration.token.ceiling:4000}")
    private int narrationTokenCeiling;

    /** Active narration provider id (defaults to the offline local stub). */
    @Value("${game.narration.provider:local-stub}")
    private String narrationProviderId;

    /** Signature policy for code-bearing plugin JARs: LENIENT, REQUIRED, or DISABLED. */
    @Value("${game.plugins.signature.policy:LENIENT}")
    private String pluginSignaturePolicy;

    /** Whether to sandbox-scan plugin bytecode (reject blocked-API references) before loading. */
    @Value("${game.plugins.sandbox.enabled:true}")
    private boolean pluginSandboxEnabled;

    @Bean
    public DungeonMasterEngine dungeonMasterEngine(SimpMessagingTemplate messaging) {

        // 1. Content packs: bundled first, then any external packs on disk.
        int externalPacks = ResourceLoader.registerAllContentPacks(Paths.get(contentPacksDir));
        if (externalPacks > 0) {
            System.out.println("[plugins] Loaded " + externalPacks + " external content pack(s) from "
                    + contentPacksDir);
        }

        // 2. Code-bearing plugins (mods). Verify JAR signatures per the
        //    configured policy before any plugin code is loaded.
        PluginLoader.SignaturePolicy sigPolicy = parseSignaturePolicy(pluginSignaturePolicy);
        SandboxPolicy sandboxPolicy = pluginSandboxEnabled ? SandboxPolicy.defaults() : SandboxPolicy.disabled();
        PluginLoader.LoadReport report = PluginLoader.loadAll(Paths.get(pluginsDir), sigPolicy, sandboxPolicy);
        if (!report.loaded.isEmpty() || !report.failed.isEmpty() || !report.rejected.isEmpty()) {
            System.out.println("[plugins] " + report + " (signature policy: " + sigPolicy + ")");
            report.rejected.forEach(r -> System.err.println("[plugins] REJECTED " + r));
            report.failed.forEach(f -> System.err.println("[plugins] FAILED " + f));
        }

        // 3. Engine.
        DungeonMasterEngine engine = new DungeonMasterEngine(
                difficulty, chaos, partyNames, partyRoles);

        // 3.5 Narration provider: registry-selected backend wrapped in the
        //     cost-guardrail + moderation decorators. The offline stub is the
        //     default until a keyed provider (OpenAI/Anthropic/xAI) is added.
        LLMProviderRegistry.setActive(narrationProviderId);
        LLMProvider narrator = new TokenBudgetProvider(
                new ModerationProvider(LLMProviderRegistry.getActive()),
                narrationTokenCeiling);
        engine.setNarrator(narrator);

        // 4. WebSocket bridge — addUiListener so subsequent listeners (Swing) coexist.
        engine.addUiListener(text -> messaging.convertAndSend("/topic/narrative", text));

        return engine;
    }

    /** Parse the configured signature policy, defaulting to LENIENT on anything unrecognised. */
    private static PluginLoader.SignaturePolicy parseSignaturePolicy(String raw) {
        if (raw == null || raw.isBlank()) {
            return PluginLoader.SignaturePolicy.LENIENT;
        }
        try {
            return PluginLoader.SignaturePolicy.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            System.err.println("[plugins] Unknown signature policy '" + raw + "', defaulting to LENIENT.");
            return PluginLoader.SignaturePolicy.LENIENT;
        }
    }
}
