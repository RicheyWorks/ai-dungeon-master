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

        LLMProviderRegistry.setActive(narrationProviderId);
        LLMProvider narrator = new TokenBudgetProvider(
                new ModerationProvider(LLMProviderRegistry.getActive()),
                narrationTokenCeiling);
        engine.setNarrator(narrator);

        if (campaignId != null && !campaignId.isBlank()) {
            Campaign campaign = CampaignRegistry.get(campaignId);
            if (campaign != null) {
                engine.setCampaign(campaign);
            } else {
                System.err.println("[campaign] Unknown campaign id '" + campaignId
                        + "' — starting without one.");
            }
        }

        if (messaging != null) {
            String topic = (sessionId == null || sessionId.isBlank())
                    ? "/topic/narrative"
                    : "/topic/narrative/" + sessionId;
            engine.addUiListener(text -> messaging.convertAndSend(topic, text));
        }
        return engine;
    }

    /** Process-default engine (legacy single-player + unauthenticated v2). */
    public DungeonMasterEngine createDefault() {
        return create(null);
    }
}
