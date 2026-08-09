package com.xai.dungeonmaster;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Goal G3 — cinematic checks produce CheckResult with stakes → roll → effect. */
class CinematicCheckTest {

    @Test
    void attackSetsLastCheckWithStakesAndRoll() {
        DungeonMasterEngine engine = new DungeonMasterEngine(
                3, 0, new String[] { "Ryn" }, new String[] { "Warrior" });
        // Force combat
        engine.triggerCombatEncounter();
        assertTrue(engine.getCombatState().isActive());

        Choice attack = engine.getCurrentAvailableChoices().stream()
                .filter(c -> "Attack".equalsIgnoreCase(c.getLabel()))
                .findFirst()
                .orElseThrow();
        engine.handleChoice(attack);

        CheckResult check = engine.getLastCheck();
        assertNotNull(check, "attack should publish a CheckResult");
        assertEquals("attack", check.getKind());
        assertFalse(check.getStakes().isBlank());
        assertTrue(check.getRoll() >= 1 && check.getRoll() <= 20, "d20 roll");
        assertTrue(check.getDifficulty() >= 8);
        assertFalse(check.getNarration().isBlank());
        assertFalse(check.getEffect().isBlank());
    }

    @Test
    void pushYourLuckAvailableOncePerScene() {
        DungeonMasterEngine engine = new DungeonMasterEngine(
                3, 0, new String[] { "Ryn" }, new String[] { "Warrior" });
        engine.triggerCombatEncounter();

        List<String> labels = engine.getCurrentAvailableChoices().stream()
                .map(Choice::getLabel).toList();
        assertTrue(labels.stream().anyMatch(l -> l.toLowerCase().contains("push")),
                "push your luck should be offered: " + labels);

        Choice push = engine.getCurrentAvailableChoices().stream()
                .filter(c -> c.getLabel().toLowerCase().contains("push"))
                .findFirst()
                .orElseThrow();
        engine.handleChoice(push);

        CheckResult check = engine.getLastCheck();
        assertNotNull(check);
        assertTrue(check.isPushedLuck() || "push".equals(check.getKind()));

        // Second offer should be gone until scene change
        List<String> after = engine.getCurrentAvailableChoices().stream()
                .map(Choice::getLabel).toList();
        // combat may have ended; if still active, push should be absent
        if (engine.getCombatState().isActive()) {
            assertTrue(after.stream().noneMatch(l -> l.toLowerCase().contains("push")),
                    "push once per scene: " + after);
        }
    }

    @Test
    void skillCheckEffectRecordsResult() {
        DungeonMasterEngine engine = new DungeonMasterEngine(
                3, 0, new String[] { "Ryn" }, new String[] { "Rogue" });
        Adventurer lead = engine.getParty().get(0);
        new ChoiceEffect("SKILL_CHECK", "dexterity=5").apply(engine, lead);
        CheckResult check = engine.getLastCheck();
        assertNotNull(check);
        assertEquals("skill", check.getKind());
        assertTrue(check.getStakes().toLowerCase().contains("dexterity")
                || check.getStakes().toLowerCase().contains("dc"));
        assertTrue(check.getRoll() >= 1 && check.getRoll() <= 20);
    }
}
