package com.xai.dungeonmaster;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Goal G2 — "Memory you can feel."
 *
 * Derives player-facing identity (epithets, scars) and a short session recap
 * from {@link Chronicle} + {@link WorldState}. Pure functions over already
 * persisted state — no extra save fields required.
 */
public final class StoryMemory {

    private StoryMemory() {}

    /**
     * Up to {@code maxLines} short recap sentences for load screens / SPA chrome.
     * Deterministic; works offline with local-stub.
     */
    public static List<String> recap(Chronicle chronicle, int maxLines) {
        if (chronicle == null || chronicle.isEmpty()) {
            return List.of("A new tale has not yet begun.");
        }
        int cap = Math.max(1, Math.min(maxLines, 5));
        List<String> lines = new ArrayList<>();

        // Prefer newest meaningful milestones, oldest-first for reading order.
        List<Chronicle.StoryEvent> events = chronicle.getRecentEvents();
        List<Chronicle.StoryEvent> picked = new ArrayList<>();
        for (int i = events.size() - 1; i >= 0 && picked.size() < cap; i--) {
            Chronicle.StoryEvent e = events.get(i);
            if (isRecapWorthy(e.getType())) {
                picked.add(0, e);
            }
        }
        // If nothing "worthy", take last events.
        if (picked.isEmpty()) {
            int from = Math.max(0, events.size() - cap);
            picked.addAll(events.subList(from, events.size()));
        }
        for (Chronicle.StoryEvent e : picked) {
            lines.add(toRecapSentence(e));
        }
        if (lines.isEmpty() && !chronicle.getTally().isEmpty()) {
            lines.add(String.join(" ", chronicle.renderFacts(1)));
        }
        if (lines.isEmpty()) {
            lines.add("The chronicle remembers only dust so far.");
        }
        return List.copyOf(lines);
    }

    /** Epithets earned from world flags + chronicle milestones (stable order). */
    public static List<String> epithets(WorldState world, Chronicle chronicle, List<Adventurer> party) {
        Set<String> out = new LinkedHashSet<>();
        if (world != null) {
            int letter = world.getFlag("letter_fate");
            if (letter == 1) out.add("Letter-Bearer");
            if (letter == 2) out.add("Ash-Handed");
            if (letter == 3) out.add("Pin-Witness");
            if (world.getFlag("veyra_pact") > 0 || "veyra_pact".equals(ending(world))) {
                out.add("Veyra's Hand");
            }
            if (world.getFlag("baker_ally") > 0) out.add("Friend of the Oven");
            if (world.getFlag("parish_hope") > 0) out.add("Warden of the Procession");
            if (world.getFlag("tree_fate") == 1) out.add("Tree-Saver");
            if (world.getFlag("tree_fate") == 2) out.add("Tree-Burner");
        }
        if (chronicle != null) {
            for (Chronicle.StoryEvent e : chronicle.getRecentEvents()) {
                if ("boss_slain".equals(e.getType()) && !e.getSubject().isBlank()) {
                    out.add("Slayer of " + e.getSubject());
                }
                if ("campaign_complete".equals(e.getType()) && !e.getSubject().isBlank()) {
                    out.add("Finisher of " + e.getSubject());
                }
            }
            Integer bosses = chronicle.getTally().get("boss_slain");
            if (bosses != null && bosses > 0 && out.stream().noneMatch(s -> s.startsWith("Slayer"))) {
                out.add(bosses == 1 ? "Boss-Slayer" : "Bane of Bosses");
            }
        }
        // Cap for UI
        List<String> list = new ArrayList<>(out);
        if (list.size() > 6) {
            return List.copyOf(list.subList(0, 6));
        }
        return List.copyOf(list);
    }

    /** Scars / lasting consequences — also from flags + chronicle. */
    public static List<String> scars(WorldState world, Chronicle chronicle) {
        Set<String> out = new LinkedHashSet<>();
        if (world != null) {
            if (world.getFlag("city_heat") >= 2) out.add("Known to the city watch");
            if (world.getFlag("city_heat") == 1) out.add("A name whispered in alleys");
            if (world.getFlag("letter_ash") > 0) out.add("Sleeves stained with letter-ash");
            if (world.getFlag("safehouse_compromised") > 0) out.add("Safehouse burned in memory");
            if (world.getFlag("soaked") > 0) out.add("River-cold in the bones");
            if (world.getFlag("dock_debt") > 0) out.add("Owes a dock clerk");
            if (world.getFlag("ending") > 0) {
                String end = endingKey(world);
                if (end != null) out.add("Marked by ending: " + end.replace('_', ' '));
            }
        }
        if (chronicle != null) {
            Integer fallen = chronicle.getTally().get("party_fallen");
            if (fallen != null && fallen > 0) {
                out.add(fallen == 1 ? "Once the party fell" : "The party has fallen " + fallen + " times");
            }
            for (Chronicle.StoryEvent e : chronicle.getRecentEvents()) {
                if ("party_fallen".equals(e.getType())) {
                    out.add("Once the party fell");
                }
                if ("quest_failed".equals(e.getType()) && !e.getSubject().isBlank()) {
                    out.add("Failed: " + e.getSubject());
                }
            }
        }
        List<String> list = new ArrayList<>(out);
        if (list.size() > 6) {
            return List.copyOf(list.subList(0, 6));
        }
        return List.copyOf(list);
    }

    /**
     * Display title for the lead adventurer, e.g. {@code "Ryn the Ash-Handed"}.
     */
    public static String partyTitle(List<Adventurer> party, List<String> epithets) {
        String name = "Adventurer";
        if (party != null && !party.isEmpty() && party.get(0) != null && party.get(0).getName() != null) {
            name = party.get(0).getName().trim();
            if (name.isEmpty()) name = "Adventurer";
        }
        if (epithets == null || epithets.isEmpty()) {
            return name;
        }
        String first = epithets.get(0);
        // "Slayer of X" stays as suffix with "the" only for short epithets
        if (first.toLowerCase(Locale.ROOT).startsWith("slayer of")
                || first.toLowerCase(Locale.ROOT).startsWith("finisher of")
                || first.toLowerCase(Locale.ROOT).startsWith("friend of")
                || first.toLowerCase(Locale.ROOT).startsWith("warden of")
                || first.toLowerCase(Locale.ROOT).startsWith("bane of")) {
            return name + ", " + first;
        }
        return name + " the " + first;
    }

    private static boolean isRecapWorthy(String type) {
        if (type == null) return false;
        return switch (type) {
            case "quest_started", "quest_completed", "quest_failed",
                    "boss_slain", "combat_won", "party_fallen",
                    "campaign_complete", "npc_met", "oath", "scar" -> true;
            default -> false;
        };
    }

    private static String toRecapSentence(Chronicle.StoryEvent e) {
        String s = e.render();
        if (s.isEmpty()) return "Something happened, then the rain washed the details away.";
        // Ensure sentence end
        char last = s.charAt(s.length() - 1);
        if (last != '.' && last != '!' && last != '?') {
            s = s + ".";
        }
        return s;
    }

    private static String ending(WorldState world) {
        // ending is stored as flag name with value via SET_FLAG ending=... but
        // WorldState may only store integer flags — check common int encodings.
        return endingKey(world);
    }

    private static String endingKey(WorldState world) {
        if (world == null) return null;
        // first-light uses SET_FLAG ending=open_square which may not parse as int —
        // WorldState setFlag from ChoiceEffect parses int. Check letter/dawn paths.
        if (world.getFlag("dawn_path") == 1) return "open_square";
        if (world.getFlag("dawn_path") == 2) return "ash_silence";
        if (world.getFlag("dawn_path") == 3) return "veyra_pact";
        if (world.getFlag("dawn_path") == 4) return "exile_road";
        return null;
    }
}
