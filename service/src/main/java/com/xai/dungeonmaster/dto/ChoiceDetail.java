package com.xai.dungeonmaster.dto;

import com.xai.dungeonmaster.Choice;

/**
 * Player-facing choice row for SPA/mobile (label + optional stakes).
 * {@code irreversible} is a UI hint when the choice sets world flags.
 */
public record ChoiceDetail(
        String label,
        String stakes,
        boolean irreversible
) {
    public static ChoiceDetail from(Choice choice) {
        if (choice == null) return null;
        String stakes = choice.getStakes();
        boolean irreversible = choice.isStoryFlagChoice()
                || (stakes != null && stakes.toLowerCase().contains("irreversible"));
        return new ChoiceDetail(choice.getLabel(), stakes, irreversible);
    }
}
