package com.xai.dungeonmaster.config;

import com.xai.dungeonmaster.auth.FileSessionStore;
import com.xai.dungeonmaster.auth.InMemorySessionStore;
import com.xai.dungeonmaster.auth.SessionStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;

/**
 * Auth-related beans. Selects the {@link SessionStore} implementation from
 * config: {@code game.auth.session.store = memory} (default) or {@code file},
 * with {@code game.auth.session.file} naming the JSON file for the file store.
 */
@Configuration
public class AuthConfig {

    @Bean
    public SessionStore sessionStore(
            @Value("${game.auth.session.store:memory}") String kind,
            @Value("${game.auth.session.file:sessions.json}") String file) {
        if ("file".equalsIgnoreCase(kind)) {
            System.out.println("[auth] session store: file (" + file + ")");
            return new FileSessionStore(Paths.get(file));
        }
        return new InMemorySessionStore();
    }
}
