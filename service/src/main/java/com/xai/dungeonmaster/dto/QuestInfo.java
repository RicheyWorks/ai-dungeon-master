package com.xai.dungeonmaster.dto;

import com.xai.dungeonmaster.Quest;

/**
 * Typed quest snapshot for the v2 status payload (ADR-001 wrap-up).
 * Exposes the current quest's outcome state so clients can render
 * progress and endings without parsing narration text.
 */
public record QuestInfo(
        String title,
        boolean completed,
        boolean failed,
        double progress
) {
    /** Map a live Quest (nullable) to its API snapshot. */
    public static QuestInfo from(Quest quest) {
        if (quest == null) return null;
        return new QuestInfo(
                quest.getTitle(),
                quest.isCompleted(),
                quest.isFailed(),
                quest.getProgressPercentage());
    }
}
