package com.xai.dungeonmaster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Structured narrative memory (ADR-001 Phase 3).
 *
 * Records typed story events — quests begun and finished, bosses slain,
 * battles won, the party falling — at the same milestones the engine already
 * broadcasts as prose. Unlike the raw turn-history strings, events are
 * bounded and compactable, so they can feed the narrator as prompt context
 * without unbounded token growth:
 *
 * - the most recent {@link #MAX_RECENT} events are kept verbatim;
 * - older events roll up into a per-type tally ("6 foes slain earlier");
 * - {@link #renderFacts} turns both into a short, deterministic fact list.
 *
 * No LLM is needed for compaction, so offline (local-stub) sessions get the
 * same memory as keyed ones. Serialized inside {@code GameStateData}.
 */
public class Chronicle {

    /** Events kept verbatim before rolling into the tally. */
    public static final int MAX_RECENT = 20;

    /** Hard cap on rendered fact strings, independent of caller's ask. */
    public static final int MAX_FACTS = 8;

    /** Rendered facts are clipped to this many characters. */
    public static final int MAX_FACT_LENGTH = 120;

    private final Deque<StoryEvent> recent;
    private final Map<String, Integer> tally;
    private long sequence;

    public Chronicle() {
        this.recent = new ArrayDeque<>();
        this.tally = new LinkedHashMap<>();
        this.sequence = 0;
    }

    @JsonCreator
    public Chronicle(
            @JsonProperty("recentEvents") List<StoryEvent> recentEvents,
            @JsonProperty("tally") Map<String, Integer> tally,
            @JsonProperty("sequence") long sequence) {
        this.recent = new ArrayDeque<>(recentEvents != null ? recentEvents : List.of());
        this.tally = new LinkedHashMap<>(tally != null ? tally : Map.of());
        this.sequence = Math.max(0, sequence);
    }

    /** Record a story event; oldest events compact into the tally. */
    public synchronized void record(String type, String subject, String detail) {
        if (type == null || type.isBlank()) return;
        recent.addLast(new StoryEvent(
                type.trim().toLowerCase(Locale.ROOT), ++sequence,
                subject != null ? subject : "", detail != null ? detail : ""));
        while (recent.size() > MAX_RECENT) {
            StoryEvent evicted = recent.removeFirst();
            tally.merge(evicted.getType(), 1, Integer::sum);
        }
    }

    /**
     * Deterministic, compact fact list for the narrator: an optional roll-up
     * line for compacted history, then the most recent events (newest last).
     * Bounded by {@code min(maxFacts, MAX_FACTS)} entries and
     * {@link #MAX_FACT_LENGTH} chars each, so prompt growth is capped no
     * matter how long the session runs.
     */
    public synchronized List<String> renderFacts(int maxFacts) {
        int cap = Math.max(1, Math.min(maxFacts, MAX_FACTS));
        List<String> facts = new ArrayList<>();

        if (!tally.isEmpty()) {
            StringBuilder sb = new StringBuilder("Earlier in this tale: ");
            boolean first = true;
            for (Map.Entry<String, Integer> e : tally.entrySet()) {
                if (!first) sb.append(", ");
                sb.append(e.getValue()).append(' ').append(humanizeTallyType(e.getKey(), e.getValue()));
                first = false;
            }
            facts.add(clip(sb.toString()));
        }

        int room = cap - facts.size();
        List<StoryEvent> events = new ArrayList<>(recent);
        int from = Math.max(0, events.size() - room);
        for (int i = from; i < events.size(); i++) {
            facts.add(clip(events.get(i).render()));
        }
        return facts;
    }

    @JsonIgnore
    public synchronized boolean isEmpty() {
        return recent.isEmpty() && tally.isEmpty();
    }

    public synchronized List<StoryEvent> getRecentEvents() {
        return List.copyOf(recent);
    }

    public synchronized Map<String, Integer> getTally() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(tally));
    }

    public synchronized long getSequence() {
        return sequence;
    }

    private static String clip(String s) {
        if (s.length() <= MAX_FACT_LENGTH) return s;
        return s.substring(0, MAX_FACT_LENGTH - 1) + "…";
    }

    private static String humanizeTallyType(String type, int count) {
        boolean plural = count != 1;
        return switch (type) {
            case "enemy_slain" -> plural ? "foes slain" : "foe slain";
            case "boss_slain" -> plural ? "bosses slain" : "boss slain";
            case "combat_won" -> plural ? "battles won" : "battle won";
            case "quest_completed" -> plural ? "quests completed" : "quest completed";
            case "quest_failed" -> plural ? "quests failed" : "quest failed";
            case "quest_started" -> plural ? "quests begun" : "quest begun";
            case "party_fallen" -> plural ? "total defeats" : "total defeat";
            default -> type.replace('_', ' ') + (plural ? " events" : " event");
        };
    }

    /** One typed story milestone. */
    public static final class StoryEvent {
        private final String type;
        private final long seq;
        private final String subject;
        private final String detail;

        @JsonCreator
        public StoryEvent(
                @JsonProperty("type") String type,
                @JsonProperty("seq") long seq,
                @JsonProperty("subject") String subject,
                @JsonProperty("detail") String detail) {
            this.type = type != null ? type : "";
            this.seq = seq;
            this.subject = subject != null ? subject : "";
            this.detail = detail != null ? detail : "";
        }

        public String getType() { return type; }
        public long getSeq() { return seq; }
        public String getSubject() { return subject; }
        public String getDetail() { return detail; }

        /** Deterministic one-line rendering for prompt context. */
        public String render() {
            String base = switch (type) {
                case "quest_started" -> "Quest begun: " + subject;
                case "quest_completed" -> "Quest completed: " + subject;
                case "quest_failed" -> "Quest failed: " + subject;
                case "boss_slain" -> "Boss slain: " + subject;
                case "enemy_slain" -> "Slain: " + subject;
                case "combat_won" -> "Battle won" + (subject.isEmpty() ? "" : " at " + subject);
                case "party_fallen" -> "The party fell";
                case "campaign_complete" -> "Campaign completed: " + subject;
                default -> type.replace('_', ' ') + (subject.isEmpty() ? "" : ": " + subject);
            };
            return detail.isEmpty() ? base : base + " (" + detail + ")";
        }
    }
}
