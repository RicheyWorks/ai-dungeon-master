package com.xai.dungeonmaster;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Arrays;

/**
 * Entry point for the Multiversal Dungeon Master — Spring Boot edition.
 *
 * Replaces both DungeonCli.main() and DungeonAdventureGui.main().
 * The game engine is wired as a singleton Spring bean and exposed via:
 *   - REST API:   http://localhost:8080/api/game/**
 *   - WebSocket:  ws://localhost:8080/ws  (STOMP, topic /topic/narrative)
 *
 * Headless by default. Spring Boot sets java.awt.headless=true at startup,
 * which is what we want for server / mobile-backend deployments. The Swing
 * desktop GUI is opt-in via:
 *
 *   java -jar app.jar --game.gui.enabled=true
 *
 * When the GUI flag is detected on the command line, we flip headless to
 * false BEFORE Spring boots so AWT can initialise.
 */
@SpringBootApplication
public class DungeonMasterApplication {

    public static void main(String[] args) {
        if (isGuiRequested(args)) {
            // Must happen BEFORE SpringApplication.run() — Spring's
            // auto-configuration sets headless during context refresh and
            // anything later is too late for AWT.
            System.setProperty("java.awt.headless", "false");
        }
        SpringApplication.run(DungeonMasterApplication.class, args);
    }

    /**
     * Looks for --game.gui.enabled=true or a short --gui flag in argv.
     * Returns false for any other input so the JVM stays headless.
     */
    static boolean isGuiRequested(String[] args) {
        if (args == null) return false;
        return Arrays.stream(args).anyMatch(a ->
                "--gui".equals(a)
                || a.startsWith("--game.gui.enabled=true")
                || a.startsWith("-Dgame.gui.enabled=true"));
    }
}
