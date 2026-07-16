package com.xai.dungeonmaster;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.xai.dungeonmaster.util.ResourceLoader;
import com.xai.dungeonmaster.plugin.LLMProvider;
import com.xai.dungeonmaster.plugin.LLMProviderRegistry;
import com.xai.dungeonmaster.plugin.QuestScriptRegistry;

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

    /** Script id the current quest was built from (campaign bookkeeping). */
    private volatile String currentQuestScriptId = QuestScriptRegistry.DEFAULT_SCRIPT;

    /** Persistent narrative truth: flags + quest outcomes (ADR-001 Phase 2). */
    private volatile WorldState worldState = new WorldState();

    /** Active story arc, or null for the historical single-quest behavior. */
    private volatile Campaign campaign;

    /** Guards the one-time "campaign complete" broadcast. */
    private volatile boolean campaignExhaustedAnnounced = false;

    /** Structured narrative memory fed to the narrator (ADR-001 Phase 3). */
    private volatile Chronicle chronicle = new Chronicle();

    /** Facts handed to the narrator per request (bounded by Chronicle caps). */
    private static final int NARRATION_FACTS = 6;

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

    /**
     * Narration backend. Defaults to the registry's active provider (the
     * offline LocalStubProvider unless a keyed provider is registered); the
     * Spring layer can swap in a budgeted/moderated stack via setNarrator().
     */
    private volatile LLMProvider narrator = LLMProviderRegistry.getActive();

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

        // Opening quest is sourced from the QuestScript SPI (bundled
        // DefaultQuestScript, id "default"), so content packs can ship their own
        // openers. Fall back to the generator directly if the registry was cleared.
        Quest opening = QuestScriptRegistry.dispatch(
                QuestScriptRegistry.DEFAULT_SCRIPT, this, difficulty, chaosLevel);
        this.currentQuest = (opening != null)
                ? opening
                : dungeonGenerator.generateCustomRift("Genesis Rift", 4, difficulty);
        chronicle.record("quest_started", this.currentQuest.getTitle(), "");
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

        boolean isBoss = target.getName().toLowerCase(Locale.ROOT).contains("boss");
        chronicle.record(isBoss ? "boss_slain" : "enemy_slain", target.getName(),
                "by " + actor.getName());

        int xpGain = 20 + random.nextInt(30);
        if (isBoss) xpGain *= 3;

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
        boolean wasFinished = (quest != null) && quest.isFinished();
        String outcome = choice.execute(this, party.get(0));
        if (quest != null) {
            quest.advance(this, choice);
            if (!wasFinished && quest.isFinished() && quest == currentQuest) {
                onQuestFinished(quest);
            } else {
                noteNpcMeeting(quest);
            }
        }

        if (random.nextInt(100) < chaosLevel * 5) {
            triggerCombatEncounter();
        }
        return outcome;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Campaign progression (ADR-001 Phase 2)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Record the finished quest in the world state and, when a campaign is
     * active, apply the finished node's granted flags and start the next
     * eligible node. With no campaign (or an exhausted one) the engine keeps
     * its historical behavior: the finished quest simply stays current.
     */
    private void onQuestFinished(Quest quest) {
        String scriptId = currentQuestScriptId;
        worldState.recordQuestOutcome(scriptId, quest.isCompleted(), quest.getTitle());
        chronicle.record(quest.isCompleted() ? "quest_completed" : "quest_failed",
                quest.getTitle(), "");

        Campaign arc = campaign;
        if (arc == null) return;

        Campaign.QuestNode finished = arc.nodeFor(scriptId);
        if (finished != null && quest.isCompleted()) {
            finished.applyGrants(worldState);
        }

        Campaign.QuestNode next = arc.nextEligible(worldState);
        if (next != null) {
            startQuestById(next.getQuestId());
        } else if (!campaignExhaustedAnnounced) {
            campaignExhaustedAnnounced = true;
            chronicle.record("campaign_complete", arc.getTitle(), "");
            broadcast("CAMPAIGN COMPLETE: " + arc.getTitle() + " — this world's story is told.");
        }
    }

    /**
     * Replace the current quest with one built from the given script id (via
     * the QuestScript registry). No-op if the registry can't build it.
     */
    public void startQuestById(String scriptId) {
        if (!QuestScriptRegistry.isRegistered(scriptId)) {
            log("WARN: unknown quest script '" + scriptId + "' — quest unchanged.");
            return;
        }
        Quest next = QuestScriptRegistry.dispatch(scriptId, this, difficulty, chaosLevel);
        if (next == null) return;
        currentQuest = next;
        currentQuestScriptId = scriptId;
        chronicle.record("quest_started", next.getTitle(), "");
        broadcast("NEW QUEST: " + next.getTitle() + " — " + next.getDescription());
        Scene opening = next.getCurrentScene();
        if (opening != null) opening.onEnter(this);
        noteNpcMeeting(next);
    }

    /**
     * Activate a story arc. Starts the campaign's first eligible quest
     * immediately (replacing the opening quest), so the arc owns the session
     * from turn one. Pass null to detach.
     */
    public void setCampaign(Campaign campaign) {
        this.campaign = campaign;
        this.campaignExhaustedAnnounced = false;
        if (campaign == null) return;
        broadcast("CAMPAIGN: " + campaign.getTitle());
        Campaign.QuestNode first = campaign.nextEligible(worldState);
        if (first != null) startQuestById(first.getQuestId());
    }

    /** The active campaign, or null. */
    public Campaign getCampaign() {
        return campaign;
    }

    /** The current quest, or null. */
    public Quest getCurrentQuest() {
        return currentQuest;
    }

    /** Persistent world flags + quest outcomes. Never null. */
    public WorldState getWorldState() {
        return worldState;
    }

    /** Structured narrative memory. Never null. */
    public Chronicle getChronicle() {
        return chronicle;
    }

    /**
     * First time the party reaches a scene with an NPC, mark the meeting in
     * the world state and the Chronicle (ADR-001 Phase 4).
     */
    private void noteNpcMeeting(Quest quest) {
        if (quest == null || quest != currentQuest) return;
        Scene scene = quest.getCurrentScene();
        String npcId = (scene != null) ? scene.getNpcId() : null;
        if (npcId == null) return;
        if (worldState.getFlag(Npc.metFlag(npcId)) > 0) return;
        worldState.setFlag(Npc.metFlag(npcId), 1);
        Npc npc = com.xai.dungeonmaster.plugin.ContentRegistry.npcs().get(npcId);
        chronicle.record("npc_met", (npc != null) ? npc.getDisplayName() : npcId,
                (npc != null && !npc.getRole().isEmpty()) ? npc.getRole() : "");
    }

    /**
     * Facts for the narrator: chronicle memory plus, when the current scene
     * has an NPC, that NPC's persona sheet so dialogue stays in character.
     */
    private List<String> narrationFacts() {
        List<String> facts = new ArrayList<>(chronicle.renderFacts(NARRATION_FACTS));
        Quest quest = currentQuest;
        Scene scene = (quest != null) ? quest.getCurrentScene() : null;
        String npcId = (scene != null) ? scene.getNpcId() : null;
        if (npcId != null) {
            Npc npc = com.xai.dungeonmaster.plugin.ContentRegistry.npcs().get(npcId);
            if (npc != null) facts.add(npc.renderFact());
        }
        return facts;
    }

    /** Roll loot for an adventurer (declarative GIVE_LOOT effect). */
    void grantLoot(Adventurer actor) {
        Item loot = dungeonGenerator.generateLoot();
        if (loot != null && actor != null) {
            actor.addItem(loot);
            log("LOOT FOUND: " + loot);
        } else if (actor != null) {
            log(actor.getName() + " found a glimmering artifact.");
        }
    }

    /** Raise the chaos level, capped at 10 (declarative INCREASE_CHAOS effect). */
    void bumpChaos(int delta) {
        chaosLevel = Math.min(10, Math.max(0, chaosLevel + delta));
    }

    void triggerCombatEncounter() {
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
        Quest quest = currentQuest;
        chronicle.record("combat_won", (quest != null) ? quest.getTitle() : "", "");

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
            GameStateData data = new GameStateData(party, currentQuest, chaosLevel, difficulty,
                    worldState, currentQuestScriptId,
                    campaign != null ? campaign.getId() : null, chronicle);
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
            // Phase-2 fields are additive: pre-worldState saves load with fresh
            // narrative state and no campaign, exactly as they played before.
            worldState = (data.worldState != null) ? data.worldState : new WorldState();
            currentQuestScriptId = (data.currentQuestScriptId != null)
                    ? data.currentQuestScriptId : QuestScriptRegistry.DEFAULT_SCRIPT;
            campaign = CampaignRegistry.get(data.campaignId);
            campaignExhaustedAnnounced = false;
            chronicle = (data.chronicle != null) ? data.chronicle : new Chronicle();
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
            chronicle.record("party_fallen", "", "");
            broadcast("FATAL: All adventurers are dead.");
            return;
        }

        if (combatState.isActive()) {
            Entity next = combatState.nextTurn();
            if (next != null) next.onTurnStart(this);

            if (combatState.isVictory()) { handleCombatVictory(); return; }
            if (combatState.isDefeat())  { combatState.setInactive(); chronicle.record("party_fallen", "", ""); broadcast("FATAL: All adventurers are dead."); return; }
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
        if (scene == null) return Collections.emptyList();
        // Hide choices whose declarative condition the world doesn't satisfy.
        Adventurer lead = party.isEmpty() ? null : party.get(0);
        List<Choice> visible = new ArrayList<>();
        for (Choice c : scene.getChoices()) {
            if (c.isAvailable(this, lead)) visible.add(c);
        }
        return visible;
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

    /**
     * Structured snapshot of the party for the v2 API. Unlike
     * {@link #getPartySummary()} (a human-readable string kept for the Swing
     * GUI and CLI), this returns typed {@link MemberState} value objects so
     * clients never parse server-formatted text.
     */
    public PartyState getPartyState() {
        List<MemberState> members = new ArrayList<>();
        synchronized (party) {
            for (Adventurer a : party) {
                List<String> statuses = new ArrayList<>();
                for (StatusEffect e : a.getActiveEffects()) {
                    statuses.add(e.getName());
                }
                members.add(new MemberState(
                        a.getName(), a.getRole(), a.getLevel(),
                        a.getHp(), a.getMaxHp(), a.getMana(), a.getMaxMana(),
                        a.getAC(), a.isAlive(), a.isAscendant(), statuses));
            }
        }
        return new PartyState(members);
    }

    /**
     * Generate a dungeon-master narration for the given player prompt through
     * the active {@link LLMProvider}, broadcast it to all UI listeners, and
     * return the raw response (text + token/fallback metadata) for the API.
     */
    public LLMProvider.NarrativeResponse narrate(String userPrompt) {
        LLMProvider provider = narrator;
        String scene = (currentQuest != null) ? currentQuest.getTitle() : "the drifting rift";
        LLMProvider.NarrativePrompt prompt = new LLMProvider.NarrativePrompt(
                userPrompt == null ? "" : userPrompt, scene, 256, narrationFacts());
        LLMProvider.NarrativeResponse response = provider.generate(prompt);
        broadcast(response.text);
        return response;
    }

    /**
     * Streaming variant of {@link #narrate}: routes the prompt through the
     * active provider and forwards each chunk to {@code onChunk} as it arrives,
     * returning the full aggregated response. Unlike {@link #narrate} it does
     * not broadcast — the streaming caller delivers the chunks itself.
     */
    public LLMProvider.NarrativeResponse narrateStreaming(String userPrompt, java.util.function.Consumer<String> onChunk) {
        LLMProvider provider = narrator;
        String scene = (currentQuest != null) ? currentQuest.getTitle() : "the drifting rift";
        LLMProvider.NarrativePrompt prompt = new LLMProvider.NarrativePrompt(
                userPrompt == null ? "" : userPrompt, scene, 256, narrationFacts());
        return provider.generateStreaming(prompt, onChunk);
    }

    /** Swap the narration backend (e.g., a budgeted + moderated provider stack). */
    public void setNarrator(LLMProvider provider) {
        if (provider != null) this.narrator = provider;
    }

    /** The active narration backend. */
    public LLMProvider getNarrator() {
        return narrator;
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
    /** Bumped when the save schema gains fields; readers treat missing as 1. */
    public int saveVersion = 3;

    public List<Adventurer> party;
    public Quest currentQuest;
    public int chaosLevel;
    public int difficulty;

    // Phase-2 additive fields — null in pre-worldState saves.
    public WorldState worldState;
    public String currentQuestScriptId;
    public String campaignId;

    // Phase-3 additive field — null in pre-chronicle saves.
    public Chronicle chronicle;

    public GameStateData() {}

    GameStateData(List<Adventurer> p, Quest q, int c, int d,
                  WorldState world, String questScriptId, String campaignId,
                  Chronicle chronicle) {
        this.party = p;
        this.currentQuest = q;
        this.chaosLevel = c;
        this.difficulty = d;
        this.worldState = world;
        this.currentQuestScriptId = questScriptId;
        this.campaignId = campaignId;
        this.chronicle = chronicle;
    }
}
