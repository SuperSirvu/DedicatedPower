/*
 * Copyright (c) 2026 SuperSirvu
 *
 * Licensed under the MIT License.
 */

package net.supersirvu.gui;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftProfileTextures;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.world.level.GameType;
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
import java.util.concurrent.ConcurrentHashMap;

public class EnhancedPlayerListGui extends JPanel implements ThemeManager.ThemeListener {
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
    private PlayerCellRenderer playerCellRenderer;

    public EnhancedPlayerListGui(MinecraftServer server) {
        this.server = server;
        ThemeManager.getInstance().addListener(this);
        this.listModel = new DefaultListModel<>();
        this.playerList = new JList<>(listModel);
        this.headCache = new ConcurrentHashMap<>();

        this.setLayout(new BorderLayout());
        this.setBackground(ThemeManager.getInstance().getPanelBackground());

        // Setup player list
        setupPlayerList();

        // Create header panel with controls
        JPanel headerPanel = createHeaderPanel();
        this.add(headerPanel, BorderLayout.NORTH);

        // Add scroll pane with player list
        JScrollPane scrollPane = new JScrollPane(playerList);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        this.add(scrollPane, BorderLayout.CENTER);

        // Refresh the player list periodically (replaces the removed server GUI tick hook)
        Timer refreshTimer = new Timer(1000, e -> tick());
        refreshTimer.start();

        // Start async head loading
        startHeadLoading();
    }

    private void startHeadLoading() {
        // Periodically check and load missing player heads
        Timer headLoadTimer = new Timer(2000, e -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
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
                        server.services().sessionService().getTextures(profile);

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
        playerCellRenderer = new PlayerCellRenderer();
        playerList.setCellRenderer(playerCellRenderer);
        playerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ThemeManager themeManager = ThemeManager.getInstance();
        playerList.setBackground(themeManager.getInputBackground());
        playerList.setForeground(themeManager.getForeground());
        playerList.setSelectionBackground(themeManager.getSelectionBackground());
        playerList.setSelectionForeground(themeManager.getForeground());
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
        panel.setBackground(ThemeManager.getInstance().getSurfaceBackground());
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
        controlPanel.setBackground(ThemeManager.getInstance().getSurfaceBackground());
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

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String name = player.getGameProfile().name();

            // Apply search filter
            if (!searchFilter.isEmpty() && !name.toLowerCase().contains(searchFilter.toLowerCase())) {
                continue;
            }

            NameAndId nameAndId = new NameAndId(player.getGameProfile());
            PlayerInfo info = new PlayerInfo(
                    player.getGameProfile(),
                    nameAndId,
                    player.connection.latency(),
                    player.gameMode(),
                    server.getPlayerList().isOp(nameAndId),
                    player.getHealth(),
                    player.getFoodData().getFoodLevel()
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
                ServerPlayer serverPlayer = server.getPlayerList().getPlayer(player.profile.id());
                serverPlayer.connection.disconnect(Component.literal(reason));
            }
        });

        JMenuItem banItem = new JMenuItem("Ban Player");
        banItem.addActionListener(e -> {
            String reason = JOptionPane.showInputDialog(this, "Ban reason:", "Ban Player", JOptionPane.QUESTION_MESSAGE);
            if (reason != null) {
                UserBanList bannedPlayerList = server.getPlayerList().getBans();
                if (!bannedPlayerList.isBanned(player.nameAndId)) {
                    UserBanListEntry bannedPlayerEntry = new UserBanListEntry(player.nameAndId, null, "SERVER", null, reason);
                    bannedPlayerList.add(bannedPlayerEntry);
                    ServerPlayer serverPlayerEntity = server.getPlayerList().getPlayer(player.profile.id());
                    if (serverPlayerEntity != null) {
                        serverPlayerEntity.connection.disconnect(Component.translatable("multiplayer.disconnect.banned"));
                    }
                }
            }
        });

        JMenuItem messageItem = new JMenuItem("Send Message");
        messageItem.addActionListener(e -> {
            String message = JOptionPane.showInputDialog(this, "Message:", "Send Message", JOptionPane.QUESTION_MESSAGE);
            if (message != null) {
                // Run on the server thread so the GUI is never blocked
                server.execute(() -> server.getCommands().performPrefixedCommand(
                        server.createCommandSourceStack(),
                        "tell " + player.profile.name() + " SERVER: " + message
                ));
            }
        });

        JMenuItem opItem = new JMenuItem(player.isOp ? "Deop Player" : "Op Player");
        opItem.addActionListener(e -> {
            if (player.isOp) {
                server.getPlayerList().deop(player.nameAndId);
            } else {
                server.getPlayerList().op(player.nameAndId);
            }
        });

        menu.add(kickItem);
        menu.add(banItem);
        menu.add(messageItem);
        menu.addSeparator();
        menu.add(opItem);

        ThemeManager.getInstance().applyComponentTheme(menu);
        menu.show(playerList, x, y);
    }

    private void showPlayerDetails(PlayerInfo player) {
        String details = String.format(
                "Player: %s\n\nPing: %d ms\nGame Mode: %s\nOperator: %s\nHealth: %.1f\nHunger: %d",
                player.profile.name(),
                player.ping,
                player.gameMode.getName(),
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
        for (String bannedPlayer : server.getPlayerList().getBans().getUserList()) {
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
                    UserBanList bannedPlayerList = server.getPlayerList().getBans();
                    NameAndId nameAndId = NameAndId.createOffline(selected);
                    if (bannedPlayerList.isBanned(nameAndId)) {
                        bannedPlayerList.remove(nameAndId);
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
        final NameAndId nameAndId;
        final int ping;
        final GameType gameMode;
        final boolean isOp;
        final float health;
        final int hunger;

        PlayerInfo(GameProfile profile, NameAndId nameAndId, int ping, GameType gameMode, boolean isOp, float health, int hunger) {
            this.profile = profile;
            this.nameAndId = nameAndId;
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
            infoLabel.setForeground(ThemeManager.getInstance().getMutedForeground());

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
        public java.awt.Component getListCellRendererComponent(JList<? extends PlayerInfo> list, PlayerInfo player,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            ThemeManager themeManager = ThemeManager.getInstance();
            Color cellForeground = isSelected ? list.getSelectionForeground() : themeManager.getForeground();

            // Set background and foreground explicitly. A Swing cell renderer is
            // reused outside the normal component hierarchy, so ThemeManager's
            // recursive component pass cannot update these labels for us.
            if (isSelected) {
                setBackground(list.getSelectionBackground());
            } else {
                setBackground(index % 2 == 0 ? themeManager.getInputBackground() : themeManager.getSurfaceBackground());
            }
            nameLabel.setForeground(cellForeground);
            infoLabel.setForeground(isSelected ? cellForeground : themeManager.getMutedForeground());

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
            infoLabel.setText("Mode: " + player.gameMode.getName());

            // Set ping with color coding. Use the selection foreground for a
            // selected row so status colors never become unreadable on the
            // themed selection background.
            pingLabel.setText(player.ping + " ms");
            if (isSelected) {
                pingLabel.setForeground(cellForeground);
            } else if (player.ping < 50) {
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

    @Override
    public void themeChanged(ThemeManager themeManager) {
        SwingUtilities.invokeLater(() -> {
            setBackground(themeManager.getPanelBackground());
            playerList.setBackground(themeManager.getInputBackground());
            playerList.setForeground(themeManager.getForeground());
            playerList.setSelectionBackground(themeManager.getSelectionBackground());
            playerList.setSelectionForeground(themeManager.getForeground());
            themeManager.applyComponentTheme(this);
            playerList.setBackground(themeManager.getInputBackground());
            playerList.setSelectionBackground(themeManager.getSelectionBackground());
            playerList.setSelectionForeground(themeManager.getForeground());
            playerCellRenderer = new PlayerCellRenderer();
            playerList.setCellRenderer(playerCellRenderer);
            playerList.repaint();
            repaint();
        });
    }

    // Sort modes
    private enum SortMode {
        NAME("Name", Comparator.comparing(p -> p.profile.name())),
        PING("Ping", Comparator.comparingInt(p -> p.ping)),
        GAMEMODE("Game Mode", Comparator.comparing(p -> p.gameMode.getName()));

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
