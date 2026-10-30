/*
 * Copyright (c) 2026 SuperSirvu
 *
 * Licensed under the MIT License.
 */

package net.supersirvu.gui;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelData;

import javax.swing.*;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumMap;
import java.util.Locale;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class EnhancedServerMenuBar extends JMenuBar {
    private static final int MAX_MOTD_LINES = 2;

    private final DedicatedServer server;
    private final Frame parentFrame;

    public EnhancedServerMenuBar(DedicatedServer server, Frame parentFrame) {
        this.server = server;
        this.parentFrame = parentFrame;

        // Build the menus synchronously: this class is only ever constructed on the
        // EDT (from the GUI install), so populating the bar here guarantees all the
        // menus are present before the frame is revalidated and repainted.
        createMenus();
    }

    private void createMenus() {
        add(createServerMenu());
        add(createWorldMenu());
        add(createNetworkMenu());
        add(createPerformanceMenu());
        add(createToolsMenu());
        add(createHelpMenu());
    }

    /**
     * Runs a console command on the server thread, exactly like the vanilla
     * console input handler, so heavy commands never block the Swing GUI.
     */
    private void runCommand(String command) {
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }

    // ==================== SERVER MENU ====================
    private JMenu createServerMenu() {
        JMenu serverMenu = new JMenu("Server");
        serverMenu.setMnemonic('S');

        JMenuItem propertiesItem = new JMenuItem("Server Properties...");
        propertiesItem.addActionListener(e -> showServerProperties());
        serverMenu.add(propertiesItem);

        JMenuItem whitelistItem = new JMenuItem("Whitelist Settings...");
        whitelistItem.addActionListener(e -> showWhitelistSettings());
        serverMenu.add(whitelistItem);

        // Difficulty submenu
        JMenu difficultyMenu = new JMenu("Difficulty");
        ButtonGroup difficultyGroup = new ButtonGroup();
        for (Difficulty difficulty : Difficulty.values()) {
            JRadioButtonMenuItem diffItem = new JRadioButtonMenuItem(difficulty.getDisplayName().getString());
            diffItem.setSelected(server.getWorldData().getDifficulty() == difficulty);
            diffItem.addActionListener(e -> setDifficulty(difficulty));
            difficultyGroup.add(diffItem);
            difficultyMenu.add(diffItem);
        }
        serverMenu.add(difficultyMenu);

        // Default Game Mode submenu
        JMenu gameModeMenu = new JMenu("Default Game Mode");
        ButtonGroup gameModeGroup = new ButtonGroup();
        for (GameType mode : GameType.values()) {
            JRadioButtonMenuItem modeItem = new JRadioButtonMenuItem(mode.getName());
            modeItem.setSelected(server.getWorldData().getGameType() == mode);
            modeItem.addActionListener(e -> setDefaultGameMode(mode));
            gameModeGroup.add(modeItem);
            gameModeMenu.add(modeItem);
        }
        serverMenu.add(gameModeMenu);

        serverMenu.addSeparator();

        JMenuItem saveAllItem = new JMenuItem("Save All Worlds");
        saveAllItem.setAccelerator(KeyStroke.getKeyStroke("ctrl S"));
        saveAllItem.addActionListener(e -> saveAllWorlds());
        serverMenu.add(saveAllItem);

        JMenuItem backupItem = new JMenuItem("Backup Server...");
        backupItem.addActionListener(e -> backupServer());
        serverMenu.add(backupItem);

        return serverMenu;
    }

    private void showServerProperties() {
        JDialog dialog = new JDialog(parentFrame, "Server Properties", true);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setSize(600, 500);
        dialog.setLocationRelativeTo(parentFrame);

        // Load server.properties with the same UTF-8 encoding Minecraft uses.
        File propertiesFile = new File("server.properties");
        Properties properties;

        try {
            properties = Utf8Properties.load(propertiesFile.toPath());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(dialog, "Failed to load server.properties: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Create table for properties
        String[] columnNames = {"Property", "Value"};
        Object[][] data = new Object[properties.size()][2];

        int i = 0;
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            data[i][0] = entry.getKey();
            data[i][1] = entry.getValue();
            i++;
        }

        JTable table = new JTable(data, columnNames);
        table.setRowHeight(25);
        JScrollPane scrollPane = new JScrollPane(table);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveButton = new JButton("Save");
        JButton cancelButton = new JButton("Cancel");

        saveButton.addActionListener(e -> {
            // Update properties from table
            for (int row = 0; row < table.getRowCount(); row++) {
                String key = (String) table.getValueAt(row, 0);
                Object value = table.getValueAt(row, 1);
                properties.setProperty(key, value.toString());
            }

            // Save to file
            try {
                Utf8Properties.store(propertiesFile.toPath(), properties,
                        "Minecraft server properties - Modified via GUI");
                JOptionPane.showMessageDialog(dialog, "Properties saved! Restart server for changes to take effect.",
                        "Success", JOptionPane.INFORMATION_MESSAGE);
                dialog.dispose();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(dialog, "Failed to save: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        cancelButton.addActionListener(e -> dialog.dispose());

        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);

        dialog.add(scrollPane, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void showWhitelistSettings() {
        JDialog dialog = new JDialog(parentFrame, "Whitelist Settings", true);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setSize(400, 400);
        dialog.setLocationRelativeTo(parentFrame);

        // Whitelist enabled checkbox
        JCheckBox enabledCheckbox = new JCheckBox("Whitelist Enabled", server.getPlayerList().isUsingWhitelist());
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(enabledCheckbox);

        // Whitelist entries
        DefaultListModel<String> listModel = new DefaultListModel<>();
        for (String player : server.getPlayerList().getWhiteListNames()) {
            listModel.addElement(player);
        }
        JList<String> whitelistList = new JList<>(listModel);
        JScrollPane scrollPane = new JScrollPane(whitelistList);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addButton = new JButton("Add Player");
        JButton removeButton = new JButton("Remove");
        JButton closeButton = new JButton("Close");

        addButton.addActionListener(e -> {
            String player = JOptionPane.showInputDialog(dialog, "Enter player name:", "Add to Whitelist", JOptionPane.QUESTION_MESSAGE);
            if (player != null && !player.trim().isEmpty()) {
                runCommand("whitelist add " + player);
                listModel.addElement(player);
            }
        });

        removeButton.addActionListener(e -> {
            String selected = whitelistList.getSelectedValue();
            if (selected != null) {
                runCommand("whitelist remove " + selected);
                listModel.removeElement(selected);
            }
        });

        enabledCheckbox.addActionListener(e -> runCommand(enabledCheckbox.isSelected() ? "whitelist on" : "whitelist off"));

        closeButton.addActionListener(e -> dialog.dispose());

        buttonPanel.add(addButton);
        buttonPanel.add(removeButton);
        buttonPanel.add(closeButton);

        dialog.add(topPanel, BorderLayout.NORTH);
        dialog.add(scrollPane, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void setDifficulty(Difficulty difficulty) {
        server.setDifficulty(difficulty, true);
        JOptionPane.showMessageDialog(parentFrame, "Difficulty set to " + difficulty.getDisplayName().getString(),
                "Success", JOptionPane.INFORMATION_MESSAGE);
    }

    private void setDefaultGameMode(GameType mode) {
        server.setDefaultGameType(mode);
        JOptionPane.showMessageDialog(parentFrame, "Default game mode set to " + mode.getName(),
                "Success", JOptionPane.INFORMATION_MESSAGE);
    }

    private void saveAllWorlds() {
        try {
            runCommand("save-all");
            JOptionPane.showMessageDialog(parentFrame, "All worlds saved successfully!",
                    "Success", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(parentFrame, "Failed to save: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void backupServer() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Choose Backup Location");
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        if (fileChooser.showSaveDialog(parentFrame) == JFileChooser.APPROVE_OPTION) {
            File backupDir = fileChooser.getSelectedFile();
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            File backupFile = new File(backupDir, "server-backup-" + timestamp + ".zip");

            // Show progress dialog
            JDialog progressDialog = new JDialog(parentFrame, "Creating Backup...", false);
            JProgressBar progressBar = new JProgressBar();
            progressBar.setIndeterminate(true);
            progressDialog.add(progressBar);
            progressDialog.setSize(300, 80);
            progressDialog.setLocationRelativeTo(parentFrame);
            progressDialog.setVisible(true);

            // Backup in separate thread
            new Thread(() -> {
                try {
                    // Save all first
                    runCommand("save-all flush");
                    Thread.sleep(2000); // Wait for save to complete

                    // Create zip
                    zipDirectory(new File("."), backupFile);

                    SwingUtilities.invokeLater(() -> {
                        progressDialog.dispose();
                        JOptionPane.showMessageDialog(parentFrame, "Backup created successfully!\n" + backupFile.getAbsolutePath(),
                                "Success", JOptionPane.INFORMATION_MESSAGE);
                    });
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        progressDialog.dispose();
                        JOptionPane.showMessageDialog(parentFrame, "Backup failed: " + e.getMessage(),
                                "Error", JOptionPane.ERROR_MESSAGE);
                    });
                }
            }).start();
        }
    }

    private void zipDirectory(File sourceDir, File zipFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            Files.walk(sourceDir.toPath())
                    .filter(path -> !Files.isDirectory(path))
                    .filter(path -> !path.toString().contains("backup")) // Don't backup backups
                    .filter(path -> !path.toString().contains("cache"))
                    .forEach(path -> {
                        try {
                            String zipEntryName = sourceDir.toPath().relativize(path).toString();
                            zos.putNextEntry(new ZipEntry(zipEntryName));
                            Files.copy(path, zos);
                            zos.closeEntry();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
        }
    }

    // ==================== WORLD MENU ====================
    private JMenu createWorldMenu() {
        JMenu worldMenu = new JMenu("World");
        worldMenu.setMnemonic('W');

        // World list submenu
        JMenu worldListMenu = new JMenu("World List");
        for (ServerLevel world : server.getAllLevels()) {
            JMenuItem worldItem = new JMenuItem(getWorldName(world));
            worldItem.addActionListener(e -> showWorldInfo(world));
            worldListMenu.add(worldItem);
        }
        worldMenu.add(worldListMenu);

        worldMenu.addSeparator();

        JMenuItem borderItem = new JMenuItem("World Border Settings...");
        borderItem.addActionListener(e -> showWorldBorderSettings());
        worldMenu.add(borderItem);

        // Time submenu
        JMenu timeMenu = new JMenu("Set Time");
        addTimeOption(timeMenu, "Day", 1000);
        addTimeOption(timeMenu, "Noon", 6000);
        addTimeOption(timeMenu, "Night", 13000);
        addTimeOption(timeMenu, "Midnight", 18000);
        timeMenu.addSeparator();
        JMenuItem customTimeItem = new JMenuItem("Custom...");
        customTimeItem.addActionListener(e -> setCustomTime());
        timeMenu.add(customTimeItem);
        worldMenu.add(timeMenu);

        // Weather submenu
        JMenu weatherMenu = new JMenu("Set Weather");
        addWeatherOption(weatherMenu, "Clear", "clear");
        addWeatherOption(weatherMenu, "Rain", "rain");
        addWeatherOption(weatherMenu, "Thunder", "thunder");
        weatherMenu.addSeparator();
        worldMenu.add(weatherMenu);

        JMenuItem gameruleItem = new JMenuItem("Game Rules...");
        gameruleItem.addActionListener(e -> showGameruleSettings());
        worldMenu.add(gameruleItem);

        worldMenu.addSeparator();

        JMenuItem reloadChunksItem = new JMenuItem("Reload Chunks");
        reloadChunksItem.addActionListener(e -> reloadChunks());
        worldMenu.add(reloadChunksItem);

        JMenuItem forceSaveItem = new JMenuItem("Force Save");
        forceSaveItem.addActionListener(e -> forceSave());
        worldMenu.add(forceSaveItem);

        JMenuItem worldBackupItem = new JMenuItem("World Backup...");
        worldBackupItem.addActionListener(e -> backupWorld());
        worldMenu.add(worldBackupItem);

        worldMenu.addSeparator();

        JMenuItem worldInfoItem = new JMenuItem("World Info...");
        worldInfoItem.addActionListener(e -> showGeneralWorldInfo());
        worldMenu.add(worldInfoItem);

        return worldMenu;
    }

    private void showGameruleSettings() {
        // Dynamically get all loaded worlds
        java.util.List<ServerLevel> worlds = new java.util.ArrayList<>();
        for (ServerLevel world : server.getAllLevels()) {
            worlds.add(world);
        }

        if (worlds.isEmpty()) {
            JOptionPane.showMessageDialog(parentFrame, "No worlds loaded!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Create world selection options
        String[] worldOptions = new String[worlds.size()];
        for (int i = 0; i < worlds.size(); i++) {
            worldOptions[i] = getWorldName(worlds.get(i));
        }

        String selectedWorldName = (String) JOptionPane.showInputDialog(
                parentFrame,
                "Select world to configure game rules:",
                "Game Rules",
                JOptionPane.QUESTION_MESSAGE,
                null,
                worldOptions,
                worldOptions[0]
        );

        if (selectedWorldName == null) return;

        // Find the selected world
        ServerLevel selectedWorld = null;
        for (ServerLevel world : worlds) {
            if (getWorldName(world).equals(selectedWorldName)) {
                selectedWorld = world;
                break;
            }
        }

        if (selectedWorld == null) return;

        showGameruleDialog(selectedWorld, selectedWorldName);
    }

    private void showGameruleDialog(ServerLevel world, String worldName) {
        JDialog dialog = new JDialog(parentFrame, "Game Rules - " + worldName, true);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setSize(700, 600);
        dialog.setLocationRelativeTo(parentFrame);

        // Create tabbed pane for categories
        JTabbedPane tabbedPane = new JTabbedPane();

        // Maps to store components by category, created on demand so that
        // no category (including modded ones) is ever silently dropped
        Map<GameRuleCategory, JPanel> categoryPanels = new HashMap<>();
        Map<GameRuleCategory, GridBagConstraints> categoryConstraints = new HashMap<>();
        Set<GameRuleCategory> usedCategories = new LinkedHashSet<>();

        // Store all gamerule components for later retrieval
        Map<String, JComponent> gameruleComponents = new HashMap<>();

        // Dynamically discover and add all gamerules
        world.getGameRules().visitGameRuleTypes(new GameRuleTypeVisitor() {
            @Override
            public void visitBoolean(GameRule<Boolean> rule) {
                GameRuleCategory category = rule.category();
                JPanel panel = getOrCreateCategoryPanel(categoryPanels, categoryConstraints, usedCategories, category);
                GridBagConstraints gbc = categoryConstraints.get(category);

                // Get current value from the world
                boolean currentValue = world.getGameRules().get(rule);

                // Create checkbox
                JCheckBox checkbox = new JCheckBox(formatGameruleName(rule.id()), currentValue);
                checkbox.setName(rule.id());
                checkbox.setToolTipText("Game rule: " + rule.id());

                panel.add(checkbox, gbc);
                gbc.gridy++;

                gameruleComponents.put(rule.id(), checkbox);
            }

            @Override
            public void visitInteger(GameRule<Integer> rule) {
                GameRuleCategory category = rule.category();
                JPanel panel = getOrCreateCategoryPanel(categoryPanels, categoryConstraints, usedCategories, category);
                GridBagConstraints gbc = categoryConstraints.get(category);

                // Get current value from the world
                int currentValue = world.getGameRules().get(rule);

                // Create row panel
                JPanel rowPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
                JLabel nameLabel = new JLabel(formatGameruleName(rule.id()) + ":");
                nameLabel.setPreferredSize(new Dimension(300, 25));

                JSpinner spinner = new JSpinner(new SpinnerNumberModel(currentValue, Integer.MIN_VALUE, Integer.MAX_VALUE, 1));
                spinner.setName(rule.id());
                spinner.setPreferredSize(new Dimension(100, 25));
                spinner.setToolTipText("Game rule: " + rule.id());

                rowPanel.add(nameLabel);
                rowPanel.add(spinner);

                panel.add(rowPanel, gbc);
                gbc.gridy++;

                gameruleComponents.put(rule.id(), spinner);
            }
        });

        // Add all category panels to tabbed pane (only categories that actually contain rules)
        for (GameRuleCategory category : usedCategories) {
            JPanel panel = categoryPanels.get(category);
            if (panel != null && panel.getComponentCount() > 0) {
                JScrollPane scrollPane = new JScrollPane(panel);
                scrollPane.getVerticalScrollBar().setUnitIncrement(16);
                tabbedPane.addTab(getCategoryDisplayName(category), scrollPane);
            }
        }

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton applyButton = new JButton("Apply");
        JButton resetButton = new JButton("Reset to Defaults");
        JButton closeButton = new JButton("Close");

        applyButton.addActionListener(e -> {
            applyGamerules(gameruleComponents);
            JOptionPane.showMessageDialog(dialog, "Game rules applied!", "Success", JOptionPane.INFORMATION_MESSAGE);
        });

        resetButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(dialog,
                    "Reset all game rules to default values?",
                    "Confirm Reset",
                    JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                resetGamerulesToDefaults(world);
                JOptionPane.showMessageDialog(dialog, "Game rules reset to defaults!", "Success", JOptionPane.INFORMATION_MESSAGE);
                dialog.dispose();
                // Reopen dialog to show updated values
                SwingUtilities.invokeLater(() -> showGameruleDialog(world, worldName));
            }
        });

        closeButton.addActionListener(e -> dialog.dispose());

        buttonPanel.add(applyButton);
        buttonPanel.add(resetButton);
        buttonPanel.add(closeButton);

        dialog.add(tabbedPane, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private JPanel getOrCreateCategoryPanel(Map<GameRuleCategory, JPanel> categoryPanels,
                                            Map<GameRuleCategory, GridBagConstraints> categoryConstraints,
                                            Set<GameRuleCategory> usedCategories,
                                            GameRuleCategory category) {
        JPanel existing = categoryPanels.get(category);
        if (existing != null) {
            return existing;
        }

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 10, 5, 10);
        gbc.weightx = 1.0;

        categoryPanels.put(category, panel);
        categoryConstraints.put(category, gbc);
        usedCategories.add(category);
        return panel;
    }

    private String formatGameruleName(String name) {
        // Convert camelCase to Title Case with spaces
        StringBuilder result = new StringBuilder();
        boolean wasLower = false;

        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);

            if (Character.isUpperCase(c) && wasLower) {
                result.append(' ');
            }

            if (i == 0) {
                result.append(Character.toUpperCase(c));
            } else {
                result.append(c);
            }

            wasLower = Character.isLowerCase(c);
        }

        return result.toString();
    }

    private String getCategoryDisplayName(GameRuleCategory category) {
        return category.label().getString();
    }

    private void applyGamerules(Map<String, JComponent> components) {
        for (Map.Entry<String, JComponent> entry : components.entrySet()) {
            String ruleName = entry.getKey();
            JComponent component = entry.getValue();

            try {
                if (component instanceof JCheckBox checkbox) {
                    executeGamerule(ruleName, String.valueOf(checkbox.isSelected()));
                } else if (component instanceof JSpinner spinner) {
                    executeGamerule(ruleName, spinner.getValue().toString());
                }
            } catch (Exception e) {
                System.err.println("Failed to apply gamerule " + ruleName + ": " + e.getMessage());
            }
        }
    }

    private void resetGamerulesToDefaults(ServerLevel world) {
        // Reset common gamerules to their defaults
        world.getGameRules().visitGameRuleTypes(new GameRuleTypeVisitor() {
            @Override
            public void visitBoolean(GameRule<Boolean> rule) {
                executeGamerule(rule.id(), String.valueOf(rule.defaultValue()));
            }

            @Override
            public void visitInteger(GameRule<Integer> rule) {
                executeGamerule(rule.id(), String.valueOf(rule.defaultValue()));
            }
        });
    }

    private void executeGamerule(String rule, String value) {
        runCommand("gamerule " + rule + " " + value);
    }

    private String getWorldName(ServerLevel world) {
        String dimensionKey = world.dimension().identifier().toString();
        if (dimensionKey.contains("overworld")) return "The Overworld";
        if (dimensionKey.contains("the_nether")) return "The Nether";
        if (dimensionKey.contains("the_end")) return "The End";
        return dimensionKey;
    }

    private void showWorldInfo(ServerLevel world) {
        String info = String.format(
                """
                        World: %s

                        Dimension: %s
                        Loaded Chunks: %d
                        Entities: %d
                        Time: %d
                        Weather: %s
                        Difficulty: %s""",
                getWorldName(world),
                world.dimension().identifier().toString(),
                world.getChunkSource().getLoadedChunksCount(),
                world.getAllEntities().spliterator().estimateSize(),
                world.getLevelData().getGameTime(),
                world.isRaining() ? (world.isThundering() ? "Thunder" : "Rain") : "Clear",
                world.getLevelData().getDifficulty().getDisplayName().getString()
        );

        JOptionPane.showMessageDialog(parentFrame, info, "World Information", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showWorldBorderSettings() {
        ServerLevel overworld = server.overworld();
        if (overworld == null) return;

        JDialog dialog = new JDialog(parentFrame, "World Border Settings", true);
        dialog.setLayout(new GridLayout(5, 2, 10, 10));
        dialog.setSize(400, 250);
        dialog.setLocationRelativeTo(parentFrame);

        dialog.add(new JLabel("Center X:"));
        JTextField centerXField = new JTextField(String.valueOf(overworld.getWorldBorder().getCenterX()));
        dialog.add(centerXField);

        dialog.add(new JLabel("Center Z:"));
        JTextField centerZField = new JTextField(String.valueOf(overworld.getWorldBorder().getCenterZ()));
        dialog.add(centerZField);

        dialog.add(new JLabel("Size:"));
        JTextField sizeField = new JTextField(String.valueOf(overworld.getWorldBorder().getSize()));
        dialog.add(sizeField);

        dialog.add(new JLabel("Damage Per Block:"));
        JTextField damageField = new JTextField(String.valueOf(overworld.getWorldBorder().getDamagePerBlock()));
        dialog.add(damageField);

        JButton applyButton = new JButton("Apply");
        JButton cancelButton = new JButton("Cancel");

        applyButton.addActionListener(e -> {
            try {
                double centerX = Double.parseDouble(centerXField.getText());
                double centerZ = Double.parseDouble(centerZField.getText());
                double size = Double.parseDouble(sizeField.getText());

                runCommand(String.format("worldborder center %f %f", centerX, centerZ));
                runCommand(String.format("worldborder set %f", size));

                JOptionPane.showMessageDialog(dialog, "World border updated!", "Success", JOptionPane.INFORMATION_MESSAGE);
                dialog.dispose();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dialog, "Invalid number format!", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        cancelButton.addActionListener(e -> dialog.dispose());

        dialog.add(applyButton);
        dialog.add(cancelButton);

        dialog.setVisible(true);
    }

    private void addTimeOption(JMenu menu, String name, long time) {
        JMenuItem item = new JMenuItem(name);
        item.addActionListener(e -> {
            runCommand("time set " + time);
        });
        menu.add(item);
    }

    private void setCustomTime() {
        String input = JOptionPane.showInputDialog(parentFrame, "Enter time (0-24000):", "Custom Time", JOptionPane.QUESTION_MESSAGE);
        if (input != null) {
            try {
                long time = Long.parseLong(input);
                runCommand("time set " + time);
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(parentFrame, "Invalid time value!", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void addWeatherOption(JMenu menu, String name, String weather) {
        JMenuItem item = new JMenuItem(name);
        item.addActionListener(e -> {
            runCommand("weather " + weather);
        });
        menu.add(item);
    }

    private void setCustomWeather() {
        String[] options = {"Clear", "Rain", "Thunder"};
        String weather = (String) JOptionPane.showInputDialog(parentFrame, "Select weather:", "Custom Weather",
                JOptionPane.QUESTION_MESSAGE, null, options, options[0]);

        if (weather != null) {
            String duration = JOptionPane.showInputDialog(parentFrame, "Enter duration (seconds):", "Duration", JOptionPane.QUESTION_MESSAGE);
            if (duration != null) {
                try {
                    int seconds = Integer.parseInt(duration);
                    runCommand("weather " + weather.toLowerCase() + " " + seconds);
                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(parentFrame, "Invalid duration!", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }

    private void reloadChunks() {
        int confirm = JOptionPane.showConfirmDialog(parentFrame,
                "Reload all chunks? This may cause lag.",
                "Confirm", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            runCommand("reload");
            JOptionPane.showMessageDialog(parentFrame, "Chunks reloaded!", "Success", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void forceSave() {
        runCommand("save-all flush");
        JOptionPane.showMessageDialog(parentFrame, "Force save completed!", "Success", JOptionPane.INFORMATION_MESSAGE);
    }

    private void backupWorld() {
        String[] worldNames = {"Overworld", "Nether", "End", "All Worlds"};
        String selected = (String) JOptionPane.showInputDialog(parentFrame, "Select world to backup:", "World Backup",
                JOptionPane.QUESTION_MESSAGE, null, worldNames, worldNames[0]);

        if (selected != null) {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Choose Backup Location");
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

            if (fileChooser.showSaveDialog(parentFrame) == JFileChooser.APPROVE_OPTION) {
                File backupDir = fileChooser.getSelectedFile();
                String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
                File backupFile = new File(backupDir, "world-backup-" + selected.toLowerCase().replace(" ", "-") + "-" + timestamp + ".zip");

                JOptionPane.showMessageDialog(parentFrame, "Backup started in background...", "Info", JOptionPane.INFORMATION_MESSAGE);

                new Thread(() -> {
                    try {
                        runCommand("save-all flush");
                        Thread.sleep(2000);

                        // Backup specific world folder
                        File worldFolder = selected.equals("All Worlds") ? new File("world") :
                                new File("world/" + selected.toLowerCase().replace(" ", "_"));

                        if (worldFolder.exists()) {
                            zipDirectory(worldFolder, backupFile);
                            SwingUtilities.invokeLater(() -> {
                                JOptionPane.showMessageDialog(parentFrame, "World backup created!\n" + backupFile.getAbsolutePath(),
                                        "Success", JOptionPane.INFORMATION_MESSAGE);
                            });
                        }
                    } catch (Exception e) {
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(parentFrame, "Backup failed: " + e.getMessage(),
                                    "Error", JOptionPane.ERROR_MESSAGE);
                        });
                    }
                }).start();
            }
        }
    }

    private void showGeneralWorldInfo() {
        ServerLevel overworld = server.overworld();
        if (overworld == null) return;

        LevelData properties = overworld.getLevelData();
        LevelData.RespawnData spawn = properties.getRespawnData();

        String info = String.format(
                "World Information\n\n" +
                        "Seed: %d\n" +
                        "Spawn: X=%d, Y=%d, Z=%d\n" +
                        "Difficulty: %s\n" +
                        "Hardcore: %s\n" +
                        "Allow Commands: %s",
                overworld.getSeed(),
                spawn.pos().getX(), spawn.pos().getY(), spawn.pos().getZ(),
                properties.getDifficulty().getDisplayName().getString(),
                properties.isHardcore() ? "Yes" : "No",
                overworld.getGameRules().get(GameRules.COMMAND_BLOCKS_WORK) ? "Yes" : "No"
        );

        JTextArea textArea = new JTextArea(info);
        textArea.setEditable(false);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

        JButton copyButton = new JButton("Copy Seed");
        copyButton.addActionListener(e -> {
            StringSelection selection = new StringSelection(String.valueOf(overworld.getSeed()));
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
            JOptionPane.showMessageDialog(parentFrame, "Seed copied to clipboard!", "Success", JOptionPane.INFORMATION_MESSAGE);
        });

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JScrollPane(textArea), BorderLayout.CENTER);
        panel.add(copyButton, BorderLayout.SOUTH);

        JOptionPane.showMessageDialog(parentFrame, panel, "World Information", JOptionPane.INFORMATION_MESSAGE);
    }

    // ==================== NETWORK MENU ====================
    private JMenu createNetworkMenu() {
        JMenu networkMenu = new JMenu("Network");
        networkMenu.setMnemonic('N');

        JMenuItem refreshItem = new JMenuItem("Refresh Network Information");
        refreshItem.addActionListener(event -> showNetworkInformation());
        networkMenu.add(refreshItem);

        networkMenu.addSeparator();
        JMenuItem infoItem = new JMenuItem("Show Network Information...");
        infoItem.addActionListener(event -> showNetworkInformation());
        networkMenu.add(infoItem);
        return networkMenu;
    }

    private void showNetworkInformation() {
        int javaPort = server.getServerPort();
        JDialog dialog = new JDialog(parentFrame, "Network", true);
        dialog.setLayout(new BorderLayout(8, 8));
        dialog.setSize(520, 260);
        dialog.setLocationRelativeTo(parentFrame);
        JTextArea textArea = new JTextArea("Reading network configuration...");
        textArea.setEditable(false);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        dialog.add(new JScrollPane(textArea), BorderLayout.CENTER);
        ThemeManager.getInstance().applyTo(dialog);

        // Start the reader before showing the modal dialog; setVisible(true) blocks
        // this EDT callback until the user closes the dialog.
        new Thread(() -> {
            Integer bedrockPort = readGeyserBedrockPort();
            String bedrock = bedrockPort == null
                    ? "Not detected (Geyser may be unavailable or configured elsewhere)"
                    : Integer.toString(bedrockPort);
            String info = "Network Information\n\n"
                    + "Java Edition port: " + javaPort + "\n"
                    + "Bedrock Edition port: " + bedrock + "\n\n"
                    + "Java address: 0.0.0.0:" + javaPort + "\n"
                    + "Bedrock address: 0.0.0.0:" + bedrock;
            SwingUtilities.invokeLater(() -> textArea.setText(info));
        }, "DedicatedPower network configuration reader").start();
        dialog.setVisible(true);
    }

    private Integer readGeyserBedrockPort() {
        Path[] candidates = {
                Path.of("config", "Geyser-Fabric", "config.yml"),
                Path.of("config", "Geyser-Fabric", "config.yaml"),
                Path.of("config", "geyser-fabric", "config.yml")
        };
        for (Path candidate : candidates) {
            if (!Files.isRegularFile(candidate)) {
                continue;
            }
            try {
                List<String> lines = Files.readAllLines(candidate);
                boolean inBedrock = false;
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("bedrock:")) {
                        inBedrock = true;
                        continue;
                    }
                    if (inBedrock && !line.isBlank() && !Character.isWhitespace(line.charAt(0))) {
                        inBedrock = false;
                    }
                    if (inBedrock && (trimmed.startsWith("port:") || trimmed.startsWith("port :"))) {
                        String value = trimmed.substring(trimmed.indexOf(':') + 1).trim();
                        return Integer.parseInt(value);
                    }
                }
            } catch (IOException | NumberFormatException ignored) {
                // Continue with the next known Geyser config location.
            }
        }
        return null;
    }

    // ==================== PERFORMANCE MENU ====================
    private JMenu createPerformanceMenu() {
        JMenu perfMenu = new JMenu("Performance");
        perfMenu.setMnemonic('P');

        // Clear Entities submenu
        JMenu clearEntitiesMenu = new JMenu("Clear Entities");
        addClearEntityOption(clearEntitiesMenu, "Items Only", "minecraft:item");
        addClearEntityOption(clearEntitiesMenu, "Hostile Mobs", "hostile");
        addClearEntityOption(clearEntitiesMenu, "All Mobs", "mobs");
        addClearEntityOption(clearEntitiesMenu, "All Non-Player", "all");
        perfMenu.add(clearEntitiesMenu);

        JMenuItem optimizeItem = new JMenuItem("Optimize Chunks");
        optimizeItem.addActionListener(e -> optimizeChunks());
        perfMenu.add(optimizeItem);

        perfMenu.addSeparator();

        JMenuItem gcItem = new JMenuItem("Force Garbage Collection");
        gcItem.addActionListener(e -> forceGarbageCollection());
        perfMenu.add(gcItem);

        JMenuItem threadDumpItem = new JMenuItem("Thread Dump...");
        threadDumpItem.addActionListener(e -> generateThreadDump());
        perfMenu.add(threadDumpItem);

        JMenuItem perfReportItem = new JMenuItem("Generate Performance Report...");
        perfReportItem.addActionListener(e -> generatePerformanceReport());
        perfMenu.add(perfReportItem);

        return perfMenu;
    }

    private void addClearEntityOption(JMenu menu, String name, String type) {
        JMenuItem item = new JMenuItem(name);
        item.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(parentFrame,
                    "Clear " + name.toLowerCase() + "?",
                    "Confirm", JOptionPane.YES_NO_OPTION);

            if (confirm == JOptionPane.YES_OPTION) {
                String command = switch (type) {
                    case "minecraft:item" -> "kill @e[type=item]";
                    case "hostile" -> "kill @e[type=!player,type=!item,type=!armor_stand]";
                    case "mobs" -> "kill @e[type=!player,type=!item]";
                    case "all" -> "kill @e[type=!player]";
                    default -> "kill @e[type=" + type + "]";
                };

                runCommand(command);
                JOptionPane.showMessageDialog(parentFrame, "Entities cleared!", "Success", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        menu.add(item);
    }

    private void optimizeChunks() {
        int confirm = JOptionPane.showConfirmDialog(parentFrame,
                "Optimize chunks? This may take a while and cause lag.",
                "Confirm", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            JOptionPane.showMessageDialog(parentFrame, "Optimization started in background...", "Info", JOptionPane.INFORMATION_MESSAGE);

            new Thread(() -> {
                try {
                    // Force save and reload
                    runCommand("save-all flush");
                    Thread.sleep(2000);
                    System.gc();

                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(parentFrame, "Chunk optimization completed!",
                                "Success", JOptionPane.INFORMATION_MESSAGE);
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }

    private void forceGarbageCollection() {
        Runtime runtime = Runtime.getRuntime();
        long beforeMem = runtime.totalMemory() - runtime.freeMemory();

        System.gc();

        long afterMem = runtime.totalMemory() - runtime.freeMemory();
        long freed = beforeMem - afterMem;

        JOptionPane.showMessageDialog(parentFrame,
                String.format("Garbage collection completed!\n\nMemory freed: %.2f MB", freed / 1024.0 / 1024.0),
                "Success", JOptionPane.INFORMATION_MESSAGE);
    }

    private void generateThreadDump() {
        StringBuilder dump = new StringBuilder();
        dump.append("=== THREAD DUMP ===\n");
        dump.append("Generated: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).append("\n\n");

        Map<Thread, StackTraceElement[]> allThreads = Thread.getAllStackTraces();
        for (Map.Entry<Thread, StackTraceElement[]> entry : allThreads.entrySet()) {
            Thread thread = entry.getKey();
            dump.append("Thread: ").append(thread.getName())
                    .append(" (").append(thread.getState()).append(")\n");

            for (StackTraceElement element : entry.getValue()) {
                dump.append("  at ").append(element.toString()).append("\n");
            }
            dump.append("\n"); // Add spacing between threads
        }

        // NOW create the dialog ONCE with all the collected data
        JTextArea textArea = new JTextArea(dump.toString());
        textArea.setEditable(false);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 10));

        JDialog dialog = new JDialog(parentFrame, "Thread Dump", false);
        dialog.setLayout(new BorderLayout());
        dialog.add(new JScrollPane(textArea), BorderLayout.CENTER);

        JButton saveButton = new JButton("Save to File");
        saveButton.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new File("threaddump-" + System.currentTimeMillis() + ".txt"));
            if (fc.showSaveDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                try (FileWriter writer = new FileWriter(fc.getSelectedFile())) {
                    writer.write(dump.toString());
                    JOptionPane.showMessageDialog(dialog, "Thread dump saved!", "Success", JOptionPane.INFORMATION_MESSAGE);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(dialog, "Failed to save: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(saveButton);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setSize(700, 500);
        dialog.setLocationRelativeTo(parentFrame);
        dialog.setVisible(true);
    }

    private void generatePerformanceReport() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();

        int totalEntities = 0;
        int totalChunks = 0;
        for (ServerLevel world : server.getAllLevels()) {
            for (Object ignored : world.getAllEntities()) {
                totalEntities++;
            }
            totalChunks += world.getChunkSource().getLoadedChunksCount();
        }

        double avgTickMs = server.getAverageTickTimeNanos() / 1_000_000.0;

        StringBuilder report = new StringBuilder();
        report.append("=== PERFORMANCE REPORT ===\n");
        report.append("Generated: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).append("\n\n");
        report.append("=== MEMORY ===\n");
        report.append(String.format("Used: %d MB\n", usedMemory / 1024 / 1024));
        report.append(String.format("Max: %d MB\n", maxMemory / 1024 / 1024));
        report.append(String.format("Usage: %.1f%%\n\n", (usedMemory * 100.0) / maxMemory));
        report.append("=== SERVER ===\n");
        report.append(String.format("TPS: %.2f\n", Math.min(20.0, 1000.0 / avgTickMs)));
        report.append(String.format("Average Tick: %.2f ms\n", avgTickMs));
        report.append(String.format("Players: %d\n", server.getPlayerCount()));
        report.append(String.format("Entities: %d\n", totalEntities));
        report.append(String.format("Chunks: %d\n\n", totalChunks));
        report.append("=== SYSTEM ===\n");
        report.append(String.format("CPU Cores: %d\n", runtime.availableProcessors()));
        report.append(String.format("Java Version: %s\n", System.getProperty("java.version")));
        report.append(String.format("OS: %s %s\n", System.getProperty("os.name"), System.getProperty("os.version")));

        JTextArea textArea = new JTextArea(report.toString());
        textArea.setEditable(false);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

        JDialog dialog = new JDialog(parentFrame, "Performance Report", false);
        dialog.setLayout(new BorderLayout());
        dialog.add(new JScrollPane(textArea), BorderLayout.CENTER);

        JButton saveButton = new JButton("Save Report");
        saveButton.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new File("performance-report-" + System.currentTimeMillis() + ".txt"));
            if (fc.showSaveDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                try (FileWriter writer = new FileWriter(fc.getSelectedFile())) {
                    writer.write(report.toString());
                    JOptionPane.showMessageDialog(dialog, "Report saved!", "Success", JOptionPane.INFORMATION_MESSAGE);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(dialog, "Failed to save: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(saveButton);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setSize(500, 400);
        dialog.setLocationRelativeTo(parentFrame);
        dialog.setVisible(true);
    }

    // ==================== TOOLS MENU ====================
    private JMenu createToolsMenu() {
        JMenu toolsMenu = new JMenu("Tools");
        toolsMenu.setMnemonic('T');

        JMenuItem appearanceItem = new JMenuItem("Appearance...");
        appearanceItem.addActionListener(e -> showAppearanceSettings());
        toolsMenu.add(appearanceItem);

        JMenuItem motdItem = new JMenuItem("MOTD Editor...");
        motdItem.addActionListener(e -> showMotdEditor());
        toolsMenu.add(motdItem);

        JMenuItem exportModsItem = new JMenuItem("Export Mod List...");
        exportModsItem.addActionListener(e -> exportModList());
        toolsMenu.add(exportModsItem);

        toolsMenu.addSeparator();

        JMenuItem commandPaletteItem = new JMenuItem("Command Palette...");
        commandPaletteItem.setAccelerator(KeyStroke.getKeyStroke("ctrl shift P"));
        commandPaletteItem.addActionListener(e -> showCommandPalette());
        toolsMenu.add(commandPaletteItem);

        JMenuItem scheduledTasksItem = new JMenuItem("Scheduled Tasks...");
        scheduledTasksItem.addActionListener(e -> showScheduledTasks());
        toolsMenu.add(scheduledTasksItem);

        JMenuItem rconItem = new JMenuItem("RCON Connection...");
        rconItem.addActionListener(e -> showRconConnection());
        toolsMenu.add(rconItem);

        toolsMenu.addSeparator();

        JMenuItem datapackItem = new JMenuItem("Datapack Manager...");
        datapackItem.addActionListener(e -> showDatapackManager());
        toolsMenu.add(datapackItem);

        JMenuItem resourcePackItem = new JMenuItem("Resource Pack Settings...");
        resourcePackItem.addActionListener(e -> showResourcePackSettings());
        toolsMenu.add(resourcePackItem);

        toolsMenu.addSeparator();

        JMenuItem serverIconItem = new JMenuItem("Server Icon...");
        serverIconItem.addActionListener(e -> changeServerIcon());
        toolsMenu.add(serverIconItem);

        return toolsMenu;
    }

    private void showAppearanceSettings() {
        ThemeManager themeManager = ThemeManager.getInstance();
        JDialog dialog = new JDialog(parentFrame, "Appearance", true);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setSize(520, 460);
        dialog.setLocationRelativeTo(parentFrame);

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel themePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        themePanel.setBorder(BorderFactory.createTitledBorder("Theme"));
        JRadioButton lightButton = new JRadioButton("Light", !themeManager.isDark());
        JRadioButton darkButton = new JRadioButton("Dark", themeManager.isDark());
        ButtonGroup themeGroup = new ButtonGroup();
        themeGroup.add(lightButton);
        themeGroup.add(darkButton);
        themePanel.add(lightButton);
        themePanel.add(darkButton);
        content.add(themePanel, BorderLayout.NORTH);

        JPanel palettePanel = new JPanel(new GridLayout(0, 2, 8, 8));
        palettePanel.setBorder(BorderFactory.createTitledBorder("Log colors"));
        Map<EnhancedLogPanel.LogLevel, JButton> colorButtons = new EnumMap<>(EnhancedLogPanel.LogLevel.class);
        for (EnhancedLogPanel.LogLevel level : EnhancedLogPanel.LogLevel.values()) {
            JLabel label = new JLabel(level.name());
            JButton colorButton = new JButton("Choose color");
            colorButton.putClientProperty("dedicatedpower.palettePreview", Boolean.TRUE);
            colorButton.setOpaque(true);
            colorButton.setBorderPainted(true);
            colorButton.setBackground(themeManager.getLogColor(level));
            colorButton.putClientProperty("dedicatedpower.paletteColor", themeManager.getLogColor(level));
            colorButton.setToolTipText("Current color: " + toHex(themeManager.getLogColor(level)));
            colorButton.addActionListener(event -> {
                Color selected = JColorChooser.showDialog(dialog, level + " log color",
                        colorButton.getBackground());
                if (selected != null) {
                    colorButton.setBackground(selected);
                    colorButton.putClientProperty("dedicatedpower.paletteColor", selected);
                    colorButton.setToolTipText("Current color: " + toHex(selected));
                }
            });
            colorButtons.put(level, colorButton);
            palettePanel.add(label);
            palettePanel.add(colorButton);
        }
        content.add(palettePanel, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton resetButton = new JButton("Reset palette");
        resetButton.addActionListener(event -> {
            Map<EnhancedLogPanel.LogLevel, Color> defaultsMap = ThemeManager.defaultLogColors();
            for (Map.Entry<EnhancedLogPanel.LogLevel, JButton> entry : colorButtons.entrySet()) {
                Color color = defaultsMap.get(entry.getKey());
                entry.getValue().setBackground(color);
                entry.getValue().putClientProperty("dedicatedpower.paletteColor", color);
                entry.getValue().setToolTipText("Current color: " + toHex(color));
            }
        });
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(event -> dialog.dispose());
        JButton applyButton = new JButton("Apply");
        applyButton.addActionListener(event -> {
            Map<EnhancedLogPanel.LogLevel, Color> colors = new EnumMap<>(EnhancedLogPanel.LogLevel.class);
            for (Map.Entry<EnhancedLogPanel.LogLevel, JButton> entry : colorButtons.entrySet()) {
                colors.put(entry.getKey(), entry.getValue().getBackground());
            }
            themeManager.applySettings(darkButton.isSelected() ? ThemeManager.Theme.DARK : ThemeManager.Theme.LIGHT, colors);
            themeManager.applyTo(parentFrame);
            dialog.dispose();
        });
        buttons.add(resetButton);
        buttons.add(cancelButton);
        buttons.add(applyButton);

        dialog.add(content, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        themeManager.applyTo(dialog);
        dialog.setVisible(true);
    }

    private void showMotdEditor() {
        ThemeManager themeManager = ThemeManager.getInstance();
        JDialog dialog = new JDialog(parentFrame, "MOTD Editor", true);
        dialog.setLayout(new BorderLayout(12, 12));
        dialog.setSize(960, 650);
        dialog.setMinimumSize(new Dimension(780, 540));
        dialog.setLocationRelativeTo(parentFrame);

        MotdHistoryStore.State history = MotdHistoryStore.load();
        Color input = themeManager.getInputBackground();
        Color muted = themeManager.getMutedForeground();

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(BorderFactory.createEmptyBorder(14, 14, 12, 14));

        JPanel heading = new JPanel(new BorderLayout(8, 2));
        JLabel title = new JLabel("MOTD Editor");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 19f));
        JLabel subtitle = new JLabel("Create the message players see in the server list (up to 2 lines)");
        subtitle.setForeground(muted);
        JPanel headingText = new JPanel();
        headingText.setLayout(new BoxLayout(headingText, BoxLayout.Y_AXIS));
        headingText.add(title);
        headingText.add(subtitle);
        heading.add(headingText, BorderLayout.WEST);
        JLabel support = new JLabel("Minecraft legacy formatting supported");
        support.setForeground(muted);
        heading.add(support, BorderLayout.EAST);
        root.add(heading, BorderLayout.NORTH);

        JTextArea editor = new JTextArea(normalizeMotdInput(server.getMotd()), 5, 42);
        editor.setLineWrap(true);
        editor.setWrapStyleWord(true);
        editor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        editor.setMargin(new Insets(10, 10, 10, 10));
        editor.setTabSize(2);
        // Minecraft shows at most 2 MOTD lines regardless of line length, so the
        // document filter trims anything beyond that for typing, pasting, loading
        // history entries, and resetting.
        if (editor.getDocument() instanceof AbstractDocument abstractDocument) {
            abstractDocument.setDocumentFilter(new DocumentFilter() {
                @Override
                public void insertString(FilterBypass bypass, int offset, String text, AttributeSet attrs)
                        throws BadLocationException {
                    applyMotdEdit(bypass, offset, 0, text, attrs);
                }

                @Override
                public void replace(FilterBypass bypass, int offset, int length, String text, AttributeSet attrs)
                        throws BadLocationException {
                    applyMotdEdit(bypass, offset, length, text, attrs);
                }
            });
        }
        // Normalize a MOTD that already exceeded the limit (e.g. from server.properties).
        editor.setText(limitMotdLines(editor.getText(), MAX_MOTD_LINES));
        JScrollPane editorScroll = new JScrollPane(editor);
        editorScroll.setBorder(BorderFactory.createLineBorder(themeManager.getBorderColor()));

        // Formatting controls: a wrapping strip so every button is always fully
        // visible with no horizontal scrollbar. WrapLayout reflows the buttons
        // onto additional rows when the editor column is narrow.
        JPanel toolbar = new JPanel(new WrapLayout(FlowLayout.LEFT, 6, 4));
        toolbar.setBorder(BorderFactory.createEmptyBorder(3, 4, 5, 4));
        addMotdCodeButton(toolbar, "Black", "0", editor);
        addMotdCodeButton(toolbar, "Dark Blue", "1", editor);
        addMotdCodeButton(toolbar, "Dark Green", "2", editor);
        addMotdCodeButton(toolbar, "Dark Aqua", "3", editor);
        addMotdCodeButton(toolbar, "Dark Red", "4", editor);
        addMotdCodeButton(toolbar, "Dark Purple", "5", editor);
        addMotdCodeButton(toolbar, "Gold", "6", editor);
        addMotdCodeButton(toolbar, "Gray", "7", editor);
        addMotdCodeButton(toolbar, "Dark Gray", "8", editor);
        addMotdCodeButton(toolbar, "Blue", "9", editor);
        addMotdCodeButton(toolbar, "Green", "a", editor);
        addMotdCodeButton(toolbar, "Aqua", "b", editor);
        addMotdCodeButton(toolbar, "Red", "c", editor);
        addMotdCodeButton(toolbar, "Light Purple", "d", editor);
        addMotdCodeButton(toolbar, "Yellow", "e", editor);
        addMotdCodeButton(toolbar, "White", "f", editor);
        toolbar.add(Box.createHorizontalStrut(10));
        addMotdCodeButton(toolbar, "Bold", "l", editor);
        addMotdCodeButton(toolbar, "Underline", "n", editor);
        addMotdCodeButton(toolbar, "Italic", "o", editor);
        addMotdCodeButton(toolbar, "Strikethrough", "m", editor);
        addMotdCodeButton(toolbar, "Obfuscated", "k", editor);
        addMotdCodeButton(toolbar, "Reset", "r", editor);

        JLabel counter = new JLabel();
        counter.setForeground(muted);
        JLabel status = new JLabel("Ready to apply");
        status.setForeground(muted);
        JPanel editorStatus = new JPanel(new BorderLayout());
        editorStatus.setBorder(BorderFactory.createEmptyBorder(6, 2, 0, 2));
        editorStatus.add(counter, BorderLayout.WEST);
        editorStatus.add(status, BorderLayout.EAST);

        JPanel editorPanel = new JPanel(new BorderLayout(4, 4));
        editorPanel.setBorder(BorderFactory.createTitledBorder("Message"));
        editorPanel.add(toolbar, BorderLayout.NORTH);
        editorPanel.add(editorScroll, BorderLayout.CENTER);
        editorPanel.add(editorStatus, BorderLayout.SOUTH);

        JTextPane preview = new JTextPane();
        preview.setContentType("text/html");
        preview.setEditable(false);
        preview.setFocusable(false);
        preview.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        preview.setBackground(input);

        JLabel previewTitle = new JLabel("DedicatedPower Server");
        previewTitle.setFont(previewTitle.getFont().deriveFont(Font.BOLD, 14f));
        JLabel previewState = new JLabel("Online  •  20 TPS");
        previewState.setForeground(new Color(76, 175, 80));
        JPanel previewHeader = new JPanel(new BorderLayout());
        previewHeader.setBorder(BorderFactory.createEmptyBorder(10, 12, 4, 12));
        previewHeader.add(previewTitle, BorderLayout.WEST);
        previewHeader.add(previewState, BorderLayout.EAST);
        JPanel previewCard = new JPanel(new BorderLayout());
        previewCard.setBorder(BorderFactory.createLineBorder(themeManager.getBorderColor()));
        previewCard.add(previewHeader, BorderLayout.NORTH);
        previewCard.add(preview, BorderLayout.CENTER);

        JList<String> historyList = new JList<>(new DefaultListModel<>());
        refreshMotdHistoryModel(historyList, history);
        historyList.setVisible(history.isEnabled());
        historyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        historyList.setCellRenderer((list, value, index, selected, focused) -> {
            // The HTML body background would otherwise cover the selection
            // highlight, so the selected row is painted with the selection color.
            Color cellBackground = selected ? themeManager.getSelectionBackground() : themeManager.getInputBackground();
            JLabel cell = new JLabel(motdToHtml(value, cellBackground));
            cell.setOpaque(true);
            cell.setBorder(BorderFactory.createEmptyBorder(7, 8, 7, 8));
            cell.setBackground(cellBackground);
            cell.setForeground(themeManager.getForeground());
            return cell;
        });
        historyList.setToolTipText("Select a saved MOTD to load it into the editor");

        JPanel historyPanel = new JPanel(new BorderLayout(6, 6));
        historyPanel.setBorder(BorderFactory.createTitledBorder("Saved MOTDs"));
        JScrollPane historyScroll = new JScrollPane(historyList);
        historyPanel.add(historyScroll, BorderLayout.CENTER);
        JPanel historyButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JCheckBox historyEnabled = new JCheckBox("Remember history", history.isEnabled());
        JButton deleteButton = new JButton("Delete");
        JButton clearButton = new JButton("Clear");
        historyEnabled.addActionListener(event -> {
            history.setEnabled(historyEnabled.isSelected());
            historyList.setVisible(history.isEnabled());
            historyPanel.revalidate();
            historyPanel.repaint();
        });
        deleteButton.addActionListener(event -> {
            String selected = historyList.getSelectedValue();
            if (selected != null) {
                history.getEntries().remove(selected);
                refreshMotdHistoryModel(historyList, history);
            }
        });
        clearButton.addActionListener(event -> {
            if (!history.getEntries().isEmpty()
                    && JOptionPane.showConfirmDialog(dialog, "Clear all saved MOTDs?", "Clear history",
                    JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                history.getEntries().clear();
                refreshMotdHistoryModel(historyList, history);
            }
        });
        historyButtons.add(historyEnabled);
        historyButtons.add(deleteButton);
        historyButtons.add(clearButton);
        historyPanel.add(historyButtons, BorderLayout.SOUTH);

        JPanel leftColumn = new JPanel(new BorderLayout(8, 8));
        leftColumn.add(editorPanel, BorderLayout.CENTER);
        leftColumn.add(previewCard, BorderLayout.SOUTH);

        JPanel rightColumn = new JPanel(new BorderLayout(8, 8));
        rightColumn.add(historyPanel, BorderLayout.CENTER);
        rightColumn.setPreferredSize(new Dimension(280, 0));

        JPanel workspace = new JPanel(new GridLayout(1, 2, 12, 0));
        workspace.add(leftColumn);
        workspace.add(rightColumn);
        root.add(workspace, BorderLayout.CENTER);

        Runnable updateStatus = () -> {
            String text = editor.getText();
            int length = text.length();
            int lines = text.isEmpty() ? 0 : 1 + countNewlines(text);
            counter.setText(lines + "/" + MAX_MOTD_LINES + " lines · " + length + " characters");
            counter.setToolTipText("Minecraft shows up to " + MAX_MOTD_LINES + " lines in the server list");
            status.setText(lines > MAX_MOTD_LINES ? "Too many lines" : "Ready to apply");
        };
        Runnable updatePreview = () -> preview.setText(motdToHtml(editor.getText()));
        editor.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void changed() {
                updatePreview.run();
                updateStatus.run();
            }
            public void insertUpdate(javax.swing.event.DocumentEvent event) { changed(); }
            public void removeUpdate(javax.swing.event.DocumentEvent event) { changed(); }
            public void changedUpdate(javax.swing.event.DocumentEvent event) { changed(); }
        });
        updatePreview.run();
        updateStatus.run();

        historyList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && historyList.getSelectedValue() != null) {
                editor.setText(normalizeMotdInput(historyList.getSelectedValue()));
                editor.requestFocusInWindow();
            }
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton resetButton = new JButton("Reset");
        resetButton.setToolTipText("Restore the MOTD currently used by the server");
        resetButton.addActionListener(event -> editor.setText(normalizeMotdInput(server.getMotd())));
        JButton copyButton = new JButton("Copy MOTD");
        copyButton.addActionListener(event -> {
            StringSelection selection = new StringSelection(normalizeMotdInput(editor.getText()));
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
            status.setText("Copied to clipboard");
        });
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(event -> dialog.dispose());
        JButton applyButton = new JButton("Save & Apply");
        applyButton.addActionListener(event -> {
            String motd = normalizeMotdInput(editor.getText()).trim();
            if (motd.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "MOTD cannot be empty.", "Invalid MOTD", JOptionPane.ERROR_MESSAGE);
                return;
            }
            server.execute(() -> server.setMotd(motd));
            if (history.isEnabled()) {
                history.add(motd);
            }
            new Thread(() -> {
                updateServerPropertiesMotd(motd);
                MotdHistoryStore.save(history);
            }, "DedicatedPower MOTD persistence").start();
            dialog.dispose();
        });
        buttons.add(resetButton);
        buttons.add(copyButton);
        buttons.add(cancelButton);
        buttons.add(applyButton);

        dialog.add(root, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        ThemeManager.getInstance().applyTo(dialog);

        // Explicit styling pass so every control follows the current theme
        // regardless of Look & Feel defaults (caret, selection, secondary text,
        // toolbar strip, scrollbars, and checkboxes included).
        editor.setBackground(themeManager.getInputBackground());
        editor.setForeground(themeManager.getForeground());
        editor.setCaretColor(themeManager.getForeground());
        editor.setSelectionColor(themeManager.getSelectionBackground());
        editor.setSelectedTextColor(themeManager.getForeground());
        editorScroll.getViewport().setBackground(themeManager.getInputBackground());

        preview.setBackground(themeManager.getInputBackground());
        historyList.setBackground(themeManager.getInputBackground());
        historyList.setForeground(themeManager.getForeground());
        historyScroll.getViewport().setBackground(themeManager.getInputBackground());

        subtitle.setForeground(themeManager.getMutedForeground());
        support.setForeground(themeManager.getMutedForeground());
        counter.setForeground(themeManager.getMutedForeground());
        status.setForeground(themeManager.getMutedForeground());

        toolbar.setBackground(themeManager.getSurfaceBackground());

        historyEnabled.setBackground(themeManager.getPanelBackground());
        historyEnabled.setForeground(themeManager.getForeground());
        dialog.setVisible(true);
    }

    private void addMotdCodeButton(JComponent container, String label, String code, JTextArea editor) {
        JButton button = new JButton(label);
        button.setFont(button.getFont().deriveFont(Font.PLAIN, 11f));
        button.setMargin(new Insets(3, 8, 3, 8));
        button.setToolTipText("Insert Minecraft formatting code §" + code);
        button.addActionListener(event -> {
            editor.insert("§" + code, editor.getCaretPosition());
            editor.requestFocusInWindow();
        });
        container.add(button);
    }

    /**
     * Converts the common escaped MOTD notation into the canonical Minecraft
     * representation. This accepts both the Java/properties form (\\u00A7 and
     * \\n) and the slash-prefixed form often copied from launcher or web examples
     * (/u00A7 and /n), while preserving already decoded § formatting codes.
     */
    private static String normalizeMotdInput(String text) {
        return MotdTextUtils.normalize(text);
    }

    private static String normalizeMotdInput(String text, boolean allowSlashNewlineAtStart) {
        return MotdTextUtils.normalize(text, allowSlashNewlineAtStart);
    }

    private static int countNewlines(String text) {
        int count = 0;
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) == '\n') {
                count++;
            }
        }
        return count;
    }

    /**
     * Truncates a MOTD so it keeps at most {@code maxLines} lines; the length of
     * each individual line is irrelevant to Minecraft's server list rendering.
     */
    private static String limitMotdLines(String text, int maxLines) {
        return MotdTextUtils.limitLines(text, maxLines);
    }

    /**
     * Applies a document edit while keeping the total at two lines. The inserted
     * text is trimmed so it can never add more lines than the remaining budget;
     * an edit that would create a line beyond the limit (for example pressing
     * Enter on the second line) is rejected entirely so the caret stays in place.
     */
    private void applyMotdEdit(DocumentFilter.FilterBypass bypass, int offset, int length,
                               String text, AttributeSet attrs) throws BadLocationException {
        int documentLength = bypass.getDocument().getLength();
        String current = bypass.getDocument().getText(0, documentLength);
        String prefix = current.substring(0, Math.min(offset, documentLength));
        String suffix = current.substring(Math.min(offset + length, documentLength));
        String normalizedPrefix = normalizeMotdInput(prefix);
        boolean allowSlashNewlineAtStart = normalizedPrefix.isEmpty() || normalizedPrefix.endsWith("§r");
        String normalizedText = normalizeMotdInput(text, allowSlashNewlineAtStart);
        int existingNewlines = countNewlines(prefix) + countNewlines(suffix);
        String limitedText = limitMotdLines(normalizedText, MAX_MOTD_LINES - existingNewlines);
        if (limitedText.isEmpty() && !normalizedText.isEmpty()) {
            // Reject edits that would exceed the line limit (e.g. Enter on line 2).
            return;
        }
        bypass.replace(offset, length, limitedText, attrs);
    }


    private void refreshMotdHistoryModel(JList<String> list, MotdHistoryStore.State history) {
        DefaultListModel<String> model = new DefaultListModel<>();
        for (String entry : history.getEntries()) {
            model.addElement(entry);
        }
        list.setModel(model);
    }

    private void updateServerPropertiesMotd(String motd) {
        Path path = Path.of("server.properties");
        Properties properties;
        try {
            properties = Utf8Properties.load(path);
        } catch (IOException error) {
            if (Files.exists(path)) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(parentFrame,
                        "MOTD applied in memory, but the existing server.properties could not be read: " + error.getMessage(),
                        "MOTD Warning", JOptionPane.WARNING_MESSAGE));
                return;
            }
            // A missing properties file can be created normally.
            properties = new Properties();
        }
        properties.setProperty("motd", motd);
        try {
            Utf8Properties.store(path, properties, "Minecraft server properties - Modified via DedicatedPower");
        } catch (IOException error) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(parentFrame,
                    "MOTD applied in memory, but server.properties could not be saved: " + error.getMessage(),
                    "MOTD Warning", JOptionPane.WARNING_MESSAGE));
        }
    }

    private String motdToHtml(String motd) {
        ThemeManager themeManager = ThemeManager.getInstance();
        return motdToHtml(motd, themeManager.getInputBackground());
    }

    private String motdToHtml(String motd, Color background) {
        String[] colors = {"000000", "0000AA", "00AA00", "00AAAA", "AA0000", "AA00AA", "FFAA00", "AAAAAA",
                "555555", "5555FF", "55FF55", "55FFFF", "FF5555", "FF55FF", "FFFF55", "FFFFFF"};
        ThemeManager themeManager = ThemeManager.getInstance();
        StringBuilder html = new StringBuilder("<html><body style='font-family:sans-serif;padding:8px;color:" + toHex(themeManager.getForeground())
                + ";background-color:" + toHex(background) + "'>");
        boolean colorOpen = false;
        boolean bold = false;
        boolean italic = false;
        boolean underline = false;
        boolean strike = false;
        boolean obfuscated = false;

        motd = normalizeMotdInput(motd);
        for (int index = 0; index < motd.length(); index++) {
            char character = motd.charAt(index);
            if (character == '§' && index + 1 < motd.length()) {
                char code = Character.toLowerCase(motd.charAt(++index));
                int colorIndex = "0123456789abcdef".indexOf(code);
                if (colorIndex >= 0) {
                    if (obfuscated) { html.append("</span>"); }
                    closeMotdFormatting(html, bold, italic, underline, strike, colorOpen);
                    html.append("<span style='color:#").append(colors[colorIndex]).append("'>");
                    colorOpen = true;
                    bold = italic = underline = strike = obfuscated = false;
                } else if (code == 'l' || code == 'o' || code == 'n' || code == 'm') {
                    if (code == 'l' && !bold) { html.append("<b>"); bold = true; }
                    if (code == 'o' && !italic) { html.append("<i>"); italic = true; }
                    if (code == 'n' && !underline) { html.append("<u>"); underline = true; }
                    if (code == 'm' && !strike) { html.append("<s>"); strike = true; }
                } else if (code == 'k') {
                    // Minecraft randomizes glyphs; a browser preview cannot reproduce
                    // that renderer, so indicate the obfuscated segment visually.
                    if (!obfuscated) {
                        html.append("<span style='letter-spacing:2px'>");
                        obfuscated = true;
                    }
                } else if (code == 'r') {
                    if (obfuscated) { html.append("</span>"); }
                    closeMotdFormatting(html, bold, italic, underline, strike, colorOpen);
                    colorOpen = false;
                    bold = italic = underline = strike = obfuscated = false;
                }
                continue;
            }
            if (character == '\n') {
                html.append("<br>");
            } else if (character == '&') {
                html.append("&amp;");
            } else if (character == '<') {
                html.append("&lt;");
            } else if (character == '>') {
                html.append("&gt;");
            } else {
                html.append(character);
            }
        }
        if (obfuscated) { html.append("</span>"); }
        closeMotdFormatting(html, bold, italic, underline, strike, colorOpen);
        return html.append("</body></html>").toString();
    }

    private void closeMotdFormatting(StringBuilder html, boolean bold, boolean italic,
                                     boolean underline, boolean strike, boolean colorOpen) {
        if (strike) html.append("</s>");
        if (underline) html.append("</u>");
        if (italic) html.append("</i>");
        if (bold) html.append("</b>");
        if (colorOpen) html.append("</span>");
    }

    private String toHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    private void exportModList() {
        List<ModContainer> mods = new ArrayList<>(FabricLoader.getInstance().getAllMods());
        mods.sort(Comparator.comparing(mod -> mod.getMetadata().getName().toLowerCase(Locale.ROOT)));

        JDialog dialog = new JDialog(parentFrame, "Export Mod List", true);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setSize(460, 230);
        dialog.setLocationRelativeTo(parentFrame);

        JPanel options = new JPanel(new GridLayout(0, 1, 8, 8));
        options.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        options.add(new JLabel(mods.size() + " installed mods detected"));
        JRadioButton textButton = new JRadioButton("Plain text (.txt)", true);
        JRadioButton markdownButton = new JRadioButton("Markdown table (.md)");
        ButtonGroup formatGroup = new ButtonGroup();
        formatGroup.add(textButton);
        formatGroup.add(markdownButton);
        options.add(textButton);
        options.add(markdownButton);
        dialog.add(options, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(event -> dialog.dispose());
        JButton exportButton = new JButton("Export...");
        exportButton.addActionListener(event -> {
            boolean markdown = markdownButton.isSelected();
            dialog.dispose();
            saveModList(markdown, mods);
        });
        buttons.add(cancelButton);
        buttons.add(exportButton);
        dialog.add(buttons, BorderLayout.SOUTH);

        ThemeManager.getInstance().applyTo(dialog);
        dialog.setVisible(true);
    }

    private void saveModList(boolean markdown, List<ModContainer> mods) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Mod List");
        chooser.setSelectedFile(new File(markdown ? "mod-list.md" : "mod-list.txt"));
        if (chooser.showSaveDialog(parentFrame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File target = chooser.getSelectedFile();
        String content = markdown ? buildModListMarkdown(mods) : buildModListText(mods);
        try (FileWriter writer = new FileWriter(target)) {
            writer.write(content);
            JOptionPane.showMessageDialog(parentFrame,
                    "Mod list saved to:\n" + target.getAbsolutePath(),
                    "Export Complete", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException error) {
            JOptionPane.showMessageDialog(parentFrame,
                    "Failed to save mod list: " + error.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String buildModListText(List<ModContainer> mods) {
        StringBuilder output = new StringBuilder();
        output.append("Minecraft Mod List\n");
        output.append("Generated: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).append('\n');
        output.append("Total mods: ").append(mods.size()).append("\n\n");
        for (ModContainer mod : mods) {
            output.append("- ").append(mod.getMetadata().getName())
                    .append(" (").append(mod.getMetadata().getId()).append(')')
                    .append(" v").append(mod.getMetadata().getVersion().getFriendlyString()).append('\n');
            String description = mod.getMetadata().getDescription();
            if (description != null && !description.isBlank()) {
                output.append("  ").append(description.replace('\n', ' ').trim()).append('\n');
            }
        }
        return output.toString();
    }

    private String buildModListMarkdown(List<ModContainer> mods) {
        StringBuilder output = new StringBuilder();
        output.append("# Minecraft Mod List\n\n");
        output.append("Generated: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()))
                .append(" — ").append(mods.size()).append(" mods\n\n");
        output.append("| Name | ID | Version | Description |\n");
        output.append("|---|---|---|---|\n");
        for (ModContainer mod : mods) {
            output.append("| ").append(escapeMarkdownCell(mod.getMetadata().getName()))
                    .append(" | ").append(escapeMarkdownCell(mod.getMetadata().getId()))
                    .append(" | ").append(escapeMarkdownCell(mod.getMetadata().getVersion().getFriendlyString()))
                    .append(" | ").append(escapeMarkdownCell(mod.getMetadata().getDescription()))
                    .append(" |\n");
        }
        return output.toString();
    }

    private String escapeMarkdownCell(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("|", "\\|").replace('\n', ' ').trim();
    }

    private void showCommandPalette() {
        JDialog dialog = new JDialog(parentFrame, "Command Palette", false);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(500, 400);
        dialog.setLocationRelativeTo(parentFrame);

        JTextField searchField = new JTextField();
        searchField.setFont(new Font("Monospaced", Font.PLAIN, 14));

        DefaultListModel<String> commandModel = new DefaultListModel<>();
        String[] commonCommands = {
                "give @p diamond 64",
                "gamemode creative @a",
                "time set day",
                "weather clear",
                "tp @p 0 100 0",
                "kill @e[type=item]",
                "difficulty peaceful",
                "gamerule doDaylightCycle false",
                "whitelist add <player>",
                "op <player>",
                "ban <player>",
                "pardon <player>"
        };

        for (String cmd : commonCommands) {
            commandModel.addElement(cmd);
        }

        JList<String> commandList = new JList<>(commandModel);
        commandList.setFont(new Font("Monospaced", Font.PLAIN, 12));

        searchField.addActionListener(e -> {
            String selected = commandList.getSelectedValue();
            if (selected != null) {
                runCommand(selected);
                dialog.dispose();
            } else if (!searchField.getText().isEmpty()) {
                runCommand(searchField.getText());
                dialog.dispose();
            }
        });

        dialog.add(searchField, BorderLayout.NORTH);
        dialog.add(new JScrollPane(commandList), BorderLayout.CENTER);
        dialog.setVisible(true);
        searchField.requestFocus();
    }

    private void showScheduledTasks() {
        JOptionPane.showMessageDialog(parentFrame,
                "Scheduled Tasks feature coming soon!\n\nThis will allow you to schedule commands to run at specific times or intervals.",
                "Scheduled Tasks", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showRconConnection() {

        JOptionPane.showMessageDialog(this, "RCON connection feature coming soon!",
                "Info", JOptionPane.INFORMATION_MESSAGE);

        return;
//        JDialog dialog = new JDialog(parentFrame, "RCON Connection", true);
//        dialog.setLayout(new GridLayout(4, 2, 10, 10));
//        dialog.setSize(350, 200);
//        dialog.setLocationRelativeTo(parentFrame);
//
//        dialog.add(new JLabel("Host:"));
//        JTextField hostField = new JTextField("localhost");
//        dialog.add(hostField);
//
//        dialog.add(new JLabel("Port:"));
//        JTextField portField = new JTextField("25575");
//        dialog.add(portField);
//
//        dialog.add(new JLabel("Password:"));
//        JPasswordField passwordField = new JPasswordField();
//        dialog.add(passwordField);
//
//        JButton connectButton = new JButton("Connect");
//        JButton cancelButton = new JButton("Cancel");
//
//        connectButton.addActionListener(e -> {
//            JOptionPane.showMessageDialog(dialog, "RCON connection feature coming soon!",
//                    "Info", JOptionPane.INFORMATION_MESSAGE);
//        });
//
//        cancelButton.addActionListener(e -> dialog.dispose());
//
//        dialog.add(connectButton);
//        dialog.add(cancelButton);
//
//        dialog.setVisible(true);
    }

    private void showDatapackManager() {
        JDialog dialog = new JDialog(parentFrame, "Datapack Manager", true);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setSize(500, 400);
        dialog.setLocationRelativeTo(parentFrame);

        DefaultListModel<String> datapackModel = new DefaultListModel<>();

        // Try to list datapacks from world/datapacks directory
        File datapackDir = new File("world/datapacks");
        if (datapackDir.exists() && datapackDir.isDirectory()) {
            File[] datapacks = datapackDir.listFiles();
            if (datapacks != null) {
                for (File dp : datapacks) {
                    if (dp.isDirectory() || dp.getName().endsWith(".zip")) {
                        datapackModel.addElement(dp.getName());
                    }
                }
            }
        }

        JList<String> datapackList = new JList<>(datapackModel);
        JScrollPane scrollPane = new JScrollPane(datapackList);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton reloadButton = new JButton("Reload Datapacks");
        JButton closeButton = new JButton("Close");

        reloadButton.addActionListener(e -> {
            runCommand("reload");
            JOptionPane.showMessageDialog(dialog, "Datapacks reloaded!", "Success", JOptionPane.INFORMATION_MESSAGE);
        });

        closeButton.addActionListener(e -> dialog.dispose());

        buttonPanel.add(reloadButton);
        buttonPanel.add(closeButton);

        dialog.add(scrollPane, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void showResourcePackSettings() {
        JDialog dialog = new JDialog(parentFrame, "Resource Pack Settings", true);
        dialog.setLayout(new GridLayout(4, 2, 10, 10));
        dialog.setSize(500, 200);
        dialog.setLocationRelativeTo(parentFrame);

        dialog.add(new JLabel("Resource Pack URL:"));
        JTextField urlField = new JTextField();
        dialog.add(urlField);

        dialog.add(new JLabel("SHA-1 Hash (optional):"));
        JTextField hashField = new JTextField();
        dialog.add(hashField);

        dialog.add(new JLabel("Require Resource Pack:"));
        JCheckBox requireCheckbox = new JCheckBox();
        dialog.add(requireCheckbox);

        JButton saveButton = new JButton("Save");
        JButton cancelButton = new JButton("Cancel");

        saveButton.addActionListener(e -> {
            JOptionPane.showMessageDialog(dialog,
                    "Resource pack settings saved!\nEdit server.properties to apply changes.",
                    "Success", JOptionPane.INFORMATION_MESSAGE);
            dialog.dispose();
        });

        cancelButton.addActionListener(e -> dialog.dispose());

        dialog.add(saveButton);
        dialog.add(cancelButton);

        dialog.setVisible(true);
    }

    private void changeServerIcon() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select Server Icon (64x64 PNG)");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PNG Images", "png"));

        if (fileChooser.showOpenDialog(parentFrame) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            File serverIcon = new File("server-icon.png");

            try {
                Files.copy(selectedFile.toPath(), serverIcon.toPath(), StandardCopyOption.REPLACE_EXISTING);
                JOptionPane.showMessageDialog(parentFrame,
                        "Server icon updated! Restart server for changes to take effect.",
                        "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(parentFrame,
                        "Failed to update icon: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ==================== HELP MENU ====================
    private JMenu createHelpMenu() {
        JMenu helpMenu = new JMenu("Help");
        helpMenu.setMnemonic('H');

        JMenuItem githubItem = new JMenuItem("GitHub Repository");
        githubItem.addActionListener(e -> openURL("https://github.com/SuperSirvu/DedicatedPower"));
        helpMenu.add(githubItem);

        JMenuItem modrinthItem = new JMenuItem("Modrinth Page");
        modrinthItem.addActionListener(e -> openURL("https://modrinth.com/mod/server-os"));
        helpMenu.add(modrinthItem);

        helpMenu.addSeparator();

        JMenuItem wikiItem = new JMenuItem("Minecraft Wiki");
        wikiItem.addActionListener(e -> openURL("https://minecraft.wiki"));
        helpMenu.add(wikiItem);

        JMenuItem commandRefItem = new JMenuItem("Command Reference");
        commandRefItem.addActionListener(e -> showCommandReference());
        helpMenu.add(commandRefItem);

        JMenuItem shortcutsItem = new JMenuItem("Keyboard Shortcuts");
        shortcutsItem.setAccelerator(KeyStroke.getKeyStroke("F1"));
        shortcutsItem.addActionListener(e -> showKeyboardShortcuts());
        helpMenu.add(shortcutsItem);

        helpMenu.addSeparator();

        JMenuItem bugItem = new JMenuItem("Report Bug...");
        bugItem.addActionListener(e -> reportBug());
        helpMenu.add(bugItem);

        helpMenu.addSeparator();

        JMenuItem sysInfoItem = new JMenuItem("System Information");
        sysInfoItem.addActionListener(e -> showSystemInformation());
        helpMenu.add(sysInfoItem);

        return helpMenu;
    }

    private void openURL(String url) {
        try {
            Desktop.getDesktop().browse(new java.net.URI(url));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(parentFrame,
                    "Failed to open browser: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showCommandReference() {
        String commands = """
                === ESSENTIAL COMMANDS ===

                /help - Show all commands
                /list - Show online players
                /say <message> - Broadcast message
                /tell <player> <message> - Private message

                === PLAYER MANAGEMENT ===
                /kick <player> [reason] - Kick player
                /ban <player> [reason] - Ban player
                /pardon <player> - Unban player
                /op <player> - Give operator status
                /deop <player> - Remove operator status
                /whitelist add/remove <player> - Manage whitelist

                === WORLD ===
                /time set <time> - Set time (day/night/0-24000)
                /weather <clear|rain|thunder> - Set weather
                /gamerule <rule> <value> - Change game rule
                /difficulty <difficulty> - Set difficulty
                /gamemode <mode> <player> - Change game mode

                === TELEPORT ===
                /tp <player> <x> <y> <z> - Teleport player
                /tp <player1> <player2> - Teleport to player

                === ITEMS ===
                /give <player> <item> [amount] - Give items
                /clear <player> - Clear inventory

                === PERFORMANCE ===
                /kill @e[type=item] - Clear dropped items
                /save-all - Save all worlds
                /reload - Reload datapacks
                /opengui - Open the DedicatedPower GUI (also after --nogui)
                """;

        JTextArea textArea = new JTextArea(commands);
        textArea.setEditable(false);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 11));

        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(500, 400));

        JOptionPane.showMessageDialog(parentFrame, scrollPane, "Command Reference", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showKeyboardShortcuts() {
        String shortcuts = """
                === KEYBOARD SHORTCUTS ===

                Ctrl+S - Save All Worlds
                Ctrl+Shift+P - Command Palette
                F1 - Keyboard Shortcuts (this dialog)
                F5 - Refresh/Reload

                === CONSOLE ===
                Up/Down - Navigate command history
                Tab - Autocomplete command
                Enter - Execute command
                Esc - Clear suggestions
                """;

        JTextArea textArea = new JTextArea(shortcuts);
        textArea.setEditable(false);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

        JOptionPane.showMessageDialog(parentFrame, textArea, "Keyboard Shortcuts", JOptionPane.INFORMATION_MESSAGE);
    }

    private void reportBug() {
        JOptionPane.showMessageDialog(parentFrame,
                "To report a bug, please visit:\nhttps://github.com/SuperSirvu/DedicatedPower/issues",
                "Report Bug", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showSystemInformation() {
        Runtime runtime = Runtime.getRuntime();

        String modVersion = FabricLoader.getInstance()
                .getModContainer("dedicatedpower")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("Unknown");
        String info = String.format("""
                        === SYSTEM INFORMATION ===

                        Java Version: %s
                        Java Vendor: %s
                        OS: %s %s (%s)

                        CPU Cores: %d
                        Total Memory: %d MB
                        Max Memory: %d MB
                        Free Memory: %d MB

                        Minecraft Version: %s
                        DedicatedPower Version: %s
                        """,
                System.getProperty("java.version"),
                System.getProperty("java.vendor"),
                System.getProperty("os.name"),
                System.getProperty("os.version"),
                System.getProperty("os.arch"),
                runtime.availableProcessors(),
                runtime.totalMemory() / 1024 / 1024,
                runtime.maxMemory() / 1024 / 1024,
                runtime.freeMemory() / 1024 / 1024,
                server.getServerVersion(),
                modVersion
        );

        JTextArea textArea = new JTextArea(info);
        textArea.setEditable(false);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 11));

        JOptionPane.showMessageDialog(parentFrame, textArea, "System Information", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Replaces the vanilla close behaviour (which shuts down the server whenever
     * the window X button is pressed) with a confirmation dialog that lets the
     * user choose between closing only the GUI, shutting down the server as well,
     * or cancelling.
     *
     * <p>The server shutdown runs on a dedicated thread so the Swing event dispatch
     * thread (and therefore the whole GUI) stays responsive while worlds are saved;
     * once the server has fully stopped, the JVM exits cleanly.</p>
     */
    public static void installCloseConfirmation(JFrame frame, DedicatedServer server) {
        // Remove the vanilla window listeners, which halt the server unconditionally.
        for (WindowListener listener : frame.getWindowListeners()) {
            frame.removeWindowListener(listener);
        }
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                // Keep the shared lifecycle state accurate if another path disposes
                // the frame instead of using the custom close dialog.
                if (net.supersirvu.ServerGuiState.getFrame() == frame) {
                    net.supersirvu.ServerGuiState.setFrame(null);
                }
            }

            @Override
            public void windowClosing(WindowEvent event) {
                // If the server has already stopped, just close the window without asking.
                if (!server.isRunning()) {
                    net.supersirvu.ServerGuiState.setFrame(null);
                    frame.dispose();
                    return;
                }

                String[] options = {
                        "Close GUI Only",
                        "Close GUI and Server",
                        "Cancel"
                };
                int choice = JOptionPane.showOptionDialog(
                        frame,
                        "Do you want to close only the GUI (the server keeps running) or also shut down the server?",
                        "Close Dedicated Server",
                        JOptionPane.YES_NO_CANCEL_OPTION,
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        options,
                        options[0]
                );
                if (choice == 0) {
                    // Hide only the GUI window; retain the frame so /opengui can
                    // show the same window again without creating a duplicate.
                    frame.setVisible(false);
                    // Mirror the message the GUI prints when /opengui finds it
                    // already open, so the console always says what happened.
                    server.execute(() -> server.createCommandSourceStack().sendSuccess(
                            () -> Component.literal("The DedicatedPower GUI has been closed; the server keeps running."),
                            false));
                } else if (choice == 1) {
                    // The server is shutting down; do not retain a reusable GUI frame.
                    net.supersirvu.ServerGuiState.setFrame(null);
                    server.execute(() -> server.createCommandSourceStack().sendSuccess(
                            () -> Component.literal("The DedicatedPower GUI has been closed and the server is shutting down."),
                            false));
                    // Gracefully stop the server on a background thread: the GUI stays
                    // responsive (the log keeps updating) while worlds are saved, then
                    // the JVM exits as soon as the server has fully stopped.
                    frame.setTitle("Minecraft server - shutting down!");
                    frame.setEnabled(false);
                    Thread shutdown = new Thread(() -> {
                        try {
                            server.halt(true);
                        } finally {
                            System.exit(0);
                        }
                    }, "Dedicated server shutdown");
                    shutdown.setDaemon(false);
                    shutdown.start();
                }
                // Otherwise (Cancel or the dialog was dismissed) keep everything as it is.
            }
        });
    }
}
