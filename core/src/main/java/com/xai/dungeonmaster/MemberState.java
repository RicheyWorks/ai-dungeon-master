package com.xai.dungeonmaster;

import java.util.List;

/**
 * Immutable, client-facing snapshot of a single party member's public state.
 *
 * This is the structured replacement for the ad-hoc text the engine used to
 * expose via {@link DungeonMasterEngine#getPartySummary()}. It carries only
 * what a UI needs to render a member — no engine internals and no mutable
 * reference back into the live {@link Adventurer}.
 */
public record MemberState(
        String name,
        String role,
        int level,
        int hp,
        int maxHp,
        int mana,
        int maxMana,
        int armorClass,
        boolean alive,
        boolean ascendant,
        List<String> statuses
) {
    public MemberState {
        // Defensive copy so the snapshot can never be mutated through the
        // caller's list, and statuses is never null on the wire.
        statuses = statuses == null ? List.of() : List.copyOf(statuses);
    }
}
