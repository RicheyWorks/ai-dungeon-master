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
        /** Goal G3 — last cinematic check (stakes → roll → result). */
        CheckResultDto lastCheck
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
                quest, recentEvents, location, discoveredRifts, List.of(), null, null);
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
                quest, recentEvents, location, discoveredRifts, choiceDetails, null, null);
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
                quest, recentEvents, location, discoveredRifts, choiceDetails, story, null);
    }
}
