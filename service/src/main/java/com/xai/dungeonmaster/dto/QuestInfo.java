package com.xai.dungeonmaster.dto;

import com.xai.dungeonmaster.Quest;
import com.xai.dungeonmaster.Scene;

/**
 * Typed quest snapshot for the v2 status payload (ADR-001 wrap-up).
 * Exposes the current quest's outcome state and scene framing so clients can
 * render cold-open prose without parsing narration text.
 */
public record QuestInfo(
        String title,
        boolean completed,
        boolean failed,
        double progress,
        String sceneId,
        String sceneDescription,
        /** Quest-level blurb for SPA framing (G9). */
        String description
) {
    /** Map a live Quest (nullable) to its API snapshot. */
    public static QuestInfo from(Quest quest) {
        if (quest == null) return null;
        Scene scene = quest.getCurrentScene();
        return new QuestInfo(
                quest.getTitle(),
                quest.isCompleted(),
                quest.isFailed(),
                quest.getProgressPercentage(),
                scene != null ? scene.getId() : null,
                scene != null ? scene.getDescription() : null,
                quest.getDescription());
    }
}
