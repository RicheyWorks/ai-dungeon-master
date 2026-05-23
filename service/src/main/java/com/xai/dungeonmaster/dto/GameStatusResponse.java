package com.xai.dungeonmaster.dto;

import java.util.List;

/**
 * Snapshot of the current game state returned to the client.
 */
public class GameStatusResponse {

    private String partySummary;
    private int chaosLevel;
    private boolean combatActive;
    private List<String> availableChoices;
    private List<String> recentHistory;

    public GameStatusResponse() {}

    // ─── Getters & setters ────────────────────────────────────────────────────

    public String getPartySummary() { return partySummary; }
    public void setPartySummary(String partySummary) { this.partySummary = partySummary; }

    public int getChaosLevel() { return chaosLevel; }
    public void setChaosLevel(int chaosLevel) { this.chaosLevel = chaosLevel; }

    public boolean isCombatActive() { return combatActive; }
    public void setCombatActive(boolean combatActive) { this.combatActive = combatActive; }

    public List<String> getAvailableChoices() { return availableChoices; }
    public void setAvailableChoices(List<String> availableChoices) { this.availableChoices = availableChoices; }

    public List<String> getRecentHistory() { return recentHistory; }
    public void setRecentHistory(List<String> recentHistory) { this.recentHistory = recentHistory; }
}
