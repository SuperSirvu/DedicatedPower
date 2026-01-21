package net.supersirvu.gui;

import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TimeHelper;
import net.minecraft.util.Util;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class EnhancedPlayerStatsGui extends JComponent {
    private static final DecimalFormat AVG_TICK_FORMAT = Util.make(
            new DecimalFormat("########0.000"),
            decimalFormat -> decimalFormat.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.ROOT))
    );
    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("#,###");

    // Graph data
    private final int[] memoryUsePercentage = new int[256];
    private final double[] tickTimeHistory = new double[256];
    private int dataPosition;

    // Stats lines
    private final String[] lines = new String[11];
    private final MinecraftServer server;
    private final Timer timer;

    // Colors
    private static final Color BG_COLOR = new Color(255, 255, 255);
    private static final Color TEXT_COLOR = new Color(0, 0, 0);
    private static final Color MEMORY_GOOD = new Color(40, 180, 99);
    private static final Color MEMORY_WARNING = new Color(241, 196, 15);
    private static final Color MEMORY_CRITICAL = new Color(231, 76, 60);
    private static final Color TPS_EXCELLENT = new Color(46, 204, 113);
    private static final Color TPS_GOOD = new Color(52, 152, 219);
    private static final Color TPS_WARNING = new Color(230, 126, 34);
    private static final Color TPS_POOR = new Color(192, 57, 43);

    // Tooltip
    private int mouseX = -1;
    private int mouseY = -1;

    // Uptime tracking
    private long startTime;

    public EnhancedPlayerStatsGui(MinecraftServer server) {
        this.server = server;
        this.startTime = System.currentTimeMillis();
        this.setPreferredSize(new Dimension(456, 246));
        this.setMinimumSize(new Dimension(400, 200));
        this.timer = new Timer(500, event -> this.update());
        this.timer.start();
        this.setBackground(BG_COLOR);

        // Add mouse motion listener for tooltips
        this.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                mouseX = e.getX();
                mouseY = e.getY();
                repaint();
            }
        });
    }

    private void update() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        long freeMemory = runtime.freeMemory();

        // Calculate stats
        double avgTickMs = (double)this.server.getAverageNanosPerTick() / TimeHelper.MILLI_IN_NANOS;
        double tps = Math.min(20.0, 1000.0 / avgTickMs);

        int entityCount = 0;
        int chunkCount = 0;
        for (ServerWorld world : this.server.getWorlds()) {
            for (Entity ignored : world.iterateEntities()) {
                entityCount++;
            }
            chunkCount += world.getChunkManager().getLoadedChunkCount();
        }

        // Update text lines
        this.lines[0] = "Memory: " + (usedMemory / 1024L / 1024L) + " MB / " +
                (maxMemory / 1024L / 1024L) + " MB (" +
                (freeMemory * 100L / maxMemory) + "% free)";
        this.lines[1] = "Avg Tick: " + AVG_TICK_FORMAT.format(avgTickMs) + " ms";
        this.lines[2] = "TPS: " + AVG_TICK_FORMAT.format(tps) + " / 20.0";
        this.lines[3] = "Entities: " + NUMBER_FORMAT.format(entityCount);
        this.lines[4] = "Chunks: " + NUMBER_FORMAT.format(chunkCount);
        this.lines[5] = "Uptime: " + formatUptime(System.currentTimeMillis() - startTime);

        // Update graph data
        this.memoryUsePercentage[this.dataPosition & 0xFF] = (int)(usedMemory * 100L / maxMemory);
        this.tickTimeHistory[this.dataPosition & 0xFF] = avgTickMs;
        this.dataPosition++;

        this.repaint();
    }

    private String formatUptime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return days + "d " + (hours % 24) + "h " + (minutes % 60) + "m";
        } else if (hours > 0) {
            return hours + "h " + (minutes % 60) + "m " + (seconds % 60) + "s";
        } else if (minutes > 0) {
            return minutes + "m " + (seconds % 60) + "s";
        } else {
            return seconds + "s";
        }
    }

    @Override
    public void paint(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;

        // Enable anti-aliasing
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();

        // Background
        g2d.setColor(BG_COLOR);
        g2d.fillRect(0, 0, width, height);

        // Calculate graph dimensions
        int graphHeight = (height - 140) / 2;
        int graphY1 = 20;
        int graphY2 = graphY1 + graphHeight + 20;
        int statsY = graphY2 + graphHeight + 20;

        // Draw memory graph
        drawGraph(g2d, "Memory Usage (%)", graphY1, graphHeight, this.memoryUsePercentage, 0, 100, true);

        // Draw TPS/Tick time graph
        drawGraph(g2d, "Tick Time (ms)", graphY2, graphHeight, this.tickTimeHistory, 0, 50, false);

        // Draw stats text
        drawStats(g2d, statsY);

        // Draw tooltip if mouse is over a graph
        if (mouseX >= 0 && mouseY >= 0) {
            if (mouseY >= graphY1 && mouseY <= graphY1 + graphHeight) {
                drawTooltip(g2d, mouseX, mouseY, true);
            } else if (mouseY >= graphY2 && mouseY <= graphY2 + graphHeight) {
                drawTooltip(g2d, mouseX, mouseY, false);
            }
        }
    }

    private void drawGraph(Graphics2D g2d, String title, int y, int height, double[] data, double minVal, double maxVal, boolean isMemory) {
        int width = getWidth();
        int graphWidth = width - 60; // Made smaller to fit scale labels
        int graphX = 10;

        // Draw grid background
        g2d.setColor(Color.WHITE);
        g2d.fillRect(graphX, y, graphWidth, height);

        // Draw grid lines
        g2d.setColor(new Color(220, 220, 220));
        for (int i = 0; i <= 5; i++) {
            int gridY = y + (height * i / 5);
            g2d.drawLine(graphX, gridY, graphX + graphWidth, gridY);
        }
        for (int i = 0; i <= 8; i++) {
            int gridX = graphX + (graphWidth * i / 8);
            g2d.drawLine(gridX, y, gridX, y + height);
        }

        // Draw title
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, 11));
        g2d.drawString(title, graphX + 5, y - 2);

        // Draw scale labels
        g2d.setFont(new Font("Arial", Font.PLAIN, 9));
        g2d.setColor(Color.BLACK);
        g2d.drawString(String.valueOf((int)maxVal), graphX + graphWidth + 5, y + 10);
        g2d.drawString(String.valueOf((int)(maxVal / 2)), graphX + graphWidth + 5, y + height / 2 + 5);
        g2d.drawString(String.valueOf((int)minVal), graphX + graphWidth + 5, y + height);

        // Draw graph data (from right to left)
        for (int i = 0; i < graphWidth && i < 256; i++) {
            int dataIndex = (this.dataPosition - 1 - i) & 0xFF; // Start from most recent
            double value = data[dataIndex];

            int barHeight = (int)((value / maxVal) * height);
            barHeight = Math.min(barHeight, height);

            Color barColor = isMemory ? getMemoryColor((int)value) : getTickColor(value);
            g2d.setColor(barColor);
            // Draw from right to left
            g2d.fillRect(graphX + graphWidth - 1 - i, y + height - barHeight, 1, barHeight);
        }

        // Draw border
        g2d.setColor(new Color(100, 100, 100));
        g2d.drawRect(graphX, y, graphWidth, height);
    }

    private void drawGraph(Graphics2D g2d, String title, int y, int height, int[] data, double minVal, double maxVal, boolean isMemory) {
        // Overload for int arrays (memory percentage)
        double[] doubleData = new double[data.length];
        for (int i = 0; i < data.length; i++) {
            doubleData[i] = data[i];
        }
        drawGraph(g2d, title, y, height, doubleData, minVal, maxVal, isMemory);
    }

    private void drawStats(Graphics2D g2d, int y) {
        g2d.setColor(TEXT_COLOR);
        g2d.setFont(new Font("Monospaced", Font.PLAIN, 11));

        for (int i = 0; i < this.lines.length; i++) {
            String line = this.lines[i];
            if (line != null) {
                g2d.drawString(line, 15, y + i * 14);
            }
        }
    }

    private void drawTooltip(Graphics2D g2d, int mx, int my, boolean isMemory) {
        int graphWidth = getWidth() - 60; // Match the new graph width
        int graphX = 10;

        if (mx < graphX || mx > graphX + graphWidth) return;

        int relativeX = mx - graphX;
        // Calculate index from right to left
        int dataIndex = (this.dataPosition - 1 - (graphWidth - 1 - relativeX)) & 0xFF;

        String tooltipText;
        if (isMemory) {
            int memPercent = this.memoryUsePercentage[dataIndex];
            tooltipText = "Memory: " + memPercent + "%";
        } else {
            double tickTime = this.tickTimeHistory[dataIndex];
            double tps = Math.min(20.0, 1000.0 / tickTime);
            tooltipText = String.format("Tick: %.2f ms | TPS: %.2f", tickTime, tps);
        }

        // Tooltip background
        FontMetrics fm = g2d.getFontMetrics();
        int tooltipWidth = fm.stringWidth(tooltipText) + 10;
        int tooltipHeight = fm.getHeight() + 6;

        int tooltipX = mx + 10;
        int tooltipY = my - 25;

        // Keep tooltip in bounds
        if (tooltipX + tooltipWidth > getWidth()) {
            tooltipX = mx - tooltipWidth - 10;
        }

        g2d.setColor(new Color(255, 255, 255, 200));
        g2d.fillRoundRect(tooltipX, tooltipY, tooltipWidth, tooltipHeight, 5, 5);
        g2d.setColor(TEXT_COLOR);
        g2d.drawRoundRect(tooltipX, tooltipY, tooltipWidth, tooltipHeight, 5, 5);

        g2d.setFont(new Font("Arial", Font.PLAIN, 10));
        g2d.drawString(tooltipText, tooltipX + 5, tooltipY + tooltipHeight - 6);
    }

    private Color getMemoryColor(int percentage) {
        if (percentage < 60) {
            return MEMORY_GOOD;
        } else if (percentage < 80) {
            return MEMORY_WARNING;
        } else {
            return MEMORY_CRITICAL;
        }
    }

    private Color getTickColor(double tickMs) {
        if (tickMs < 25) {
            return TPS_EXCELLENT;
        } else if (tickMs < 40) {
            return TPS_GOOD;
        } else if (tickMs < 50) {
            return TPS_WARNING;
        } else {
            return TPS_POOR;
        }
    }

    public void stop() {
        this.timer.stop();
    }
}