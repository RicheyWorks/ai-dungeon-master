package com.xai.dungeonmaster.config;

import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.plugin.PluginLoader;
import com.xai.dungeonmaster.plugin.SandboxPolicy;
import com.xai.dungeonmaster.service.GameEngineFactory;
import com.xai.dungeonmaster.service.GameInstanceService;
import com.xai.dungeonmaster.util.ResourceLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.nio.file.Paths;

/**
 * Boots content packs / plugins once, then exposes a {@link GameEngineFactory}
 * that mints configured engines, a process-default {@link DungeonMasterEngine}
 * (legacy single-player + GUI), and a {@link GameInstanceService} that isolates
 * authenticated v2 clients onto their own engines.
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

    /** Active campaign id (from a pack's campaigns/*.json); empty = no campaign. */
    @Value("${game.campaign.id:}")
    private String campaignId;

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

    /** Wall-clock timeout (ms) for plugin SPI calls (item/spell effects). */
    @Value("${game.plugins.call.timeout-ms:2000}")
    private long pluginCallTimeoutMs;

    /** Whether to run plugin SPI calls under the timeout guard. */
    @Value("${game.plugins.call.guard:true}")
    private boolean pluginCallGuard;

    /** Directory for per-session save files. */
    @Value("${game.saves.dir:saves}")
    private String savesDir;

    /** Idle TTL for per-session engines (seconds). 0 = never idle-evict. */
    @Value("${game.instances.idle-ttl-seconds:3600}")
    private long instanceIdleTtlSeconds;

    /** Max concurrent per-session engines (LRU eviction when exceeded). */
    @Value("${game.instances.max:100}")
    private int instanceMaxSessions;

    /** Auto-save a session engine to disk when it is evicted/destroyed. */
    @Value("${game.instances.save-on-evict:true}")
    private boolean instanceSaveOnEvict;

    /** On mint, load an existing per-session save if present (reconnect restore). */
    @Value("${game.instances.autoload:true}")
    private boolean instanceAutoload;

    @Bean
    public GameEngineFactory gameEngineFactory(SimpMessagingTemplate messaging) {
        // Runtime isolation: apply call-guard props before any plugin SPI may fire.
        System.setProperty(com.xai.dungeonmaster.plugin.PluginCallGuard.PROP_TIMEOUT_MS,
                Long.toString(Math.max(50L, pluginCallTimeoutMs)));
        System.setProperty(com.xai.dungeonmaster.plugin.PluginCallGuard.PROP_ENABLED,
                Boolean.toString(pluginCallGuard));

        // 1. Content packs once per process.
        int externalPacks = ResourceLoader.registerAllContentPacks(Paths.get(contentPacksDir));
        if (externalPacks > 0) {
            System.out.println("[plugins] Loaded " + externalPacks + " external content pack(s) from "
                    + contentPacksDir);
        }

        // 2. Code-bearing plugins once per process.
        PluginLoader.SignaturePolicy sigPolicy = parseSignaturePolicy(pluginSignaturePolicy);
        SandboxPolicy sandboxPolicy = pluginSandboxEnabled ? SandboxPolicy.defaults() : SandboxPolicy.disabled();
        PluginLoader.LoadReport report = PluginLoader.loadAll(Paths.get(pluginsDir), sigPolicy, sandboxPolicy);
        if (!report.loaded.isEmpty() || !report.failed.isEmpty() || !report.rejected.isEmpty()) {
            System.out.println("[plugins] " + report + " (signature policy: " + sigPolicy
                    + ", call-guard=" + pluginCallGuard + "/" + pluginCallTimeoutMs + "ms)");
            report.rejected.forEach(r -> System.err.println("[plugins] REJECTED " + r));
            report.failed.forEach(f -> System.err.println("[plugins] FAILED " + f));
        }

        return new GameEngineFactory(
                difficulty, chaos, partyNames, partyRoles,
                campaignId, narrationProviderId, narrationTokenCeiling,
                messaging);
    }

    @Bean
    public DungeonMasterEngine dungeonMasterEngine(GameEngineFactory factory) {
        return factory.createDefault();
    }

    @Bean
    public GameInstanceService gameInstanceService(GameEngineFactory factory,
                                                   DungeonMasterEngine defaultEngine) {
        GameInstanceService.Policy policy = new GameInstanceService.Policy(
                instanceIdleTtlSeconds, instanceMaxSessions, instanceSaveOnEvict, instanceAutoload);
        System.out.println("[game-instances] policy: idleTtl=" + policy.idleTtlSeconds()
                + "s max=" + policy.maxSessions()
                + " saveOnEvict=" + policy.saveOnEvict()
                + " autoload=" + policy.autoload());
        return new GameInstanceService(factory, defaultEngine, Paths.get(savesDir), policy);
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
