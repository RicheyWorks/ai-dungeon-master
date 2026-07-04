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
        List<String> recentHistory
) {}
