package com.xai.dungeonmaster;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Scanner;

/**
 * Optional CLI mode for the Spring Boot app.
 *
 * Enabled by adding this to application.properties:
 *   game.cli.enabled=true
 *
 * When disabled (the default), the app runs as a pure web server and the
 * terminal stays quiet except for Spring's own startup log.
 *
 * The CLI no longer drives its own DungeonMasterEngine — it shares the same
 * singleton bean that the REST/WebSocket controllers use, so saving in the
 * CLI and loading via the REST API (or vice-versa) all work on the same state.
 */
@Component
@ConditionalOnProperty(name = "game.cli.enabled", havingValue = "true")
public class DungeonCli implements CommandLineRunner {

    public static final String RESET  = "\u001B[0m";
    public static final String GREEN  = "\u001B[32m";
    public static final String RED    = "\u001B[31m";
    public static final String CYAN   = "\u001B[36m";
    public static final String YELLOW = "\u001B[33m";

    private final DungeonMasterEngine engine;

    /** Spring injects the same engine bean created in GameConfig. */
    public DungeonCli(DungeonMasterEngine engine) {
        this.engine = engine;
    }

    @Override
    public void run(String... args) {
        Scanner scanner = new Scanner(System.in);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println(RED + "\n[System] Cosmic tether severed." + RESET);
            scanner.close();
        }));

        printHeader();

        // Print the narrative as it arrives from the engine.
        // Use addUiListener (NOT setUiUpdater) so we coexist with the WebSocket
        // listener registered by GameConfig — otherwise the CLI would silently
        // disconnect every connected browser/native client.
        engine.addUiListener(text -> {
            System.out.println(CYAN + "\n»»————- DREAD NARRATIVE ————-««" + RESET);
            System.out.println(text);
            System.out.println(CYAN + "»»—————————————————————————««\n" + RESET);
        });

        System.out.println(GREEN + engine.startQuest() + RESET);
        commandLoop(scanner);
    }

    private void commandLoop(Scanner scanner) {
        boolean running = true;

        while (running) {
            autoResolveNonPlayerTurns();

            List<Choice> options = engine.getCurrentAvailableChoices();
            printStatusLine();
            printActionMenu(options);

            int input = promptInt(scanner, YELLOW + "> Select Action: " + RESET,
                    0, options.size() + 2);

            try {
                if (input == 0) {
                    running = false;
                } else if (input == options.size() + 1) {
                    engine.saveGame("savegame.json");
                    System.out.println(GREEN + "Timeline persisted to disk." + RESET);
                } else if (input == options.size() + 2) {
                    engine.loadGame("savegame.json");
                    System.out.println(GREEN + "Timeline restored." + RESET);
                } else {
                    engine.handleChoice(options.get(input - 1));
                }
            } catch (Exception e) {
                System.out.println(RED + "[Error] The void rejected that action: " + e.getMessage() + RESET);
            }
        }
    }

    private void autoResolveNonPlayerTurns() {
        int safety = 0;
        while (engine.getCombatState().isActive() && safety < 25) {
            Entity current = engine.getCombatState().getCurrentEntity();
            if (current == null || current instanceof Adventurer) return;
            current.onTurnStart(engine);
            if (!engine.getCombatState().isActive()) return;
            engine.getCombatState().nextTurn();
            safety++;
        }
    }

    private void printStatusLine() {
        System.out.println(YELLOW + "Party Status: " + engine.getPartySummary()
                + " | Chaos: " + engine.getChaosLevel() + RESET);
    }

    private void printActionMenu(List<Choice> options) {
        System.out.println("Available Actions:");
        for (int i = 0; i < options.size(); i++) {
            System.out.printf(" [%d] %s\n", (i + 1), options.get(i).getLabel());
        }
        System.out.printf(" [%d] Save Game | [%d] Load Game | [0] Exit\n",
                options.size() + 1, options.size() + 2);
    }

    private int promptInt(Scanner scanner, String message, int min, int max) {
        while (true) {
            System.out.print(message);
            try {
                int val = Integer.parseInt(scanner.nextLine().trim());
                if (val >= min && val <= max) return val;
                System.out.printf(RED + "Invalid choice. Range: %d - %d\n" + RESET, min, max);
            } catch (NumberFormatException e) {
                System.out.println(RED + "Numeric input required." + RESET);
            }
        }
    }

    private void printHeader() {
        System.out.println(GREEN + """
                ╔═════════════════════════════════════════════════════════╗
                ║           MULTIVERSAL ASCENDANT: DUNGEON MASTER         ║
                ║             - Spring Boot Edition v2.0 -                ║
                ╚═════════════════════════════════════════════════════════╝
                """ + RESET);
    }
}
