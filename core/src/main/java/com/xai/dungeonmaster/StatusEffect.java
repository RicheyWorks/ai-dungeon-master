package com.xai.dungeonmaster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Enterprise-grade Status Effect system.
 * Uses a Type-Magnitude pattern for full JSON serialization support.
 */
public class StatusEffect {

    public enum EffectType {
        DAMAGE_OVER_TIME,
        HEAL_OVER_TIME,
        STAT_BUFF,
        STAT_DEBUFF,
        STUN
    }

    private final String name;
    private final EffectType type;
    private final int magnitude;
    private int duration;

    @JsonCreator
    public StatusEffect(
            @JsonProperty("name") String name,
            @JsonProperty("type") EffectType type,
            @JsonProperty("magnitude") int magnitude,
            @JsonProperty("duration") int duration) {

        this.name = normalize(name, "Unnamed Effect");
        this.type = (type != null) ? type : EffectType.STAT_DEBUFF;
        this.magnitude = Math.max(0, magnitude);
        this.duration = Math.max(0, duration);
    }

    /**
     * Executes the effect logic based on its type.
     */
    public void applyTick(DungeonMasterEngine engine, Entity target) {
        if (isExpired() || target == null) {
            return;
        }

        switch (type) {
            case DAMAGE_OVER_TIME -> {
                target.takeDamage(magnitude);
                log(engine, target.getName() + " suffers " + magnitude + " damage from [" + name + "].");
            }

            case HEAL_OVER_TIME -> {
                target.heal(magnitude);
                log(engine, target.getName() + " restores " + magnitude + " HP via [" + name + "].");
            }

            case STUN -> {
                log(engine, target.getName() + " is paralyzed by [" + name + "]!");
            }

            case STAT_BUFF, STAT_DEBUFF -> {
                // Passive modifiers; consumed by other systems like AC, attack, movement, etc.
                log(engine, target.getName() + " is affected by [" + name + "].");
            }
        }

        decrementDuration();
    }

    public void decrementDuration() {
        if (duration > 0) {
            duration--;
        }
    }

    public String getName() {
        return name;
    }

    public int getDuration() {
        return duration;
    }

    public EffectType getType() {
        return type;
    }

    public int getMagnitude() {
        return magnitude;
    }

    @JsonProperty("isExpired")
    public boolean isExpired() {
        return duration <= 0;
    }

    @JsonIgnore
    public boolean isBuff() {
        return type == EffectType.STAT_BUFF;
    }

    @JsonIgnore
    public boolean isDebuff() {
        return type == EffectType.STAT_DEBUFF || type == EffectType.DAMAGE_OVER_TIME || type == EffectType.STUN;
    }

    @JsonIgnore
    public StatusEffect copy() {
        return new StatusEffect(name, type, magnitude, duration);
    }

    private static void log(DungeonMasterEngine engine, String message) {
        if (engine != null && message != null && !message.isBlank()) {
            engine.log(message);
        }
    }

    private static String normalize(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    @Override
    public String toString() {
        return "StatusEffect{" +
                "name='" + name + '\'' +
                ", type=" + type +
                ", magnitude=" + magnitude +
                ", duration=" + duration +
                '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type, magnitude, duration);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof StatusEffect other)) return false;
        return magnitude == other.magnitude
                && duration == other.duration
                && Objects.equals(name, other.name)
                && type == other.type;
    }
}
