package com.xai.dungeonmaster.config;

import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.plugin.PluginLoader;
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

    @Bean
    public DungeonMasterEngine dungeonMasterEngine(SimpMessagingTemplate messaging) {

        // 1. Content packs: bundled first, then any external packs on disk.
        int externalPacks = ResourceLoader.registerAllContentPacks(Paths.get(contentPacksDir));
        if (externalPacks > 0) {
            System.out.println("[plugins] Loaded " + externalPacks + " external content pack(s) from "
                    + contentPacksDir);
        }

        // 2. Code-bearing plugins (mods).
        PluginLoader.LoadReport report = PluginLoader.loadAll(Paths.get(pluginsDir));
        if (!report.loaded.isEmpty() || !report.failed.isEmpty()) {
            System.out.println("[plugins] " + report);
            report.failed.forEach(f -> System.err.println("[plugins] FAILED " + f));
        }

        // 3. Engine.
        DungeonMasterEngine engine = new DungeonMasterEngine(
                difficulty, chaos, partyNames, partyRoles);

        // 4. WebSocket bridge — addUiListener so subsequent listeners (Swing) coexist.
        engine.addUiListener(text -> messaging.convertAndSend("/topic/narrative", text));

        return engine;
    }
}
