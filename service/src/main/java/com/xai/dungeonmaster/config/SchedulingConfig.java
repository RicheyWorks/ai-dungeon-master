package com.xai.dungeonmaster.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables {@code @Scheduled} beans (e.g. idle game-instance reaper). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
