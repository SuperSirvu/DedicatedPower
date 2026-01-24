/*
 * Copyright (c) 2026 SuperSirvu
 *
 * Licensed under the MIT License.
 */

package net.supersirvu.gui;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftProfileTextures;
import net.minecraft.server.BannedPlayerEntry;
import net.minecraft.server.BannedPlayerList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;
import net.supersirvu.DedicatedPower;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class EnhancedPlayerListGui extends JPanel {
    private static final String[] SKINS = new String[]{
            "textures/entity/player/slim/alex.png",
            "textures/entity/player/slim/ari.png",
            "textures/entity/player/slim/efe.png",
            "textures/entity/player/slim/kai.png",
            "textures/entity/player/slim/makena.png",
            "textures/entity/player/slim/noor.png",
            "textures/entity/player/slim/steve.png",
            "textures/entity/player/slim/sunny.png",
            "textures/entity/player/slim/zuri.png",
            "textures/entity/player/wide/alex.png",
            "textures/entity/player/wide/ari.png",
            "textures/entity/player/wide/efe.png",
            "textures/entity/player/wide/kai.png",
            "textures/entity/player/wide/makena.png",
            "textures/entity/player/wide/noor.png",
            "textures/entity/player/wide/steve.png",
            "textures/entity/player/wide/sunny.png",
            "textures/entity/player/wide/zuri.png"
    };

    private final MinecraftServer server;
    private final JList<PlayerInfo> playerList;
    private final DefaultListModel<PlayerInfo> listModel;
    private final Map<String, BufferedImage> headCache;
    private int tick;
    private SortMode sortMode = SortMode.NAME;
    private String searchFilter = "";

    public EnhancedPlayerListGui(MinecraftServer server) {
        this.server = server;
        this.listModel = new DefaultListModel<>();
        this.playerList = new JList<>(listModel);
        this.headCache = new HashMap<>();

        this.setLayout(new BorderLayout());
        this.setBackground(new Color(240, 240, 240));

        // Setup player list
        setupPlayerList();

        // Create header panel with controls
        JPanel headerPanel = createHeaderPanel();
        this.add(headerPanel, BorderLayout.NORTH);

        // Add scroll pane with player list
        JScrollPane scrollPane = new JScrollPane(playerList);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        this.add(scrollPane, BorderLayout.CENTER);

        // Register tick handler
        server.addServerGuiTickable(this::tick);

        // Start async head loading
        startHeadLoading();
    }

    private void startHeadLoading() {
        // Periodically check and load missing player heads
        Timer headLoadTimer = new Timer(2000, e -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                String uuid = player.getGameProfile().id().toString();
                if (!headCache.containsKey(uuid)) {
                    loadPlayerHead(player.getGameProfile());
                }
            }
        });
        headLoadTimer.start();
    }

    private void loadPlayerHead(GameProfile profile) {
        String uuid = profile.id().toString();

        CompletableFuture.runAsync(() -> {
            try {
                // Get skin URL from Mojang session server
                MinecraftProfileTextures textures =
                        server.getApiServices().sessionService().getTextures(profile);

                if (textures.skin() != null) {
                    MinecraftProfileTexture skinTexture = textures.skin();
                    String skinUrl = skinTexture.getUrl();

                    // Download skin
                    URL url = URI.create(skinUrl).toURL();
                    DedicatedPower.LOGGER.info("Loading Skin: " + skinUrl);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(5000);

                    try (InputStream in = connection.getInputStream()) {
                        BufferedImage skin = ImageIO.read(in);

                        // Extract head from skin (8x8 face + 8x8 overlay)
                        BufferedImage head = extractHead(skin);
                        headCache.put(uuid, head);

                        // Trigger repaint
                        SwingUtilities.invokeLater(() -> playerList.repaint());
                    }
                } else {
                    // Use default Steve head if no skin
                    headCache.put(uuid, createDefaultHead(uuid));
                }
            } catch (Exception e) {
                // On error, use default head
                headCache.put(uuid, createDefaultHead(uuid));
            }
        });
    }

    private BufferedImage extractHead(BufferedImage skin) {
        // Create 32x32 head with overlay
        BufferedImage head = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = head.createGraphics();

        // Draw base face (from coordinates 8,8 size 8x8 in the skin)
        g.drawImage(skin, 0, 0, 32, 32, 8, 8, 16, 16, null);

        // Draw overlay/hat layer (from coordinates 40,8 size 8x8 in the skin)
        g.drawImage(skin, 0, 0, 32, 32, 40, 8, 48, 16, null);

        g.dispose();
        return head;
    }

    private BufferedImage createDefaultHead(String profile) {
        try {
            // Get the default skin texture identifier for this player
            String textureId = SKINS[Math.floorMod(profile.hashCode(), SKINS.length)];

            // Load from Minecraft's resource pack/jar
            InputStream stream = getClass().getClassLoader().getResourceAsStream("assets/minecraft/" + textureId);
            if (stream != null) {
                BufferedImage skin = ImageIO.read(stream);
                stream.close();
                return extractHead(skin);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Fallback if loading fails
        return createFallbackHead();
    }

    private BufferedImage createFallbackHead() {
        BufferedImage head = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = head.createGraphics();
        g.setColor(new Color(100, 70, 50));
        g.fillRect(0, 0, 32, 10);
        g.setColor(new Color(198, 146, 103));
        g.fillRect(0, 10, 32, 22);
        g.setColor(new Color(80, 60, 40));
        g.fillRect(8, 14, 6, 3);
        g.fillRect(18, 14, 6, 3);
        g.fillRect(10, 24, 12, 2);
        g.dispose();
        return head;
    }

    private void setupPlayerList() {
        playerList.setCellRenderer(new PlayerCellRenderer());
        playerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        playerList.setBackground(Color.WHITE);
        playerList.setFixedCellHeight(40);

        // Add right-click context menu
        playerList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int index = playerList.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        playerList.setSelectedIndex(index);
                        showContextMenu(e.getX(), e.getY(), listModel.get(index));
                    }
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int index = playerList.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        showPlayerDetails(listModel.get(index));
                    }
                }
            }
        });
    }

    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(250, 250, 250));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Search field
        JTextField searchField = new JTextField();
        searchField.setText("Search players...");
        searchField.setForeground(Color.GRAY);
        searchField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent e) {
                if (searchField.getText().equals("Search players...")) {
                    searchField.setText("");
                    searchField.setForeground(Color.BLACK);
                }
            }
            public void focusLost(java.awt.event.FocusEvent e) {
                if (searchField.getText().isEmpty()) {
                    searchField.setText("Search players...");
                    searchField.setForeground(Color.GRAY);
                }
            }
        });
        searchField.addActionListener(e -> {
            searchFilter = searchField.getText().equals("Search players...") ? "" : searchField.getText();
            updatePlayerList();
        });

        // Sort button
        JButton sortButton = new JButton("Sort: Name");
        sortButton.addActionListener(e -> {
            sortMode = sortMode.next();
            sortButton.setText("Sort: " + sortMode.getDisplayName());
            updatePlayerList();
        });

        // Ban list button
        JButton banListButton = new JButton("Ban List");
        banListButton.addActionListener(e -> showBanList());

        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        controlPanel.setBackground(new Color(250, 250, 250));
        controlPanel.add(sortButton);
        controlPanel.add(banListButton);

        panel.add(searchField, BorderLayout.CENTER);
        panel.add(controlPanel, BorderLayout.EAST);

        return panel;
    }

    public void tick() {
        if (this.tick++ % 20 == 0) {
            updatePlayerList();
        }
    }

    private void updatePlayerList() {
        List<PlayerInfo> players = new ArrayList<>();

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            String name = player.getGameProfile().name();

            // Apply search filter
            if (!searchFilter.isEmpty() && !name.toLowerCase().contains(searchFilter.toLowerCase())) {
                continue;
            }

            PlayerInfo info = new PlayerInfo(
                    player.getGameProfile(),
                    player.getPlayerConfigEntry(),
                    player.networkHandler.getLatency(),
                    player.interactionManager.getGameMode(),
                    server.getPlayerManager().isOperator(player.getPlayerConfigEntry()),
                    player.getHealth(),
                    player.getHungerManager().getFoodLevel()
            );
            players.add(info);
        }

        // Sort players
        players.sort(sortMode.getComparator());

        // Update list model
        listModel.clear();
        for (PlayerInfo info : players) {
            listModel.addElement(info);
        }
    }

    private void showContextMenu(int x, int y, PlayerInfo player) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem kickItem = new JMenuItem("Kick Player");
        kickItem.addActionListener(e -> {
            String reason = JOptionPane.showInputDialog(this, "Kick reason:", "Kick Player", JOptionPane.QUESTION_MESSAGE);
            if (reason != null) {
                ServerPlayerEntity serverPlayer = server.getPlayerManager().getPlayer(player.profile.id());
                serverPlayer.networkHandler.disconnect(Text.literal(reason));
            }
        });

        JMenuItem banItem = new JMenuItem("Ban Player");
        banItem.addActionListener(e -> {
            String reason = JOptionPane.showInputDialog(this, "Ban reason:", "Ban Player", JOptionPane.QUESTION_MESSAGE);
            if (reason != null) {
                BannedPlayerList bannedPlayerList = server.getPlayerManager().getUserBanList();
                if (!bannedPlayerList.contains(player.configEntry)) {
                    BannedPlayerEntry bannedPlayerEntry = new BannedPlayerEntry(player.configEntry, null, "SERVER", null, reason);
                    bannedPlayerList.add(bannedPlayerEntry);
                    ServerPlayerEntity serverPlayerEntity = server.getPlayerManager().getPlayer(player.profile.id());
                    if (serverPlayerEntity != null) {
                        serverPlayerEntity.networkHandler.disconnect(Text.translatable("multiplayer.disconnect.banned"));
                    }
                }
            }
        });

        JMenuItem messageItem = new JMenuItem("Send Message");
        messageItem.addActionListener(e -> {
            String message = JOptionPane.showInputDialog(this, "Message:", "Send Message", JOptionPane.QUESTION_MESSAGE);
            if (message != null) {
                server.getPlayerManager().getPlayer(player.profile.id()).sendMessage(Text.literal("SERVER: " + message));
            }
        });

        JMenuItem opItem = new JMenuItem(player.isOp ? "Deop Player" : "Op Player");
        opItem.addActionListener(e -> {
            if(player.isOp) {
                server.getPlayerManager().removeFromOperators(player.configEntry);
            } else {
                server.getPlayerManager().addToOperators(player.configEntry);
            }
        });

        menu.add(kickItem);
        menu.add(banItem);
        menu.add(messageItem);
        menu.addSeparator();
        menu.add(opItem);

        menu.show(playerList, x, y);
    }

    private void showPlayerDetails(PlayerInfo player) {
        String details = String.format(
                "Player: %s\n\nPing: %d ms\nGame Mode: %s\nOperator: %s\nHealth: %.1f\nHunger: %d",
                player.profile.name(),
                player.ping,
                player.gameMode.getTranslatableName(),
                player.isOp ? "Yes" : "No",
                player.health,
                player.hunger
        );

        JOptionPane.showMessageDialog(this, details, "Player Details", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showBanList() {
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Ban List", true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(400, 300);
        dialog.setLocationRelativeTo(this);

        DefaultListModel<String> banModel = new DefaultListModel<>();
        JList<String> banList = new JList<>(banModel);

        // Get banned players
        for (String bannedPlayer : server.getPlayerManager().getUserBanList().getNames()) {
            banModel.addElement(bannedPlayer);
        }

        JScrollPane scrollPane = new JScrollPane(banList);

        JButton unbanButton = new JButton("Unban Selected");
        unbanButton.addActionListener(e -> {
            String selected = banList.getSelectedValue();
            if (selected != null) {
                int confirm = JOptionPane.showConfirmDialog(dialog,
                        "Unban " + selected + "?",
                        "Confirm Unban",
                        JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    BannedPlayerList bannedPlayerList = server.getPlayerManager().getUserBanList();
                    PlayerConfigEntry player = bannedPlayerList.values().stream().filter(entry -> entry.getKey().name().equals(selected)).findFirst().orElseThrow().getKey();
                    if (bannedPlayerList.contains(player)) {
                        bannedPlayerList.remove(player);
                    }
                    banModel.removeElement(selected);
                }
            }
        });

        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dialog.dispose());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(unbanButton);
        buttonPanel.add(closeButton);

        dialog.add(scrollPane, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    // Player info data class
    private static class PlayerInfo {
        final GameProfile profile;
        final PlayerConfigEntry configEntry;
        final int ping;
        final GameMode gameMode;
        final boolean isOp;
        final float health;
        final int hunger;

        PlayerInfo(GameProfile profile, PlayerConfigEntry configEntry, int ping, GameMode gameMode, boolean isOp, float health, int hunger) {
            this.profile = profile;
            this.configEntry = configEntry;
            this.ping = ping;
            this.gameMode = gameMode;
            this.isOp = isOp;
            this.health = health;
            this.hunger = hunger;
        }
    }

    // Custom cell renderer
    private class PlayerCellRenderer extends JPanel implements ListCellRenderer<PlayerInfo> {
        private final JLabel headLabel;
        private final JLabel nameLabel;
        private final JLabel infoLabel;
        private final JLabel pingLabel;
        private final JPanel iconPanel;

        PlayerCellRenderer() {
            setLayout(new BorderLayout(10, 0));
            setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

            // Player head label
            headLabel = new JLabel();
            headLabel.setPreferredSize(new Dimension(32, 32));
            headLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY));

            // Name and info panel
            JPanel textPanel = new JPanel(new GridLayout(2, 1, 0, 2));
            textPanel.setOpaque(false);

            nameLabel = new JLabel();
            nameLabel.setFont(new Font("Arial", Font.BOLD, 12));

            infoLabel = new JLabel();
            infoLabel.setFont(new Font("Arial", Font.PLAIN, 10));
            infoLabel.setForeground(Color.GRAY);

            textPanel.add(nameLabel);
            textPanel.add(infoLabel);

            // Icon panel for gamemode and op indicator
            iconPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
            iconPanel.setOpaque(false);

            // Ping label
            pingLabel = new JLabel();
            pingLabel.setFont(new Font("Arial", Font.PLAIN, 10));

            JPanel rightPanel = new JPanel(new BorderLayout());
            rightPanel.setOpaque(false);
            rightPanel.add(iconPanel, BorderLayout.NORTH);
            rightPanel.add(pingLabel, BorderLayout.SOUTH);

            add(headLabel, BorderLayout.WEST);
            add(textPanel, BorderLayout.CENTER);
            add(rightPanel, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends PlayerInfo> list, PlayerInfo player,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            // Set background
            if (isSelected) {
                setBackground(new Color(184, 207, 229));
            } else {
                setBackground(index % 2 == 0 ? Color.WHITE : new Color(250, 250, 250));
            }

            // Set player head
            String uuid = player.profile.id().toString();
            BufferedImage headImage = headCache.get(uuid);
            if (headImage != null) {
                headLabel.setIcon(new ImageIcon(headImage));
                headLabel.setOpaque(false);
            } else {
                // Show loading placeholder
                headLabel.setIcon(null);
                headLabel.setOpaque(true);
                headLabel.setBackground(new Color(200, 200, 200));
            }

            // Set name
            nameLabel.setText(player.profile.name());

            // Set info (gamemode)
            infoLabel.setText("Mode: " + player.gameMode.getTranslatableName());

            // Set ping with color coding
            pingLabel.setText(player.ping + " ms");
            if (player.ping < 50) {
                pingLabel.setForeground(new Color(40, 180, 99));
            } else if (player.ping < 100) {
                pingLabel.setForeground(new Color(241, 196, 15));
            } else {
                pingLabel.setForeground(new Color(231, 76, 60));
            }

            // Setup icons
            iconPanel.removeAll();

            // Op indicator
            if (player.isOp) {
                JLabel opLabel = new JLabel("OP");
                opLabel.setForeground(new Color(255, 215, 0));
                opLabel.setFont(new Font("Arial", Font.BOLD, 16));
                opLabel.setToolTipText("Operator");
                iconPanel.add(opLabel);
            }

            return this;
        }
    }

    // Sort modes
    private enum SortMode {
        NAME("Name", Comparator.comparing(p -> p.profile.name())),
        PING("Ping", Comparator.comparingInt(p -> p.ping)),
        GAMEMODE("Game Mode", Comparator.comparing(p -> p.gameMode.getTranslatableName().getString()));

        private final String displayName;
        private final Comparator<PlayerInfo> comparator;

        SortMode(String displayName, Comparator<PlayerInfo> comparator) {
            this.displayName = displayName;
            this.comparator = comparator;
        }

        String getDisplayName() {
            return displayName;
        }

        Comparator<PlayerInfo> getComparator() {
            return comparator;
        }

        SortMode next() {
            SortMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }
}