package com.xai.dungeonmaster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Goal G3 — cinematic check result: stakes → roll → outcome in one object.
 * Surfaced on status / STOMP so the SPA can render drama, not a spreadsheet line.
 */
public final class CheckResult {

    private final String kind;       // attack | skill | push | spell | flee
    private final String actor;
    private final String target;
    private final String stakes;     // what hangs in the balance
    private final int roll;          // d20 or percentile display
    private final int modifier;
    private final int total;
    private final int difficulty;    // AC / DC
    private final boolean success;
    private final boolean critical;  // nat 20 or crit flag
    private final boolean fumble;    // nat 1
    private final String effect;     // short mechanical summary
    private final String narration;  // full player-facing line
    private final long atEpochMs;
    private final boolean pushedLuck;

    @JsonCreator
    public CheckResult(
            @JsonProperty("kind") String kind,
            @JsonProperty("actor") String actor,
            @JsonProperty("target") String target,
            @JsonProperty("stakes") String stakes,
            @JsonProperty("roll") int roll,
            @JsonProperty("modifier") int modifier,
            @JsonProperty("total") int total,
            @JsonProperty("difficulty") int difficulty,
            @JsonProperty("success") boolean success,
            @JsonProperty("critical") boolean critical,
            @JsonProperty("fumble") boolean fumble,
            @JsonProperty("effect") String effect,
            @JsonProperty("narration") String narration,
            @JsonProperty("atEpochMs") long atEpochMs,
            @JsonProperty("pushedLuck") boolean pushedLuck) {
        this.kind = kind != null ? kind : "check";
        this.actor = actor != null ? actor : "";
        this.target = target != null ? target : "";
        this.stakes = stakes != null ? stakes : "";
        this.roll = roll;
        this.modifier = modifier;
        this.total = total;
        this.difficulty = difficulty;
        this.success = success;
        this.critical = critical;
        this.fumble = fumble;
        this.effect = effect != null ? effect : "";
        this.narration = narration != null ? narration : "";
        this.atEpochMs = atEpochMs > 0 ? atEpochMs : System.currentTimeMillis();
        this.pushedLuck = pushedLuck;
    }

    public static CheckResult of(
            String kind, String actor, String target, String stakes,
            int roll, int modifier, int difficulty,
            boolean success, boolean critical, boolean fumble,
            String effect, String narration, boolean pushedLuck) {
        return new CheckResult(
                kind, actor, target, stakes,
                roll, modifier, roll + modifier, difficulty,
                success, critical, fumble, effect, narration,
                System.currentTimeMillis(), pushedLuck);
    }

    public String getKind() { return kind; }
    public String getActor() { return actor; }
    public String getTarget() { return target; }
    public String getStakes() { return stakes; }
    public int getRoll() { return roll; }
    public int getModifier() { return modifier; }
    public int getTotal() { return total; }
    public int getDifficulty() { return difficulty; }
    public boolean isSuccess() { return success; }
    public boolean isCritical() { return critical; }
    public boolean isFumble() { return fumble; }
    public String getEffect() { return effect; }
    public String getNarration() { return narration; }
    public long getAtEpochMs() { return atEpochMs; }
    public boolean isPushedLuck() { return pushedLuck; }
}
