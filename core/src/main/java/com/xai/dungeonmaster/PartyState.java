package com.xai.dungeonmaster;

import java.util.List;

/**
 * Immutable snapshot of the whole party as structured value objects.
 *
 * Returned by {@link DungeonMasterEngine#getPartyState()} and serialized
 * straight onto the v2 API envelope, replacing the legacy pipe-delimited
 * party string that clients previously had to parse.
 */
public record PartyState(List<MemberState> members) {
    public PartyState {
        members = members == null ? List.of() : List.copyOf(members);
    }

    /** Number of living members — convenient for clients and tests. */
    public long aliveCount() {
        return members.stream().filter(MemberState::alive).count();
    }
}
