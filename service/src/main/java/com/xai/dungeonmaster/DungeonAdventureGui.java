package com.xai.dungeonmaster;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Swing GUI for the Multiversal Dungeon Master.
 *
 * Spring Boot changes vs the original:
 *  - Engine is now INJECTED by GuiLauncher — no self-contained engine creation.
 *  - Constructor signature: DungeonAdventureGui(DungeonMasterEngine engine).
 *  - setVisible(true) is called by GuiLauncher on the EDT, not the constructor.
 *  - main() removed — DungeonMasterApplication is the entry point.
 *  - engine.addUiListener() used so the WebSocket listener in GameConfig is
 *    preserved alongside this Swing listener (both fire on every broadcast).
 *
 * Everything else — health bars, spell menu, flash animation, boss banner,
 * turn history panel — is unchanged from the original.
 */
public class DungeonAdventureGui extends JFrame {

    private static final Color BG    = new Color(15, 15, 22);
    private static final Color TEXT  = new Color(230, 230, 235);
    private static final Color GOLD  = new Color(212, 170, 85);
    private static final Color RED   = new Color(140, 45, 55);
    private static final Color GREEN = new Color(80, 210, 130);

    private final DungeonMasterEngine engine;

    private JTextArea narrationArea;
    private JTextArea turnHistoryArea;
    private JPanel buttonPanel;
    private JLabel statusLabel;
    private JLabel healthBarsLabel;
    private JLabel bossBannerLabel;

    private final ExecutorService threadPool = Executors.newSingleThreadExecutor();

    // ──────────────────────────────────────────────────────────────────────────
    // Constructor — called by GuiLauncher on the Swing EDT
    // ──────────────────────────────────────────────────────────────────────────

    public DungeonAdventureGui(DungeonMasterEngine engine) {
        this.engine = engine;

        setupWindow();
        initComponents();

        /*
         * addUiListener (not setUiUpdater) so the WebSocket SimpMessagingTemplate
         * listener registered in GameConfig is NOT wiped.  Every broadcast now
         * fans out to both WebSocket subscribers AND this Swing panel.
         */
        engine.addUiListener(text -> SwingUtilities.invokeLater(() -> {
            appendNarration(text);
            checkBossWarning(text);
            refreshUI();
        }));

        appendNarration("=== JOURNEY BEGINS ===\n" + engine.startQuest());
        refreshUI();
        // setVisible(true) is called by GuiLauncher after construction
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Window + component setup (unchanged from original)
    // ──────────────────────────────────────────────────────────────────────────

    private void setupWindow() {
        setTitle("AI Dungeon Master — Spring Boot Edition");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1300, 880);
        setMinimumSize(new Dimension(1000, 700));
        setLayout(new BorderLayout(12, 12));
        getContentPane().setBackground(BG);
        setLocationRelativeTo(null);
    }

    private void initComponents() {
        statusLabel = new JLabel("Party Status: Initializing...");
        statusLabel.setOpaque(true);
        statusLabel.setBackground(new Color(20, 20, 30));
        statusLabel.setForeground(GOLD);
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        statusLabel.setBorder(new EmptyBorder(8, 16, 8, 16));

        healthBarsLabel = new JLabel();
        healthBarsLabel.setOpaque(true);
        healthBarsLabel.setBackground(new Color(20, 20, 30));
        healthBarsLabel.setForeground(TEXT);
        healthBarsLabel.setFont(new Font("Monospaced", Font.PLAIN, 15));
        healthBarsLabel.setBorder(new EmptyBorder(12, 16, 12, 16));

        bossBannerLabel = new JLabel(" ");
        bossBannerLabel.setOpaque(true);
        bossBannerLabel.setBackground(new Color(90, 0, 0));
        bossBannerLabel.setForeground(Color.WHITE);
        bossBannerLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        bossBannerLabel.setHorizontalAlignment(SwingConstants.CENTER);
        bossBannerLabel.setBorder(new EmptyBorder(10, 10, 10, 10));
        bossBannerLabel.setVisible(false);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(BG);
        topPanel.add(statusLabel, BorderLayout.NORTH);
        topPanel.add(healthBarsLabel, BorderLayout.CENTER);
        topPanel.add(bossBannerLabel, BorderLayout.SOUTH);
        add(topPanel, BorderLayout.NORTH);

        narrationArea = createTextArea(16, TEXT, new Color(18, 18, 26));
        JScrollPane mainScroll = new JScrollPane(narrationArea);
        mainScroll.setBorder(createTitleBorder("Chronicle"));
        add(mainScroll, BorderLayout.CENTER);

        turnHistoryArea = createTextArea(13, GREEN, new Color(10, 12, 10));
        JScrollPane historyScroll = new JScrollPane(turnHistoryArea);
        historyScroll.setBorder(createTitleBorder("Cosmic Log"));

        JPanel historyPanel = new JPanel(new BorderLayout());
        historyPanel.setBackground(BG);
        historyPanel.setBorder(new EmptyBorder(0, 0, 0, 12));
        historyPanel.add(historyScroll, BorderLayout.CENTER);
        historyPanel.setPreferredSize(new Dimension(340, 0));
        add(historyPanel, BorderLayout.EAST);

        buttonPanel = new JPanel(new GridLayout(0, 3, 12, 12));
        buttonPanel.setBackground(BG);
        buttonPanel.setBorder(new EmptyBorder(14, 14, 16, 14));
        add(buttonPanel, BorderLayout.SOUTH);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // UI refresh (unchanged from original)
    // ──────────────────────────────────────────────────────────────────────────

    private void refreshUI() {
        autoResolveNonPlayerTurns();

        buttonPanel.removeAll();
        statusLabel.setText("Chaos Level: " + engine.getChaosLevel());
        buildHealthBars();

        List<Choice> choices = engine.getCurrentAvailableChoices();
        if (choices == null) choices = Collections.emptyList();

        for (Choice choice : choices) {
            if (choice.getLabel().equalsIgnoreCase("Spell")) {
                addSpellButton();
            } else {
                addChoiceButton(choice);
            }
        }

        addSystemButton("Save", () -> engine.saveGame("savegame.json"));
        addSystemButton("Load", () -> engine.loadGame("savegame.json"));

        buttonPanel.revalidate();
        buttonPanel.repaint();
        updateTurnHistory();
    }

    private void buildHealthBars() {
        StringBuilder sb = new StringBuilder("<html><b>🏴‍☠️ PARTY</b><br>");
        for (String line : engine.getPartySummary().split(" \\| ")) {
            sb.append(line).append("<br>");
        }
        sb.append("<br><b>ENEMIES</b><br>");

        List<Entity> enemies = engine.getCombatState().getEnemies();
        if (enemies.isEmpty()) {
            sb.append("No enemies in sight.<br>");
        } else {
            for (Entity e : enemies) {
                int hp      = e.getHp();
                int max     = e.getMaxHp();
                int percent = max > 0 ? (hp * 100 / max) : 0;
                String bar  = "█".repeat(percent / 10) + "░".repeat(10 - percent / 10);
                String color = percent > 60 ? "#50C878" : percent > 30 ? "#FFB800" : "#E04E4E";
                sb.append(e.getName())
                  .append(" <font color='").append(color).append("'>[")
                  .append(bar).append("]</font> ")
                  .append(hp).append("/").append(max).append("<br>");
            }
        }
        sb.append("</html>");
        healthBarsLabel.setText(sb.toString());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Spell menu (unchanged from original)
    // ──────────────────────────────────────────────────────────────────────────

    private void addSpellButton() {
        JButton btn = new JButton("Cast Spell");
        styleButton(btn, new Color(100, 0, 180), Color.WHITE);
        btn.addActionListener(e -> threadPool.submit(this::showSpellMenu));
        buttonPanel.add(btn);
    }

    private void showSpellMenu() {
        Entity current = engine.getCombatState().getCurrentEntity();
        if (!(current instanceof Adventurer adv)) {
            JOptionPane.showMessageDialog(this, "No one here can cast spells!", "Rift Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<Spell> spells = adv.getKnownSpells();
        if (spells.isEmpty()) {
            JOptionPane.showMessageDialog(this, adv.getName() + " knows no spells yet.", "Silent Rift", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JDialog spellDialog = new JDialog(this, "Choose Spell - " + adv.getName(), true);
        spellDialog.setLayout(new GridLayout(0, 1, 8, 8));
        spellDialog.setSize(420, 300);
        spellDialog.setLocationRelativeTo(this);

        for (Spell spell : spells) {
            JButton spellBtn = new JButton(spell.getName() + " (" + spell.getManaCost() + " MP)");
            spellBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
            spellBtn.addActionListener(ev -> {
                String result = spell.cast(engine, adv, engine.getCombatState().getRandomEnemy());
                engine.broadcast(result);
                flashHealthBar(true);
                spellDialog.dispose();
                SwingUtilities.invokeLater(this::refreshUI);
            });
            spellDialog.add(spellBtn);
        }

        spellDialog.setVisible(true);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Flash animation (unchanged from original)
    // ──────────────────────────────────────────────────────────────────────────

    private void flashHealthBar(boolean isDamage) {
        Color original = healthBarsLabel.getBackground();
        healthBarsLabel.setBackground(isDamage ? new Color(180, 30, 30) : new Color(30, 140, 60));
        Timer flash = new Timer(180, e -> {
            healthBarsLabel.setBackground(original);
            ((Timer) e.getSource()).stop();
        });
        flash.setRepeats(false);
        flash.start();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Button factory helpers (unchanged from original)
    // ──────────────────────────────────────────────────────────────────────────

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

    private void addChoiceButton(Choice choice) {
        JButton btn = new JButton(choice.getLabel());
        btn.setToolTipText(choice.getResultText());
        styleButton(btn, RED, Color.WHITE);
        btn.addActionListener(e -> threadPool.submit(() -> {
            engine.handleChoice(choice);
            flashHealthBar(true);
            SwingUtilities.invokeLater(this::refreshUI);
        }));
        buttonPanel.add(btn);
    }

    private void addSystemButton(String label, Runnable action) {
        JButton btn = new JButton(label);
        styleButton(btn, new Color(45, 45, 60), GOLD);
        btn.addActionListener(e -> threadPool.submit(() -> {
            action.run();
            SwingUtilities.invokeLater(this::refreshUI);
        }));
        buttonPanel.add(btn);
    }

    private void styleButton(JButton btn, Color bg, Color fg) {
        btn.setFocusPainted(false);
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 15));
        btn.setBorder(new LineBorder(GOLD, 1));
        btn.setPreferredSize(new Dimension(160, 48));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Text area / border helpers (unchanged from original)
    // ──────────────────────────────────────────────────────────────────────────

    private JTextArea createTextArea(int fontSize, Color foreground, Color background) {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font("Segoe UI", Font.PLAIN, fontSize));
        area.setMargin(new Insets(14, 14, 14, 14));
        area.setBackground(background);
        area.setForeground(foreground);
        area.setCaretColor(foreground);
        DefaultCaret caret = (DefaultCaret) area.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        return area;
    }

    private TitledBorder createTitleBorder(String title) {
        TitledBorder border = BorderFactory.createTitledBorder(new LineBorder(GOLD, 1), title);
        border.setTitleColor(GOLD);
        border.setTitleFont(new Font("Segoe UI", Font.BOLD, 14));
        return border;
    }

    void appendNarration(String text) {
        if (text == null || text.isBlank()) return;
        if (narrationArea.getText().isBlank()) {
            narrationArea.setText(text);
        } else {
            narrationArea.append("\n\n" + text);
        }
        narrationArea.setCaretPosition(narrationArea.getDocument().getLength());
    }

    void checkBossWarning(String text) {
        if (text == null || bossBannerLabel == null) return;
        String upper = text.toUpperCase();
        if (upper.contains("BOSS ENCOUNTER") || upper.contains("BOSS")) {
            bossBannerLabel.setText("⚠ BOSS ENCOUNTER ⚠");
            bossBannerLabel.setVisible(true);
            new Timer(3500, e -> {
                bossBannerLabel.setVisible(false);
                bossBannerLabel.setText(" ");
            }).start();
        }
    }

    private void updateTurnHistory() {
        List<String> history = engine.getTurnHistory();
        if (history == null) return;
        int start = Math.max(0, history.size() - 35);
        turnHistoryArea.setText(String.join("\n", history.subList(start, history.size())));
    }

    @Override
    public void dispose() {
        threadPool.shutdownNow();
        super.dispose();
    }
}
