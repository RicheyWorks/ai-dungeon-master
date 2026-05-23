package com.xai.dungeonmaster;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.xai.dungeonmaster.util.ResourceLoader;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Core game engine for the Multiversal Dungeon Master.
 *
 * Spring Boot changes vs the original:
 *  - Raw ServerSocket / Socket broadcaster REMOVED.
 *    Real-time messages now flow via the SimpMessagingTemplate wired into
 *    uiUpdater inside GameConfig.  Every engine.broadcast() → uiUpdater →
 *    SimpMessagingTemplate.convertAndSend("/topic/narrative", text).
 *
 *  - Constructor no longer takes (isServer, port).  Those parameters are gone.
 *    Party names/roles and difficulty/chaos come from application.properties
 *    via GameConfig, which creates this bean.
 *
 *  - Everything else (combat, loot, XP, quests, spells, persistence) is
 *    identical to the original.
 *
 * This class is NOT annotated with @Service because it needs constructor
 * arguments injected from application.properties.  GameConfig creates it as
 * a @Bean and wires the messaging template.
 */
public class DungeonMasterEngine {

    private final List<Adventurer> party = Collections.synchronizedList(new ArrayList<>());
    private final List<String> turnHistoryLog = Collections.synchronizedList(new ArrayList<>());

    private final CombatState combatState;
    private final DungeonGenerator dungeonGenerator;

    /**
     * Volatile so reads in handleChoice / save / load see the latest write.
     * For compound read-modify-write sequences, snapshot to a local first.
     */
    private volatile Quest currentQuest;

    private final ObjectMapper mapper;
    private final Random random = new Random();

    private int difficulty;
    private int chaosLevel;
    private int actionCounter = 0;

    /*
     * Multi-listener broadcast system.
     * CopyOnWriteArrayList keeps iteration safe when subscribers are added from
     * different threads at startup.  Two listeners are registered in practice:
     *   1. GameConfig  -> SimpMessagingTemplate (WebSocket /topic/narrative push)
     *   2. GuiLauncher -> DungeonAdventureGui   (Swing panel update on EDT)
     */
    private final java.util.concurrent.CopyOnWriteArrayList<Consumer<String>> listeners =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    // ──────────────────────────────────────────────────────────────────────────
    // Constructor  (isServer / port removed — networking is handled by Spring)
    // ──────────────────────────────────────────────────────────────────────────

    public DungeonMasterEngine(int difficulty, int chaos, String[] names, String[] roles) {

        this.mapper = ResourceLoader.getMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);

        this.difficulty = difficulty;
        this.chaosLevel = chaos;

        this.combatState = new CombatState();
        this.dungeonGenerator = new DungeonGenerator(random, difficulty, chaos);

        // Build party
        for (int i = 0; i < names.length; i++) {
            String role = (i < roles.length) ? roles[i] : "Wanderer";
            Adventurer adv = new Adventurer(names[i], 12, 12, 12, 12, 12, 12, 100, role);
            party.add(adv);
        }

        // Role-based starter spells
        for (Adventurer adv : party) {
            switch (adv.getRole().toUpperCase(Locale.ROOT)) {
                case "MAGE" -> {
                    adv.learnSpell(new Spell("Void Bolt",     8, "VOID_BOLT",    18));
                    adv.learnSpell(new Spell("Temporal Heal", 6, "TEMPORAL_HEAL", 15));
                }
                case "CLERIC" -> {
                    adv.learnSpell(new Spell("Temporal Heal", 5, "TEMPORAL_HEAL", 20));
                    adv.learnSpell(new Spell("Rift Shield",   7, "RIFT_SHIELD",   12));
                }
                case "SUMMONER" -> {
                    adv.learnSpell(new Spell("Chrono Stun", 9, "CHRONO_STUN", 8));
                }
            }
        }

        this.currentQuest = dungeonGenerator.generateCustomRift("Genesis Rift", 4, difficulty);
        log("Multiversal Engine Online. Chaos Level " + chaosLevel);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Action router
    // ──────────────────────────────────────────────────────────────────────────

    public void handleChoice(Choice choice) {
        if (choice == null) return;

        String result;

        if (combatState.isActive()) {
            result = processCombatAction(choice.getLabel());
        } else {
            result = processExplorationAction(choice);
        }

        broadcast(result);
        postActionCleanup();
    }

    private String processCombatAction(String actionLabel) {

        Entity actor = combatState.getCurrentEntity();
        if (actor == null) return "The timeline is stagnant.";

        if (!actor.isAlive()) {
            combatState.nextTurn();
            return actor.getName() + " is unable to act.";
        }

        String action = actionLabel.toUpperCase(Locale.ROOT);

        switch (action) {

            case "ATTACK": {
                Entity target = combatState.getRandomEnemy();
                if (target == null) return "No enemies remain.";

                int baseDmg = 5 + random.nextInt(10);
                boolean crit = random.nextInt(100) < 15;
                if (crit) baseDmg *= 2;

                boolean dodged = random.nextInt(100) < 10;
                if (dodged) return target.getName() + " evades the attack!";

                if (chaosLevel > 5 && random.nextInt(100) < chaosLevel * 3) {
                    baseDmg += random.nextInt(10);
                }

                target.takeDamage(baseDmg);
                String result = actor.getName() + " strikes " + target.getName() + " for " + baseDmg + " damage.";
                if (crit) result += " CRITICAL HIT!";

                if (target.isAlive() && random.nextInt(100) < 30) {
                    result += "\n" + target.getName() + " snarls: 'You will regret that...'";
                }
                if (!target.isAlive()) result += handleEnemyDefeated(actor, target);
                return result;
            }

            case "SPELL": {
                if (!(actor instanceof Adventurer adv)) return actor.getName() + " has no arcane talent.";

                List<Spell> spells = adv.getKnownSpells();
                if (spells.isEmpty()) return adv.getName() + " knows no spells yet.";

                Spell spell = spells.get(random.nextInt(spells.size()));
                Entity target = combatState.getRandomEnemy();
                String result = spell.cast(this, adv, target);

                if (chaosLevel > 6 && random.nextInt(100) < 25) {
                    result += "\n🌌 CHAOS AMPLIFIES THE SPELL!";
                    if (target != null) target.takeDamage(5);
                }
                if (target != null && !target.isAlive()) result += handleEnemyDefeated(actor, target);
                return result;
            }

            case "ITEM": {
                if (!(actor instanceof Adventurer adv)) return actor.getName() + " has no usable inventory.";

                List<Item> items = adv.getInventory();
                if (items.isEmpty()) return adv.getName() + " reaches into their pack... but finds nothing.";

                Item item = items.stream().filter(Item::isConsumable).findFirst().orElse(null);
                if (item == null) return adv.getName() + " has no consumable items ready.";

                String result = adv.getName() + " uses " + item.getName() + ".\n";
                result += item.use(this, adv);
                adv.removeItem(item);
                return result;
            }

            case "FLEE":
                combatState.setInactive();
                return actor.getName() + " tears open reality and escapes the encounter.";

            default:
                return actor.getName() + " steadies themselves for what comes next.";
        }
    }

    private String handleEnemyDefeated(Entity actor, Entity target) {
        StringBuilder result = new StringBuilder("\nDEFEATED: ").append(target.getName()).append(" has been slain.");

        int xpGain = 20 + random.nextInt(30);
        if (target.getName().toLowerCase(Locale.ROOT).contains("boss")) xpGain *= 3;

        if (actor instanceof Adventurer adv) {
            String xpText = adv.gainXp(xpGain);
            if (!xpText.isBlank()) result.append("\n").append(xpText);
        }

        Item loot = dungeonGenerator.generateLoot();
        if (loot != null && actor instanceof Adventurer adv) {
            adv.addItem(loot);
            result.append("\nLOOT FOUND: ").append(loot);
        }

        target.onDeath(this);
        return result.toString();
    }

    private String processExplorationAction(Choice choice) {
        // Snapshot the quest reference once so a concurrent loadGame() can't
        // swap it out from under us mid-method.
        Quest quest = currentQuest;
        String outcome = choice.execute(this, party.get(0));
        if (quest != null) quest.advance(this);

        if (random.nextInt(100) < chaosLevel * 5) {
            triggerCombatEncounter();
        }
        return outcome;
    }

    private void triggerCombatEncounter() {
        List<Enemy> foes = new ArrayList<>();
        boolean isBoss = random.nextInt(100) < 15;

        if (isBoss) {
            Enemy boss = dungeonGenerator.generateEnemy(true);
            boss.heal(50);
            boss.addEffect(new StatusEffect("Boss Aura", StatusEffect.EffectType.STAT_BUFF, 3, Integer.MAX_VALUE));
            foes.add(boss);
            combatState.initialize(party, foes);
            broadcast("⚠ BOSS ENCOUNTER: " + boss.getName() + " emerges from the rift!");
            return;
        }

        int count = 1 + random.nextInt(3);
        for (int i = 0; i < count; i++) foes.add(dungeonGenerator.generateEnemy(false));
        combatState.initialize(party, foes);
        broadcast(">> ALERT: Hostile entities detected.");
    }

    private void handleCombatVictory() {
        combatState.setInactive();
        broadcast("VICTORY: The hostile rift collapses.");

        int partyXp = 25 + random.nextInt(40);
        synchronized (party) {
            for (Adventurer adv : party) {
                if (adv.isAlive()) {
                    String xpText = adv.gainXp(partyXp);
                    if (!xpText.isBlank()) broadcast(xpText);

                    Item bonusLoot = dungeonGenerator.generateLoot();
                    if (bonusLoot != null) {
                        adv.addItem(bonusLoot);
                        broadcast(adv.getName() + " receives victory loot: " + bonusLoot);
                    }
                }
            }
        }
    }

    private void maybeAddNarrativeFlavor() {
        actionCounter++;
        if (actionCounter % 3 != 0) return;

        String[] flavor = {
                "The dungeon walls pulse like something alive.",
                "A cold wind moves through the corridor, though no door is open.",
                "Somewhere in the dark, stone claws scrape against stone.",
                "The rift hum deepens, bending the air around the party.",
                "For a moment, every shadow points toward the party."
        };
        broadcast(flavor[random.nextInt(flavor.length)]);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Persistence
    // ──────────────────────────────────────────────────────────────────────────

    public synchronized void saveGame(String path) {
        try {
            GameStateData data = new GameStateData(party, currentQuest, chaosLevel, difficulty);
            mapper.writeValue(new File(path), data);
            log("Timeline persisted.");
        } catch (IOException e) {
            log("Persistence failure: " + e.getMessage());
        }
    }

    public synchronized void loadGame(String path) {
        try {
            GameStateData data = mapper.readValue(new File(path), GameStateData.class);
            party.clear();
            party.addAll(data.party);
            currentQuest = data.currentQuest;
            chaosLevel = data.chaosLevel;
            difficulty = data.difficulty;
            log("Timeline restored.");
        } catch (IOException e) {
            log("Load failure: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Turn management
    // ──────────────────────────────────────────────────────────────────────────

    private void postActionCleanup() {
        if (combatState.isVictory()) {
            handleCombatVictory();
            maybeAddNarrativeFlavor();
            return;
        }

        if (combatState.isDefeat()) {
            combatState.setInactive();
            broadcast("FATAL: All adventurers are dead.");
            return;
        }

        if (combatState.isActive()) {
            Entity next = combatState.nextTurn();
            if (next != null) next.onTurnStart(this);

            if (combatState.isVictory()) { handleCombatVictory(); return; }
            if (combatState.isDefeat())  { combatState.setInactive(); broadcast("FATAL: All adventurers are dead."); return; }
        }

        maybeAddNarrativeFlavor();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // UI / API interface
    // ──────────────────────────────────────────────────────────────────────────

    public List<Choice> getCurrentAvailableChoices() {
        if (combatState.isActive()) {
            return List.of(
                    new Choice("Attack", "Strike enemy"),
                    new Choice("Spell",  "Cast magic"),
                    new Choice("Item",   "Use first consumable"),
                    new Choice("Flee",   "Retreat")
            );
        }
        // Snapshot to avoid NPE if a concurrent loadGame() swaps the quest mid-read.
        Quest quest = currentQuest;
        if (quest == null) return Collections.emptyList();
        Scene scene = quest.getCurrentScene();
        return (scene != null) ? scene.getChoices() : Collections.emptyList();
    }

    /**
     * Adds the message to the turn-history log and pushes it to uiUpdater.
     * In the Spring Boot context, uiUpdater is wired to SimpMessagingTemplate
     * in GameConfig, so every log call → /topic/narrative WebSocket push.
     */
    public void log(String msg) {
        if (msg == null) return;
        turnHistoryLog.add(msg);
        for (Consumer<String> l : listeners) {
            try { l.accept(msg); } catch (Exception ignored) {}
        }
    }

    /**
     * Alias for log().  In the original engine this also broadcasted over raw
     * sockets to connected clients.  Spring WebSocket handles that now.
     */
    public void broadcast(String message) {
        log(message);
    }

    public String startQuest() {
        Quest quest = currentQuest;
        return (quest != null) ? "Entering: " + quest.getTitle() : "No active quest.";
    }

    public String getPartySummary() {
        return party.stream()
                .map(a -> a.getName() + " Lvl:" + a.getLevel()
                        + " XP:" + a.getXp() + "/" + a.getXpToNext()
                        + " HP:" + a.getHp() + "/" + a.getMaxHp()
                        + " MP:" + a.getMana() + "/" + a.getMaxMana())
                .collect(Collectors.joining(" | "));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Accessors
    // ──────────────────────────────────────────────────────────────────────────

    public int getChaosLevel()               { return chaosLevel; }
    public List<String> getTurnHistory()     { return turnHistoryLog; }
    public CombatState getCombatState()      { return combatState; }
    /**
     * @deprecated Destructive: clears every existing listener before installing
     *             the new one. Callers that want to ADD a listener without
     *             evicting the WebSocket/Swing bridges should use
     *             {@link #addUiListener(Consumer)} instead. This method is kept
     *             only for old call-sites that genuinely want a single-listener
     *             reset (e.g., test harnesses); production wiring should not
     *             call it.
     */
    @Deprecated
    public void setUiUpdater(Consumer<String> updater) {
        listeners.clear();
        if (updater != null) listeners.add(updater);
    }

    /**
     * Adds an additional listener WITHOUT clearing existing ones.
     * GuiLauncher calls this AFTER GameConfig has already registered the WS listener,
     * so both the WebSocket push and the Swing repaint fire for every broadcast.
     */
    public void addUiListener(Consumer<String> listener) {
        if (listener != null) listeners.add(listener);
    }

    /**
     * Kept for DungeonAdventureGui backward-compat.  In Spring Boot mode
     * this always returns true (the server IS the process).
     */
    public boolean isServer()                { return true; }
}

// ─────────────────────────────────────────────────────────────────────────────
// GameStateData — moved to its own file ideally, but kept here for brevity
// ─────────────────────────────────────────────────────────────────────────────

class GameStateData {
    public List<Adventurer> party;
    public Quest currentQuest;
    public int chaosLevel;
    public int difficulty;

    public GameStateData() {}
    public GameStateData(List<Adventurer> p, Quest q, int c, int d) {
        this.party = p;
        this.currentQuest = q;
        this.chaosLevel = c;
        this.difficulty = d;
    }
}
