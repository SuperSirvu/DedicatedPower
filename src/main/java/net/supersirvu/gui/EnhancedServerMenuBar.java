package net.supersirvu.gui;

import net.minecraft.server.dedicated.MinecraftDedicatedServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.WorldProperties;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class EnhancedServerMenuBar extends JMenuBar {
    private final MinecraftDedicatedServer server;
    private final Frame parentFrame;

    public EnhancedServerMenuBar(MinecraftDedicatedServer server, Frame parentFrame) {
        this.server = server;
        this.parentFrame = parentFrame;

        SwingUtilities.invokeLater(this::createMenus);
    }

    private void createMenus() {
        add(createServerMenu());
        add(createWorldMenu());
        add(createPerformanceMenu());
        add(createToolsMenu());
        add(createHelpMenu());
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
            JRadioButtonMenuItem diffItem = new JRadioButtonMenuItem(difficulty.getName());
            diffItem.setSelected(server.getSaveProperties().getDifficulty() == difficulty);
            diffItem.addActionListener(e -> setDifficulty(difficulty));
            difficultyGroup.add(diffItem);
            difficultyMenu.add(diffItem);
        }
        serverMenu.add(difficultyMenu);

        // Default Game Mode submenu
        JMenu gameModeMenu = new JMenu("Default Game Mode");
        ButtonGroup gameModeGroup = new ButtonGroup();
        for (GameMode mode : GameMode.values()) {
            JRadioButtonMenuItem modeItem = new JRadioButtonMenuItem(mode.getTranslatableName().getString());
            modeItem.setSelected(server.getSaveProperties().getGameMode() == mode);
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

        // Load server.properties
        File propertiesFile = new File("server.properties");
        Properties properties = new Properties();

        try (FileInputStream fis = new FileInputStream(propertiesFile)) {
            properties.load(fis);
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
            try (FileOutputStream fos = new FileOutputStream(propertiesFile)) {
                properties.store(fos, "Minecraft server properties - Modified via GUI");
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
        JCheckBox enabledCheckbox = new JCheckBox("Whitelist Enabled", server.getPlayerManager().isWhitelistEnabled());
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(enabledCheckbox);

        // Whitelist entries
        DefaultListModel<String> listModel = new DefaultListModel<>();
        for (String player : server.getPlayerManager().getWhitelist().getNames()) {
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
                server.getCommandManager().executeWithPrefix(server.getCommandSource(), "whitelist add " + player);
                listModel.addElement(player);
            }
        });

        removeButton.addActionListener(e -> {
            String selected = whitelistList.getSelectedValue();
            if (selected != null) {
                server.getCommandManager().executeWithPrefix(server.getCommandSource(), "whitelist remove " + selected);
                listModel.removeElement(selected);
            }
        });

        enabledCheckbox.addActionListener(e -> server.getCommandManager().executeWithPrefix(server.getCommandSource(),
                enabledCheckbox.isSelected() ? "whitelist on" : "whitelist off"));

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
        JOptionPane.showMessageDialog(parentFrame, "Difficulty set to " + difficulty.getName(),
                "Success", JOptionPane.INFORMATION_MESSAGE);
    }

    private void setDefaultGameMode(GameMode mode) {
        server.setDefaultGameMode(mode);
        JOptionPane.showMessageDialog(parentFrame, "Default game mode set to " + mode.getTranslatableName(),
                "Success", JOptionPane.INFORMATION_MESSAGE);
    }

    private void saveAllWorlds() {
        try {
            server.getCommandManager().executeWithPrefix(server.getCommandSource(), "save-all");
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
                    server.getCommandManager().executeWithPrefix(server.getCommandSource(), "save-all flush");
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
        for (ServerWorld world : server.getWorlds()) {
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
        java.util.List<ServerWorld> worlds = new java.util.ArrayList<>();
        for (ServerWorld world : server.getWorlds()) {
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
        ServerWorld selectedWorld = null;
        for (ServerWorld world : worlds) {
            if (getWorldName(world).equals(selectedWorldName)) {
                selectedWorld = world;
                break;
            }
        }

        if (selectedWorld == null) return;

        showGameruleDialog(selectedWorld, selectedWorldName);
    }

    private void showGameruleDialog(ServerWorld world, String worldName) {
        JDialog dialog = new JDialog(parentFrame, "Game Rules - " + worldName, true);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setSize(700, 600);
        dialog.setLocationRelativeTo(parentFrame);

        // Create tabbed pane for categories
        JTabbedPane tabbedPane = new JTabbedPane();

        // Maps to store components by category
        Map<GameRules.Category, JPanel> categoryPanels = new java.util.HashMap<>();
        Map<GameRules.Category, GridBagConstraints> categoryConstraints = new java.util.HashMap<>();

        // Initialize panels for each category
        for (GameRules.Category category : GameRules.Category.values()) {
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
        }

        // Store all gamerule components for later retrieval
        Map<String, JComponent> gameruleComponents = new java.util.HashMap<>();

        // Dynamically discover and add all gamerules
        world.getGameRules().accept(new GameRules.Visitor() {
            @Override
            public void visitBoolean(GameRules.Key<GameRules.BooleanRule> key, GameRules.Type<GameRules.BooleanRule> type) {
                GameRules.Category category = key.getCategory();
                JPanel panel = categoryPanels.get(category);
                GridBagConstraints gbc = categoryConstraints.get(category);

                if (panel != null && gbc != null) {
                    // Get current value from the world
                    boolean currentValue = world.getGameRules().getBoolean(key);

                    // Create checkbox
                    JCheckBox checkbox = new JCheckBox(formatGameruleName(key.getName()), currentValue);
                    checkbox.setName(key.getName());
                    checkbox.setToolTipText("Game rule: " + key.getName());

                    panel.add(checkbox, gbc);
                    gbc.gridy++;

                    gameruleComponents.put(key.getName(), checkbox);
                }
            }

            @Override
            public void visitInt(GameRules.Key<GameRules.IntRule> key, GameRules.Type<GameRules.IntRule> type) {
                GameRules.Category category = key.getCategory();
                JPanel panel = categoryPanels.get(category);
                GridBagConstraints gbc = categoryConstraints.get(category);

                if (panel != null && gbc != null) {
                    // Get current value from the world
                    int currentValue = world.getGameRules().getInt(key);

                    // Create row panel
                    JPanel rowPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
                    JLabel nameLabel = new JLabel(formatGameruleName(key.getName()) + ":");
                    nameLabel.setPreferredSize(new Dimension(300, 25));

                    // Dynamically extract min/max from the IntegerArgumentType
                    int min = 0;
                    int max = 1000000;

                    JSpinner spinner = new JSpinner(new SpinnerNumberModel(currentValue, min, max, 1));
                    spinner.setName(key.getName());
                    spinner.setPreferredSize(new Dimension(100, 25));
                    spinner.setToolTipText("Game rule: " + key.getName() + " (Min: " + min + ", Max: " + max + ")");

                    rowPanel.add(nameLabel);
                    rowPanel.add(spinner);

                    panel.add(rowPanel, gbc);
                    gbc.gridy++;

                    gameruleComponents.put(key.getName(), spinner);
                }
            }
        });

        // Add all category panels to tabbed pane
        for (GameRules.Category category : GameRules.Category.values()) {
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

    private String getCategoryDisplayName(GameRules.Category category) {
        return switch (category) {
            case PLAYER -> "Player";
            case MOBS -> "Mobs";
            case SPAWNING -> "Spawning";
            case DROPS -> "Drops";
            case UPDATES -> "Updates";
            case CHAT -> "Chat";
            case MISC -> "Miscellaneous";
        };
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

    private void resetGamerulesToDefaults(ServerWorld world) {
        // Reset common gamerules to their defaults using GameRules class knowledge
        world.getGameRules().accept(new GameRules.Visitor() {
            @Override
            public void visitBoolean(GameRules.Key<GameRules.BooleanRule> key, GameRules.Type<GameRules.BooleanRule> type) {
                // Create a new default rule to get the default value
                GameRules.BooleanRule defaultRule = type.createRule();
                executeGamerule(key.getName(), String.valueOf(defaultRule.get()));
            }

            @Override
            public void visitInt(GameRules.Key<GameRules.IntRule> key, GameRules.Type<GameRules.IntRule> type) {
                // Create a new default rule to get the default value
                GameRules.IntRule defaultRule = type.createRule();
                executeGamerule(key.getName(), String.valueOf(defaultRule.get()));
            }
        });
    }

    private void executeGamerule(String rule, String value) {
        try {
            server.getCommandManager().executeWithPrefix(
                    server.getCommandSource(),
                    "gamerule " + rule + " " + value
            );
        } catch (Exception e) {
            System.err.println("Failed to set gamerule " + rule + ": " + e.getMessage());
        }
    }

    private String getWorldName(ServerWorld world) {
        String dimensionKey = world.getRegistryKey().getValue().toString();
        if (dimensionKey.contains("overworld")) return "The Overworld";
        if (dimensionKey.contains("the_nether")) return "The Nether";
        if (dimensionKey.contains("the_end")) return "The End";
        return dimensionKey;
    }

    private void showWorldInfo(ServerWorld world) {
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
                world.getRegistryKey().getValue().toString(),
                world.getChunkManager().getLoadedChunkCount(),
                world.iterateEntities().spliterator().estimateSize(),
                world.getTimeOfDay(),
                world.isRaining() ? (world.isThundering() ? "Thunder" : "Rain") : "Clear",
                world.getDifficulty().getName()
        );

        JOptionPane.showMessageDialog(parentFrame, info, "World Information", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showWorldBorderSettings() {
        ServerWorld overworld = server.getOverworld();
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

                server.getCommandManager().executeWithPrefix(server.getCommandSource(),
                        String.format("worldborder center %f %f", centerX, centerZ));
                server.getCommandManager().executeWithPrefix(server.getCommandSource(),
                        String.format("worldborder set %f", size));

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
            server.getCommandManager().executeWithPrefix(server.getCommandSource(), "time set " + time);
        });
        menu.add(item);
    }

    private void setCustomTime() {
        String input = JOptionPane.showInputDialog(parentFrame, "Enter time (0-24000):", "Custom Time", JOptionPane.QUESTION_MESSAGE);
        if (input != null) {
            try {
                long time = Long.parseLong(input);
                server.getCommandManager().executeWithPrefix(server.getCommandSource(), "time set " + time);
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(parentFrame, "Invalid time value!", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void addWeatherOption(JMenu menu, String name, String weather) {
        JMenuItem item = new JMenuItem(name);
        item.addActionListener(e -> {
            server.getCommandManager().executeWithPrefix(server.getCommandSource(), "weather " + weather);
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
                    server.getCommandManager().executeWithPrefix(server.getCommandSource(),
                            "weather " + weather.toLowerCase() + " " + seconds);
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
            server.getCommandManager().executeWithPrefix(server.getCommandSource(), "reload");
            JOptionPane.showMessageDialog(parentFrame, "Chunks reloaded!", "Success", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void forceSave() {
        server.getCommandManager().executeWithPrefix(server.getCommandSource(), "save-all flush");
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
                        server.getCommandManager().executeWithPrefix(server.getCommandSource(), "save-all flush");
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
        ServerWorld overworld = server.getOverworld();
        if (overworld == null) return;

        WorldProperties properties = overworld.getLevelProperties();

        String info = String.format(
                "World Information\n\n" +
                        "Seed: %d\n" +
                        "Spawn: X=%d, Y=%d, Z=%d\n" +
                        "Difficulty: %s\n" +
                        "Hardcore: %s\n" +
                        "Allow Commands: %s",
                overworld.getSeed(),
                properties.getSpawnPos().getX(), properties.getSpawnPos().getY(), properties.getSpawnPos().getZ(),
                properties.getDifficulty().getName(),
                properties.isHardcore() ? "Yes" : "No",
                server.getProperties().enableCommandBlock ? "Yes" : "No"
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

                server.getCommandManager().executeWithPrefix(server.getCommandSource(), command);
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
                    server.getCommandManager().executeWithPrefix(server.getCommandSource(), "save-all flush");
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
        for (ServerWorld world : server.getWorlds()) {
            for (Object ignored : world.iterateEntities()) {
                totalEntities++;
            }
            totalChunks += world.getChunkManager().getLoadedChunkCount();
        }

        StringBuilder report = new StringBuilder();
        report.append("=== PERFORMANCE REPORT ===\n");
        report.append("Generated: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).append("\n\n");
        report.append("=== MEMORY ===\n");
        report.append(String.format("Used: %d MB\n", usedMemory / 1024 / 1024));
        report.append(String.format("Max: %d MB\n", maxMemory / 1024 / 1024));
        report.append(String.format("Usage: %.1f%%\n\n", (usedMemory * 100.0) / maxMemory));
        report.append("=== SERVER ===\n");
        report.append(String.format("TPS: %.2f\n", Math.min(20.0, 1000.0 / server.getAverageTickTime())));
        report.append(String.format("Average Tick: %.2f ms\n", server.getAverageTickTime()));
        report.append(String.format("Players: %d\n", server.getCurrentPlayerCount()));
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
                server.getCommandManager().executeWithPrefix(server.getCommandSource(), selected);
                dialog.dispose();
            } else if (!searchField.getText().isEmpty()) {
                server.getCommandManager().executeWithPrefix(server.getCommandSource(), searchField.getText());
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
            server.getCommandManager().executeWithPrefix(server.getCommandSource(), "reload");
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
                server.getVersion()
        );

        JTextArea textArea = new JTextArea(info);
        textArea.setEditable(false);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 11));

        JOptionPane.showMessageDialog(parentFrame, textArea, "System Information", JOptionPane.INFORMATION_MESSAGE);
    }
}