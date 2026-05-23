package com.xai.dungeonmaster;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.swing.*;

/**
 * Launches the Swing GUI after the Spring application context is fully
 * initialised (i.e., after the WebSocket listener in GameConfig has already
 * called engine.addUiListener for the SimpMessagingTemplate).
 *
 * Disabled by default — the app boots headless as a pure web/STOMP server
 * so it can run on a mobile backend, container, or any non-desktop JVM.
 *
 * To run with the Swing GUI locally, launch with:
 *   java -jar app.jar --game.gui.enabled=true
 * (DungeonMasterApplication.main() detects this flag and flips
 *  java.awt.headless to false before Spring initialises.)
 *
 * Threading contract:
 *   Spring's ApplicationRunner.run() is called on the main thread.
 *   All Swing construction is dispatched to the EDT via invokeLater — never
 *   block here, just dispatch and return so the web server continues normally.
 *
 * Both the WebSocket push and the Swing repaint fire for every engine.broadcast()
 * because DungeonAdventureGui calls engine.addUiListener() (not setUiUpdater),
 * preserving GameConfig's existing listener.
 */
@Component
@ConditionalOnProperty(name = "game.gui.enabled", havingValue = "true", matchIfMissing = false)
public class GuiLauncher implements ApplicationRunner {

    private final DungeonMasterEngine engine;

    public GuiLauncher(DungeonMasterEngine engine) {
        this.engine = engine;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Swing system L&F — optional cosmetic tweak for Windows
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Fall back to default Metal L&F — not a blocking error
        }

        /*
         * Dispatch to EDT.  ApplicationRunner.run() returns immediately so
         * Spring doesn't stall waiting for the GUI window to close.
         * The web server and WebSocket endpoints stay live alongside the GUI.
         */
        SwingUtilities.invokeLater(() -> {
            DungeonAdventureGui gui = new DungeonAdventureGui(engine);
            gui.setVisible(true);
        });
    }
}
