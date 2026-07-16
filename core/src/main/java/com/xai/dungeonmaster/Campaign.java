package com.xai.dungeonmaster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A flag-gated chain of quests — the story arc above individual quests
 * (ADR-001 Phase 2).
 *
 * A campaign is an ordered list of {@link QuestNode}s. A node is eligible
 * when its quest hasn't finished yet and every {@code requires} flag meets
 * its minimum in the {@link WorldState}. The engine plays the first eligible
 * node, applies the node's {@code grants} flags when its quest completes,
 * and repeats — so earlier choices (via SET_FLAG effects) decide which
 * quests exist on a given play-through.
 *
 * Pure data: packs ship campaigns as {@code campaigns/*.json}.
 */
public class Campaign {

    private final String id;
    private final String title;
    private final List<QuestNode> nodes;

    @JsonCreator
    public Campaign(
            @JsonProperty("id") String id,
            @JsonProperty("title") String title,
            @JsonProperty("nodes") List<QuestNode> nodes) {
        this.id = id != null ? id.trim() : "";
        this.title = (title != null && !title.isBlank()) ? title : this.id;
        this.nodes = nodes != null ? List.copyOf(nodes) : Collections.emptyList();
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public List<QuestNode> getNodes() { return nodes; }

    /**
     * First node whose quest hasn't finished and whose flag requirements the
     * world satisfies, or null when the arc is exhausted for this world.
     */
    public QuestNode nextEligible(WorldState world) {
        for (QuestNode node : nodes) {
            if (world != null && world.isQuestFinished(node.getQuestId())) continue;
            if (node.isEligible(world)) return node;
        }
        return null;
    }

    /** The node that plays {@code questId}, or null. */
    public QuestNode nodeFor(String questId) {
        if (questId == null) return null;
        for (QuestNode node : nodes) {
            if (questId.equals(node.getQuestId())) return node;
        }
        return null;
    }

    /** One arc step: a quest, its flag gate, and the flags it grants. */
    public static final class QuestNode {
        private final String questId;
        private final Map<String, Integer> requires;
        private final Map<String, Integer> grants;

        @JsonCreator
        public QuestNode(
                @JsonProperty("questId") String questId,
                @JsonProperty("requires") Map<String, Integer> requires,
                @JsonProperty("grants") Map<String, Integer> grants) {
            this.questId = questId != null ? questId.trim() : "";
            this.requires = requires != null ? Map.copyOf(requires) : Map.of();
            this.grants = grants != null ? Map.copyOf(grants) : Map.of();
        }

        public String getQuestId() { return questId; }
        public Map<String, Integer> getRequires() { return requires; }
        public Map<String, Integer> getGrants() { return grants; }

        /** Every required flag meets its minimum (empty requires = always). */
        public boolean isEligible(WorldState world) {
            for (Map.Entry<String, Integer> req : requires.entrySet()) {
                int actual = (world != null) ? world.getFlag(req.getKey()) : 0;
                if (actual < req.getValue()) return false;
            }
            return true;
        }

        /** Apply this node's granted flags to the world. */
        public void applyGrants(WorldState world) {
            if (world == null) return;
            for (Map.Entry<String, Integer> grant : grants.entrySet()) {
                world.addFlag(grant.getKey(), grant.getValue());
            }
        }
    }
}
