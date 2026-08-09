package com.xai.dungeonmaster.dto;

import com.xai.dungeonmaster.MemberState;

import java.util.List;

/**
 * Structured v2 game-status payload. Replaces the legacy
 * {@link GameStatusResponse} flat {@code partySummary} string with a typed
 * {@code party} array so native clients never parse server-formatted text.
 */
public record GameStatusV2(
        List<MemberState> party,
        int chaosLevel,
        boolean combatActive,
        List<String> availableChoices,
        List<String> recentHistory,
        QuestInfo quest,
        List<String> recentEvents,
        /** Current party location from the engine WorldMap. */
        String location,
        /** Rifts the party has discovered (includes starting + completed quests). */
        List<String> discoveredRifts,
        /** Rich choice rows (stakes / irreversible); parallel to availableChoices. */
        List<ChoiceDetail> choiceDetails
) {
    /** Backward-compatible constructor used by older tests. */
    public GameStatusV2(
            List<MemberState> party,
            int chaosLevel,
            boolean combatActive,
            List<String> availableChoices,
            List<String> recentHistory,
            QuestInfo quest,
            List<String> recentEvents,
            String location,
            List<String> discoveredRifts) {
        this(party, chaosLevel, combatActive, availableChoices, recentHistory,
                quest, recentEvents, location, discoveredRifts, List.of());
    }
}
