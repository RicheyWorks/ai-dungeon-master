package com.xai.dungeonmaster.dto;

import com.xai.dungeonmaster.MemberState;

import java.util.List;

/**
 * Structured v2 game-status payload.
 */
public record GameStatusV2(
        List<MemberState> party,
        int chaosLevel,
        boolean combatActive,
        List<String> availableChoices,
        List<String> recentHistory,
        QuestInfo quest,
        List<String> recentEvents,
        String location,
        List<String> discoveredRifts,
        List<ChoiceDetail> choiceDetails,
        StoryMemoryPayload story,
        CheckResultDto lastCheck,
        /** Active campaign id, if any (G9). */
        String campaignId,
        /** Human campaign title. */
        String campaignTitle,
        /** Short next-step hint for SPA empty / first-run states. */
        String playHint
) {
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
                quest, recentEvents, location, discoveredRifts, List.of(), null, null,
                null, null, null);
    }

    public GameStatusV2(
            List<MemberState> party,
            int chaosLevel,
            boolean combatActive,
            List<String> availableChoices,
            List<String> recentHistory,
            QuestInfo quest,
            List<String> recentEvents,
            String location,
            List<String> discoveredRifts,
            List<ChoiceDetail> choiceDetails) {
        this(party, chaosLevel, combatActive, availableChoices, recentHistory,
                quest, recentEvents, location, discoveredRifts, choiceDetails, null, null,
                null, null, null);
    }

    public GameStatusV2(
            List<MemberState> party,
            int chaosLevel,
            boolean combatActive,
            List<String> availableChoices,
            List<String> recentHistory,
            QuestInfo quest,
            List<String> recentEvents,
            String location,
            List<String> discoveredRifts,
            List<ChoiceDetail> choiceDetails,
            StoryMemoryPayload story) {
        this(party, chaosLevel, combatActive, availableChoices, recentHistory,
                quest, recentEvents, location, discoveredRifts, choiceDetails, story, null,
                null, null, null);
    }

    public GameStatusV2(
            List<MemberState> party,
            int chaosLevel,
            boolean combatActive,
            List<String> availableChoices,
            List<String> recentHistory,
            QuestInfo quest,
            List<String> recentEvents,
            String location,
            List<String> discoveredRifts,
            List<ChoiceDetail> choiceDetails,
            StoryMemoryPayload story,
            CheckResultDto lastCheck) {
        this(party, chaosLevel, combatActive, availableChoices, recentHistory,
                quest, recentEvents, location, discoveredRifts, choiceDetails, story, lastCheck,
                null, null, null);
    }
}
