package com.xai.dungeonmaster.dto;

import java.util.List;

/** Active narration provider + health, plus everything available to switch to. */
public record NarrationInfo(
        String active,
        String health,
        List<String> available) {}
