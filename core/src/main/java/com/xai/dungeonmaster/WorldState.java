package com.xai.dungeonmaster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent narrative world state (ADR-001 Phase 2).
 *
 * The single source of story truth outside the current quest: integer flags
 * and counters set by choice effects, and the outcome log of every finished
 * quest. Campaign nodes gate on flags; the Chronicle (Phase 3) will read the
 * outcome log. Serialized inside {@code GameStateData}, so world state
 * survives save/load like the party does.
 */
public class WorldState {

    private final Map<String, Integer> flags;
    private final List<QuestOutcome> questOutcomes;

    public WorldState() {
        this.flags = new ConcurrentHashMap<>();
        this.questOutcomes = Collections.synchronizedList(new ArrayList<>());
    }

    @JsonCreator
    public WorldState(
            @JsonProperty("flags") Map<String, Integer> flags,
            @JsonProperty("questOutcomes") List<QuestOutcome> questOutcomes) {
        this.flags = new ConcurrentHashMap<>(flags != null ? flags : Map.of());
        this.questOutcomes = Collections.synchronizedList(
                new ArrayList<>(questOutcomes != null ? questOutcomes : List.of()));
    }

    // ─── Flags ───────────────────────────────────────────────────────────────

    /** Set a flag to an exact value. */
    public void setFlag(String key, int value) {
        if (key != null && !key.isBlank()) flags.put(key.trim(), value);
    }

    /** Add a delta to a flag (missing flags start at 0). */
    public void addFlag(String key, int delta) {
        if (key != null && !key.isBlank()) flags.merge(key.trim(), delta, Integer::sum);
    }

    /** Current flag value; 0 when unset. */
    public int getFlag(String key) {
        if (key == null) return 0;
        return flags.getOrDefault(key.trim(), 0);
    }

    /** True when the flag's value is at least {@code min}. */
    public boolean hasFlag(String key, int min) {
        return getFlag(key) >= min;
    }

    public Map<String, Integer> getFlags() {
        return Collections.unmodifiableMap(flags);
    }

    // ─── Quest outcomes ─────────────────────────────────────────────────────

    /** Record that a quest finished (completed or failed). */
    public void recordQuestOutcome(String questId, boolean completed, String detail) {
        if (questId == null || questId.isBlank()) return;
        questOutcomes.add(new QuestOutcome(questId, completed, detail));
    }

    /** True if a quest with this id has ever finished (either way). */
    @JsonIgnore
    public boolean isQuestFinished(String questId) {
        if (questId == null) return false;
        synchronized (questOutcomes) {
            return questOutcomes.stream().anyMatch(o -> questId.equals(o.getQuestId()));
        }
    }

    public List<QuestOutcome> getQuestOutcomes() {
        return Collections.unmodifiableList(questOutcomes);
    }

    /** One finished quest: id, whether it completed (vs failed), free detail. */
    public static final class QuestOutcome {
        private final String questId;
        private final boolean completed;
        private final String detail;

        @JsonCreator
        public QuestOutcome(
                @JsonProperty("questId") String questId,
                @JsonProperty("completed") boolean completed,
                @JsonProperty("detail") String detail) {
            this.questId = questId;
            this.completed = completed;
            this.detail = detail != null ? detail : "";
        }

        public String getQuestId() { return questId; }
        public boolean isCompleted() { return completed; }
        public String getDetail() { return detail; }
    }
}
