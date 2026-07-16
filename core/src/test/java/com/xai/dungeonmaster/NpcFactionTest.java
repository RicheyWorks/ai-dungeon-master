package com.xai.dungeonmaster;

import com.xai.dungeonmaster.plugin.ContentPack;
import com.xai.dungeonmaster.plugin.ContentRegistry;
import com.xai.dungeonmaster.plugin.LLMProvider;
import com.xai.dungeonmaster.plugin.QuestScript;
import com.xai.dungeonmaster.plugin.QuestScriptRegistry;
import com.xai.dungeonmaster.plugin.builtin.LocalStubProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ADR-001 Phase 4: NPCs and factions — pack-merged registries, disposition
 * and reputation via the effect/condition machinery, first-meeting chronicle
 * events, and persona facts in narration.
 */
class NpcFactionTest {

    private static final Npc BRINE = new Npc("mother-brine", "Mother Brine",
            "Salt-Keeper", "parish-survivors",
            "an old woman crusted in grave-salt", 0);

    /** Minimal in-memory pack carrying one NPC and one faction. */
    private static ContentPack npcPack() {
        return new ContentPack() {
            @Override public String id() { return "npc-pack"; }
            @Override public String displayName() { return "NPC Pack"; }
            @Override public String version() { return "1.0.0"; }
            @Override public Map<String, Npc> npcs() { return Map.of(BRINE.getId(), BRINE); }
            @Override public Map<String, Faction> factions() {
                return Map.of("parish-survivors",
                        new Faction("parish-survivors", "Parish Survivors", "The living remnant.", 0));
            }
        };
    }

    @BeforeEach
    @AfterEach
    void reset() {
        ContentRegistry.clearForTests();
        QuestScriptRegistry.clearForTests();
        CampaignRegistry.clearForTests();
    }

    private static DungeonMasterEngine engine() {
        return new DungeonMasterEngine(1, 0, new String[]{"Kael"}, new String[]{"Warrior"});
    }

    /** Register a one-scene dialogue quest featuring Mother Brine. */
    private static void registerDialogueQuest() {
        QuestScriptRegistry.register(new QuestScript() {
            @Override public String id() { return "dialogue"; }
            @Override public String displayName() { return "dialogue"; }
            @Override public Quest build(DungeonMasterEngine e, int d, int c) {
                Scene chapel = new Scene("chapel", "The chapel.", List.of(
                        new Choice("Pray", null, "You pray.", null,
                                List.of(new ChoiceEffect("MODIFY_DISPOSITION", "mother-brine=2")), null),
                        new Choice("Blessing", null, "She blesses you.", null, null,
                                new ChoiceCondition("DISPOSITION", "mother-brine", "GTE", 2))),
                        false, "mother-brine");
                Scene end = new Scene("end", "Done.", List.of(), true, null);
                return new Quest("Dialogue Quest", "Talk.", List.of(chapel, end));
            }
        });
    }

    // ─── Registry merge ─────────────────────────────────────────────────────

    @Test
    void npcsAndFactionsMergeIntoContentRegistry() {
        ContentRegistry.register(npcPack());

        assertEquals("Mother Brine", ContentRegistry.npcs().get("mother-brine").getDisplayName());
        assertEquals("Parish Survivors",
                ContentRegistry.factions().get("parish-survivors").getDisplayName());

        ContentRegistry.setEnabled("npc-pack", false);
        assertTrue(ContentRegistry.npcs().isEmpty(), "disabled pack's NPCs must disappear");
    }

    @Test
    void packsWithoutNpcSupportReturnEmptyDefaults() {
        ContentPack legacy = new ContentPack() {
            @Override public String id() { return "legacy"; }
            @Override public String displayName() { return "Legacy"; }
            @Override public String version() { return "1.0.0"; }
        };
        assertTrue(legacy.npcs().isEmpty());
        assertTrue(legacy.factions().isEmpty());
    }

    // ─── Disposition / reputation machinery ─────────────────────────────────

    @Test
    void dispositionAndReputationEffectsUseNamespacedFlags() {
        DungeonMasterEngine eng = engine();
        new ChoiceEffect("MODIFY_DISPOSITION", "mother-brine=2").apply(eng, null);
        new ChoiceEffect("MODIFY_DISPOSITION", "mother-brine=-1").apply(eng, null);
        new ChoiceEffect("MODIFY_REPUTATION", "parish-survivors").apply(eng, null);

        assertEquals(1, eng.getWorldState().getFlag(Npc.dispositionFlag("mother-brine")));
        assertEquals(1, eng.getWorldState().getFlag(Faction.reputationFlag("parish-survivors")));
    }

    @Test
    void dialogueChoicesGateOnDisposition() {
        registerDialogueQuest();
        DungeonMasterEngine eng = engine();
        eng.startQuestById("dialogue");

        // "Blessing" needs disposition >= 2: hidden at first.
        assertEquals(List.of("Pray"),
                eng.getCurrentAvailableChoices().stream().map(Choice::getLabel).toList());

        eng.getWorldState().addFlag(Npc.dispositionFlag("mother-brine"), 2);
        assertEquals(List.of("Pray", "Blessing"),
                eng.getCurrentAvailableChoices().stream().map(Choice::getLabel).toList());
    }

    // ─── Meetings + narration ───────────────────────────────────────────────

    @Test
    void firstMeetingIsChronicledOnceAndPersonaFeedsNarration() {
        ContentRegistry.register(npcPack());
        registerDialogueQuest();
        DungeonMasterEngine eng = engine();
        eng.startQuestById("dialogue");

        // Meeting recorded once, with display name from the registry.
        assertEquals(1, eng.getWorldState().getFlag(Npc.metFlag("mother-brine")));
        long meetings = eng.getChronicle().getRecentEvents().stream()
                .filter(ev -> ev.getType().equals("npc_met")).count();
        assertEquals(1, meetings);
        assertTrue(eng.getChronicle().renderFacts(8).stream()
                .anyMatch(f -> f.contains("Mother Brine")));

        // Persona sheet reaches the narrator (stub recaps the newest fact,
        // which is the persona line appended after chronicle facts).
        eng.setNarrator(new LocalStubProvider());
        LLMProvider.NarrativeResponse resp = eng.narrate("greet the salt-keeper");
        assertTrue(resp.text.contains("Present: Mother Brine, Salt-Keeper of parish-survivors"),
                "persona fact should reach narration, got: " + resp.text);
    }

    @Test
    void meetingWithoutRegistryEntryFallsBackToId() {
        registerDialogueQuest(); // npc not registered in ContentRegistry
        DungeonMasterEngine eng = engine();
        eng.startQuestById("dialogue");

        assertTrue(eng.getChronicle().renderFacts(8).stream()
                .anyMatch(f -> f.contains("mother-brine")));
    }
}
