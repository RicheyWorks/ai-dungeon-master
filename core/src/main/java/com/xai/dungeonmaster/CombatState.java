package com.xai.dungeonmaster;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Robust Combat Manager for the Multiverse.
 * Handles turn cycles, initiative sorting, target selection,
 * victory/defeat detection, and dynamic entity management.
 */
public class CombatState {

    private final List<Adventurer> party = Collections.synchronizedList(new ArrayList<>());
    private final List<Entity> enemies = Collections.synchronizedList(new ArrayList<>());
    private final List<Summon> summons = Collections.synchronizedList(new ArrayList<>());

    private final Queue<Entity> turnOrder = new ConcurrentLinkedQueue<>();

    private int currentRound = 1;
    private boolean active = false;

    private Entity initiativeLeader;

    // ------------------------------
    // Combat Initialization
    // ------------------------------

    public void initialize(List<Adventurer> players, List<? extends Entity> foes) {
        synchronized (party) {
            party.clear();
            if (players != null) {
                party.addAll(players.stream()
                        .filter(Objects::nonNull)
                        .filter(Entity::isAlive)
                        .collect(Collectors.toList()));
            }
        }

        synchronized (enemies) {
            enemies.clear();
            if (foes != null) {
                enemies.addAll(foes.stream()
                        .filter(Objects::nonNull)
                        .filter(Entity::isAlive)
                        .collect(Collectors.toList()));
            }
        }

        synchronized (summons) {
            summons.clear();
        }

        currentRound = 1;
        active = hasLivingPartyMembers() && hasLivingEnemies();
        recalculateTurnOrder();
    }

    // ------------------------------
    // Initiative & Cleanup
    // ------------------------------

    public void recalculateTurnOrder() {
        cleanupDeadParticipants();

        List<Entity> all = getAllActiveParticipants();
        all.sort(Comparator.comparingInt(Entity::getAC)
                .thenComparingInt(Entity::getLevel)
                .reversed());

        turnOrder.clear();
        turnOrder.addAll(all);
        initiativeLeader = all.isEmpty() ? null : all.get(0);

        active = hasLivingPartyMembers() && hasLivingEnemies();
    }

    // ------------------------------
    // Turn Flow
    // ------------------------------

    public Entity getCurrentEntity() {
        cleanupTurnOrder();
        return isActive() ? turnOrder.peek() : null;
    }

    public Entity nextTurn() {
        cleanupTurnOrder();
        if (!isActive()) {
            active = false;
            return null;
        }

        int attempts = 0;
        int size = turnOrder.size();

        while (attempts < size) {
            Entity current = turnOrder.poll();
            attempts++;

            if (current != null && current.isAlive()) {
                if (current == initiativeLeader) {
                    currentRound++;
                }
                turnOrder.add(current);
                return current;
            }
        }

        recalculateTurnOrder();
        return turnOrder.peek();
    }

    // ------------------------------
    // Dynamic Participation
    // ------------------------------

    public void addSummon(Summon summon) {
        if (summon == null || !summon.isAlive()) return;

        synchronized (summons) {
            summons.add(summon);
        }
        recalculateTurnOrder();
    }

    // ------------------------------
    // Target Selection
    // ------------------------------

    public Entity getRandomEnemy() {
        List<Entity> alive = getEnemies();
        return alive.isEmpty() ? null : alive.get(ThreadLocalRandom.current().nextInt(alive.size()));
    }

    public Entity getRandomPartyMember() {
        List<Adventurer> alive = getLivingPartyMembers();
        return alive.isEmpty() ? null : alive.get(ThreadLocalRandom.current().nextInt(alive.size()));
    }

    // ------------------------------
    // State Queries
    // ------------------------------

    public List<Entity> getEnemies() {
        synchronized (enemies) {
            return enemies.stream()
                    .filter(Objects::nonNull)
                    .filter(Entity::isAlive)
                    .collect(Collectors.toList());
        }
    }

    public List<Adventurer> getLivingPartyMembers() {
        synchronized (party) {
            return party.stream()
                    .filter(Objects::nonNull)
                    .filter(Entity::isAlive)
                    .collect(Collectors.toList());
        }
    }

    public boolean isActive() {
        return active && hasLivingPartyMembers() && hasLivingEnemies();
    }

    public boolean isVictory() {
        return hasLivingPartyMembers() && !hasLivingEnemies();
    }

    public boolean isDefeat() {
        return !hasLivingPartyMembers();
    }

    public void setInactive() {
        active = false;
        turnOrder.clear();
    }

    public int getCurrentRound() {
        return currentRound;
    }

    // ------------------------------
    // Internal Helpers
    // ------------------------------

    private boolean hasLivingPartyMembers() {
        synchronized (party) {
            return party.stream().anyMatch(e -> e != null && e.isAlive());
        }
    }

    private boolean hasLivingEnemies() {
        synchronized (enemies) {
            return enemies.stream().anyMatch(e -> e != null && e.isAlive());
        }
    }

    private void cleanupDeadParticipants() {
        synchronized (party) { party.removeIf(e -> e == null || !e.isAlive()); }
        synchronized (enemies) { enemies.removeIf(e -> e == null || !e.isAlive()); }
        synchronized (summons) { summons.removeIf(e -> e == null || !e.isAlive()); }
        cleanupTurnOrder();
    }

    private void cleanupTurnOrder() {
        turnOrder.removeIf(e -> e == null || !e.isAlive());
    }

    private List<Entity> getAllActiveParticipants() {
        List<Entity> all = new ArrayList<>();

        synchronized (party) {
            all.addAll(party.stream().filter(Objects::nonNull).filter(Entity::isAlive).collect(Collectors.toList()));
        }
        synchronized (enemies) {
            all.addAll(enemies.stream().filter(Objects::nonNull).filter(Entity::isAlive).collect(Collectors.toList()));
        }
        synchronized (summons) {
            all.addAll(summons.stream().filter(Objects::nonNull).filter(Entity::isAlive).collect(Collectors.toList()));
        }
        return all;
    }
}
