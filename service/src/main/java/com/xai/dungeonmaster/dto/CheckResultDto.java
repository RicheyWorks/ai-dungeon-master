package com.xai.dungeonmaster.dto;

import com.xai.dungeonmaster.CheckResult;

/** Wire form of a cinematic check (G3). */
public record CheckResultDto(
        String kind,
        String actor,
        String target,
        String stakes,
        int roll,
        int modifier,
        int total,
        int difficulty,
        boolean success,
        boolean critical,
        boolean fumble,
        String effect,
        String narration,
        long atEpochMs,
        boolean pushedLuck
) {
    public static CheckResultDto from(CheckResult c) {
        if (c == null) return null;
        return new CheckResultDto(
                c.getKind(), c.getActor(), c.getTarget(), c.getStakes(),
                c.getRoll(), c.getModifier(), c.getTotal(), c.getDifficulty(),
                c.isSuccess(), c.isCritical(), c.isFumble(),
                c.getEffect(), c.getNarration(), c.getAtEpochMs(), c.isPushedLuck());
    }
}
