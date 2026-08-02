package com.xai.dungeonmaster.content;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SessionPackConfig {

    @Bean
    public SessionPackStore sessionPackStore() {
        return new MemorySessionPackStore();
    }
}
