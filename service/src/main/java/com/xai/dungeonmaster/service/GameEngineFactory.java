package com.xai.dungeonmaster.service;

import com.xai.dungeonmaster.Campaign;
import com.xai.dungeonmaster.CampaignRegistry;
import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.plugin.LLMProvider;
import com.xai.dungeonmaster.plugin.LLMProviderRegistry;
import com.xai.dungeonmaster.plugin.builtin.ModerationProvider;
import com.xai.dungeonmaster.plugin.builtin.TokenBudgetProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * Builds configured {@link DungeonMasterEngine} instances (difficulty, party,
 * narrator, campaign, WebSocket bridge). Content packs and plugins must already
 * be registered before the first call — {@code GameConfig} does that once at
 * startup.
 *
 * Used by {@link GameInstanceService} to mint an isolated engine per player
 * session so concurrent clients no longer share one mutable world.
 */
public final class GameEngineFactory {

    private final int difficulty;
    private final int chaos;
    private final String[] partyNames;
    private final String[] partyRoles;
    private final String campaignId;
    private final String narrationProviderId;
    private final int narrationTokenCeiling;
    private final SimpMessagingTemplate messaging;
    private volatile boolean bootstrappedActive;

    public GameEngineFactory(int difficulty, int chaos,
                             String[] partyNames, String[] partyRoles,
                             String campaignId,
                             String narrationProviderId, int narrationTokenCeiling,
                             SimpMessagingTemplate messaging) {
        this.difficulty = difficulty;
        this.chaos = chaos;
        this.partyNames = partyNames != null ? partyNames : new String[]{"Adventurer"};
        this.partyRoles = partyRoles != null ? partyRoles : new String[]{"Warrior"};
        this.campaignId = campaignId;
        this.narrationProviderId = narrationProviderId != null ? narrationProviderId : "local-stub";
        this.narrationTokenCeiling = Math.max(1, narrationTokenCeiling);
        this.messaging = messaging;
    }

    /**
     * Create a fully-wired engine. When {@code sessionId} is non-null, narrative
     * broadcasts go to {@code /topic/narrative/{sessionId}} so clients only hear
     * their own world. The process-default engine uses the legacy global topic.
     */
    public DungeonMasterEngine create(String sessionId) {
        DungeonMasterEngine engine = new DungeonMasterEngine(
                difficulty, chaos, partyNames, partyRoles);

        bootstrapActiveOnce();
        engine.setNarrator(buildNarrator());

        attachDefaultCampaign(engine);

        if (messaging != null) {
            String topic = (sessionId == null || sessionId.isBlank())
                    ? "/topic/narrative"
                    : "/topic/narrative/" + sessionId;
            engine.addUiListener(text -> messaging.convertAndSend(topic, text));
        }
        return engine;
    }

    /**
     * Budgeted + moderated stack around the current registry active provider.
     * Safe to call after ops switches the active id.
     */
    public LLMProvider buildNarrator() {
        bootstrapActiveOnce();
        return new TokenBudgetProvider(
                new ModerationProvider(LLMProviderRegistry.getActive()),
                narrationTokenCeiling);
    }

    /** Seed registry from config once; subsequent mints respect ops switches. */
    private void bootstrapActiveOnce() {
        if (bootstrappedActive) return;
        synchronized (this) {
            if (bootstrappedActive) return;
            LLMProviderRegistry.setActive(narrationProviderId);
            bootstrappedActive = true;
        }
    }

    /** Process-default engine (legacy single-player + unauthenticated v2). */

    /**
     * Goal G9 — attach configured campaign, else First Light arc when registered,
     * so cold-open → noon chains without ops config.
     */
    private void attachDefaultCampaign(DungeonMasterEngine engine) {
        String id = campaignId;
        if (id == null || id.isBlank()) {
            if (CampaignRegistry.get("first-light-arc") != null) {
                id = "first-light-arc";
            } else {
                return;
            }
        }
        Campaign campaign = CampaignRegistry.get(id);
        if (campaign != null) {
            engine.setCampaign(campaign);
        } else {
            System.err.println("[campaign] Unknown campaign id '" + id
                    + "' — starting without one.");
        }
    }

    public DungeonMasterEngine createDefault() {
        return create(null);
    }
}
