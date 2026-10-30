/*
 * Copyright (c) 2026 SuperSirvu
 *
 * Licensed under the MIT License.
 */

package net.supersirvu.gui;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.JScrollBar;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JList;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicCheckBoxMenuItemUI;
import javax.swing.plaf.basic.BasicMenuBarUI;
import javax.swing.plaf.basic.BasicMenuItemUI;
import javax.swing.plaf.basic.BasicMenuUI;
import javax.swing.plaf.basic.BasicPopupMenuUI;
import javax.swing.plaf.basic.BasicRadioButtonMenuItemUI;
import java.awt.AWTEvent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.text.Caret;
import javax.swing.text.JTextComponent;

/** Centralized appearance settings for the DedicatedPower Swing interface. */
public final class ThemeManager {
    public enum Theme {
        LIGHT,
        DARK
    }

    public interface ThemeListener {
        void themeChanged(ThemeManager themeManager);
    }

    private static final Path SETTINGS_FILE = Path.of("config", "dedicatedpower-gui.properties");
    private static final EnumMap<EnhancedLogPanel.LogLevel, Color> DEFAULT_LOG_COLORS = createDefaultLogColors();
    private static final ThemeManager INSTANCE = new ThemeManager();

    private final CopyOnWriteArrayList<WeakReference<ThemeListener>> listeners = new CopyOnWriteArrayList<>();
    private final EnumMap<EnhancedLogPanel.LogLevel, Color> logColors = new EnumMap<>(EnhancedLogPanel.LogLevel.class);
    private Theme theme = Theme.LIGHT;

    private ThemeManager() {
        load();
        applySwingDefaults();
        installWindowThemeListener();
    }

    private void installWindowThemeListener() {
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event instanceof WindowEvent windowEvent
                    && windowEvent.getID() == WindowEvent.WINDOW_OPENED) {
                Window window = windowEvent.getWindow();
                SwingUtilities.invokeLater(() -> applyTo(window));
            }
        }, AWTEvent.WINDOW_EVENT_MASK);
    }

    public static ThemeManager getInstance() {
        return INSTANCE;
    }

    public Theme getTheme() {
        return theme;
    }

    public boolean isDark() {
        return theme == Theme.DARK;
    }

    public Color getLogColor(EnhancedLogPanel.LogLevel level) {
        return logColors.get(level);
    }

    public Color getPanelBackground() {
        return isDark() ? new Color(43, 43, 43) : new Color(240, 240, 240);
    }

    public Color getSurfaceBackground() {
        return isDark() ? new Color(50, 50, 50) : new Color(250, 250, 250);
    }

    public Color getInputBackground() {
        return isDark() ? new Color(60, 60, 60) : Color.WHITE;
    }

    public Color getGraphBackground() {
        return isDark() ? new Color(50, 50, 50) : Color.WHITE;
    }

    public Color getForeground() {
        return isDark() ? new Color(235, 235, 235) : Color.BLACK;
    }

    public Color getMutedForeground() {
        return isDark() ? new Color(180, 180, 180) : Color.GRAY;
    }

    public Color getBorderColor() {
        return isDark() ? new Color(105, 105, 105) : new Color(150, 150, 150);
    }

    public Color getGridColor() {
        return isDark() ? new Color(80, 80, 80) : new Color(220, 220, 220);
    }

    public Color getSelectionBackground() {
        return isDark() ? new Color(70, 95, 125) : new Color(184, 207, 229);
    }

    public void addListener(ThemeListener listener) {
        listeners.add(new WeakReference<>(listener));
    }

    public void removeListener(ThemeListener listener) {
        listeners.removeIf(reference -> {
            ThemeListener current = reference.get();
            return current == null || current == listener;
        });
    }

    public void applySettings(Theme newTheme, Map<EnhancedLogPanel.LogLevel, Color> newLogColors) {
        theme = newTheme;
        logColors.clear();
        logColors.putAll(newLogColors);
        save();
        applySwingDefaults();
        notifyListeners();
    }

    /**
     * Applies the current theme to one window and to every other open window.
     *
     * <p>Reinstalling a Swing UI moves the caret of every text component to
     * position 0. Because this pass runs whenever a window opens (the command
     * completion popup is a window), it used to fire while the user was typing
     * in the console and the remaining characters were inserted in front of the
     * first one, turning {@code give} into {@code iveg}. The caret and the
     * selection of every text component are captured before the update and put
     * back immediately after it, on the same event dispatch turn.</p>
     */
    public void applyTo(Window window) {
        applySwingDefaults();

        List<Window> windows = windowsToUpdate(window);
        List<TextSelection> selections = new ArrayList<>();
        for (Window openWindow : windows) {
            captureSelections(openWindow, selections);
        }

        for (Window openWindow : windows) {
            SwingUtilities.updateComponentTreeUI(openWindow);
            applyComponentTheme(openWindow);
            openWindow.repaint();
        }

        restoreSelections(selections);
    }

    private static List<Window> windowsToUpdate(Window window) {
        List<Window> windows = new ArrayList<>();
        if (window != null) {
            windows.add(window);
        }
        for (Window openWindow : Window.getWindows()) {
            if (openWindow != window && openWindow.isDisplayable()) {
                windows.add(openWindow);
            }
        }
        return windows;
    }

    /** Remembers the caret and the selection of every text component in a tree. */
    private static void captureSelections(Component component, List<TextSelection> selections) {
        if (component instanceof JTextComponent textComponent) {
            Caret caret = textComponent.getCaret();
            if (caret != null) {
                selections.add(new TextSelection(textComponent, caret.getDot(), caret.getMark()));
            }
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                captureSelections(child, selections);
            }
        }
    }

    /** Puts the captured carets back, clamped to the current text length. */
    private static void restoreSelections(List<TextSelection> selections) {
        for (TextSelection selection : selections) {
            JTextComponent textComponent = selection.component();
            int length = textComponent.getDocument().getLength();
            int dot = Math.min(Math.max(selection.dot(), 0), length);
            int mark = Math.min(Math.max(selection.mark(), 0), length);

            if (textComponent.getCaretPosition() != dot) {
                textComponent.setCaretPosition(dot);
            }
            if (mark != dot && textComponent.getCaret().getMark() != mark) {
                textComponent.moveCaretPosition(mark);
            }
        }
    }

    private record TextSelection(JTextComponent component, int dot, int mark) {
    }

    private void applySwingDefaults() {
        Color panel = getPanelBackground();
        Color surface = getSurfaceBackground();
        Color input = getInputBackground();
        Color foreground = getForeground();
        Color border = getBorderColor();

        UIManager.put("Panel.background", panel);
        UIManager.put("Panel.foreground", foreground);
        UIManager.put("Label.foreground", foreground);
        UIManager.put("TextField.background", input);
        UIManager.put("TextField.foreground", foreground);
        UIManager.put("TextArea.background", input);
        UIManager.put("TextArea.foreground", foreground);
        UIManager.put("TextPane.background", input);
        UIManager.put("TextPane.foreground", foreground);
        UIManager.put("List.background", input);
        UIManager.put("List.foreground", foreground);
        UIManager.put("Table.background", input);
        UIManager.put("Table.foreground", foreground);
        UIManager.put("Viewport.background", input);
        UIManager.put("MenuBar.background", surface);
        UIManager.put("MenuBar.foreground", foreground);
        UIManager.put("Menu.background", surface);
        UIManager.put("Menu.foreground", foreground);
        UIManager.put("Menu.selectionBackground", getSelectionBackground());
        UIManager.put("Menu.selectionForeground", foreground);
        UIManager.put("MenuItem.background", surface);
        UIManager.put("MenuItem.foreground", foreground);
        UIManager.put("MenuItem.selectionBackground", getSelectionBackground());
        UIManager.put("MenuItem.selectionForeground", foreground);
        UIManager.put("RadioButtonMenuItem.background", surface);
        UIManager.put("RadioButtonMenuItem.foreground", foreground);
        UIManager.put("RadioButtonMenuItem.selectionBackground", getSelectionBackground());
        UIManager.put("RadioButtonMenuItem.selectionForeground", foreground);
        UIManager.put("CheckBoxMenuItem.background", surface);
        UIManager.put("CheckBoxMenuItem.foreground", foreground);
        UIManager.put("CheckBoxMenuItem.selectionBackground", getSelectionBackground());
        UIManager.put("CheckBoxMenuItem.selectionForeground", foreground);
        UIManager.put("PopupMenu.background", surface);
        UIManager.put("PopupMenu.foreground", foreground);
        UIManager.put("Button.background", surface);
        UIManager.put("Button.foreground", foreground);
        UIManager.put("Button.select", isDark() ? new Color(75, 95, 120) : new Color(210, 225, 245));
        UIManager.put("Button.focus", border);
        UIManager.put("CheckBox.background", panel);
        UIManager.put("CheckBox.foreground", foreground);
        UIManager.put("RadioButton.background", panel);
        UIManager.put("RadioButton.foreground", foreground);
        UIManager.put("Spinner.background", input);
        UIManager.put("Spinner.foreground", foreground);
        UIManager.put("ComboBox.background", input);
        UIManager.put("ComboBox.foreground", foreground);
        UIManager.put("List.selectionBackground", getSelectionBackground());
        UIManager.put("List.selectionForeground", foreground);
        UIManager.put("Table.selectionBackground", getSelectionBackground());
        UIManager.put("Table.selectionForeground", foreground);
        UIManager.put("ScrollBar.background", surface);
        UIManager.put("ScrollBar.foreground", foreground);
        UIManager.put("ScrollBar.thumb", isDark() ? new Color(105, 105, 105) : new Color(165, 165, 165));
        UIManager.put("ScrollBar.track", isDark() ? new Color(40, 40, 40) : new Color(222, 222, 222));
        UIManager.put("ScrollBar.thumbShadow", border);
        UIManager.put("ScrollBar.thumbHighlight", border);
        // String-created TitledBorders fall back to this UIManager key for their
        // title color; the Windows Look & Feel default is black, which is
        // unreadable on dark panels. Pointing it at the theme foreground keeps
        // section titles readable in both Light and Dark mode.
        UIManager.put("TitledBorder.titleColor", foreground);
        UIManager.put("OptionPane.background", panel);
        UIManager.put("OptionPane.messageForeground", foreground);
        UIManager.put("Separator.foreground", border);
        Color muted = getMutedForeground();
        // Menu-specific defaults: the Windows Look & Feel renders submenu arrows,
        // accelerators, disabled entries, and check/radio glyphs with native dark
        // colors that are unreadable on dark menus. Point them at theme colors.
        UIManager.put("Menu.arrowColor", foreground);
        UIManager.put("Menu.acceleratorForeground", foreground);
        UIManager.put("MenuItem.acceleratorForeground", foreground);
        UIManager.put("CheckBoxMenuItem.acceleratorForeground", foreground);
        UIManager.put("RadioButtonMenuItem.acceleratorForeground", foreground);
        UIManager.put("Menu.disabledForeground", muted);
        UIManager.put("MenuItem.disabledForeground", muted);
        UIManager.put("CheckBoxMenuItem.disabledForeground", muted);
        UIManager.put("RadioButtonMenuItem.disabledForeground", muted);
        UIManager.put("CheckBoxMenuItem.checkIcon", new ThemedCheckboxIcon());
        UIManager.put("RadioButtonMenuItem.checkIcon", new ThemedRadioIcon());
        UIManager.put("PopupMenu.border", BorderFactory.createLineBorder(border));
    }

    private void notifyListeners() {
        for (WeakReference<ThemeListener> reference : listeners) {
            ThemeListener listener = reference.get();
            if (listener == null) {
                listeners.remove(reference);
            } else {
                listener.themeChanged(this);
            }
        }
    }

    private void load() {
        setDefaultLogColors();
        if (!Files.isRegularFile(SETTINGS_FILE)) {
            return;
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(SETTINGS_FILE)) {
            properties.load(input);
            theme = Theme.valueOf(properties.getProperty("theme", Theme.LIGHT.name()).toUpperCase());
            for (EnhancedLogPanel.LogLevel level : EnhancedLogPanel.LogLevel.values()) {
                String value = properties.getProperty("log." + level.name().toLowerCase());
                if (value != null) {
                    Color parsed = Color.decode(value);
                    logColors.put(level, parsed);
                }
            }
        } catch (Exception ignored) {
            theme = Theme.LIGHT;
            setDefaultLogColors();
        }
    }

    public static Map<EnhancedLogPanel.LogLevel, Color> defaultLogColors() {
        return new EnumMap<>(DEFAULT_LOG_COLORS);
    }

    private static EnumMap<EnhancedLogPanel.LogLevel, Color> createDefaultLogColors() {
        EnumMap<EnhancedLogPanel.LogLevel, Color> colors = new EnumMap<>(EnhancedLogPanel.LogLevel.class);
        colors.put(EnhancedLogPanel.LogLevel.INFO, new Color(52, 152, 219));
        colors.put(EnhancedLogPanel.LogLevel.WARN, new Color(243, 156, 18));
        colors.put(EnhancedLogPanel.LogLevel.ERROR, new Color(231, 76, 60));
        colors.put(EnhancedLogPanel.LogLevel.DEBUG, new Color(149, 165, 166));
        colors.put(EnhancedLogPanel.LogLevel.CHAT, new Color(46, 204, 113));
        return colors;
    }

    private void setDefaultLogColors() {
        logColors.clear();
        logColors.putAll(DEFAULT_LOG_COLORS);
    }

    private void save() {
        Properties properties = new Properties();
        properties.setProperty("theme", theme.name().toLowerCase());
        for (Map.Entry<EnhancedLogPanel.LogLevel, Color> entry : logColors.entrySet()) {
            properties.setProperty("log." + entry.getKey().name().toLowerCase(),
                    String.format("#%02x%02x%02x", entry.getValue().getRed(), entry.getValue().getGreen(), entry.getValue().getBlue()));
        }

        try {
            Files.createDirectories(SETTINGS_FILE.getParent());
            try (OutputStream output = Files.newOutputStream(SETTINGS_FILE,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                properties.store(output, "DedicatedPower GUI appearance settings");
            }
        } catch (IOException ignored) {
            // Appearance changes remain active for this session even if persistence fails.
        }
    }

    /**
     * Applies explicit colors to already-created Swing controls. UIManager values
     * only affect newly-created controls for some Look & Feels, so this pass is
     * required when the user switches theme while the GUI is open.
     */
    public void applyComponentTheme(Component component) {
        Color panel = getPanelBackground();
        Color surface = getSurfaceBackground();
        Color input = getInputBackground();
        Color foreground = getForeground();
        Color border = getBorderColor();

        if (component instanceof JMenuBar menuBar) {
            menuBar.setOpaque(true);
            menuBar.setBackground(surface);
            menuBar.setForeground(foreground);
            // The Windows Look & Feel paints the native (light) menu bar skin over
            // any background color, which left the bar white in Dark mode. The
            // Basic renderer shows the themed background instead.
            menuBar.setUI(new BasicMenuBarUI());
        } else if (component instanceof JMenu menu) {
            menu.setOpaque(true);
            menu.setBackground(surface);
            menu.setForeground(foreground);
            menu.setUI(new BasicMenuUI());
            if (menu.getClientProperty("dedicatedpower.themeMenuListener") == null) {
                menu.putClientProperty("dedicatedpower.themeMenuListener", Boolean.TRUE);
                menu.addMenuListener(new MenuListener() {
                    @Override
                    public void menuSelected(MenuEvent event) {
                        applyComponentTheme(menu.getPopupMenu());
                    }

                    @Override
                    public void menuDeselected(MenuEvent event) {
                    }

                    @Override
                    public void menuCanceled(MenuEvent event) {
                    }
                });
            }
        } else if (component instanceof JRadioButtonMenuItem radioMenuItem) {
            radioMenuItem.setOpaque(true);
            radioMenuItem.setBackground(surface);
            radioMenuItem.setForeground(foreground);
            radioMenuItem.setUI(new BasicRadioButtonMenuItemUI());
        } else if (component instanceof JCheckBoxMenuItem checkMenuItem) {
            checkMenuItem.setOpaque(true);
            checkMenuItem.setBackground(surface);
            checkMenuItem.setForeground(foreground);
            checkMenuItem.setUI(new BasicCheckBoxMenuItemUI());
        } else if (component instanceof JMenuItem menuItem) {
            menuItem.setOpaque(true);
            menuItem.setBackground(surface);
            menuItem.setForeground(foreground);
            menuItem.setUI(new BasicMenuItemUI());
        } else if (component instanceof JPopupMenu popupMenu) {
            popupMenu.setOpaque(true);
            popupMenu.setBackground(surface);
            popupMenu.setForeground(foreground);
            popupMenu.setUI(new BasicPopupMenuUI());
        } else if (component instanceof AbstractButton button) {
            boolean palettePreview = Boolean.TRUE.equals(button.getClientProperty("dedicatedpower.palettePreview"));
            if (palettePreview) {
                Object paletteColor = button.getClientProperty("dedicatedpower.paletteColor");
                if (paletteColor instanceof Color color) {
                    // updateComponentTreeUI may reset a button to the Look & Feel
                    // default. Restore the user-selected log color explicitly.
                    button.setBackground(color);
                }
                // The native Windows button skin paints a light gradient with a
                // blue border over the background, which left the palette buttons
                // looking light in Dark mode. ThemedButtonUI renders the selected
                // log color instead.
                button.setContentAreaFilled(false);
                button.setBorderPainted(false);
                button.setOpaque(false);
                button.setUI(new ThemedButtonUI(border));
            } else {
                button.setBackground(surface);
                button.setOpaque(true);
                if (button instanceof JButton normalButton) {
                    // Disable native content painting and draw all button states in
                    // ThemedButtonUI. This avoids Windows Look & Feel colors leaking
                    // into either light or dark mode.
                    normalButton.setContentAreaFilled(false);
                    normalButton.setBorderPainted(false);
                    normalButton.setOpaque(false);
                    normalButton.setUI(new ThemedButtonUI(border));
                }
            }
            // Replace the native checkbox/radio glyphs with theme-aware icons so
            // the box and its mark stay readable in both light and dark mode.
            if (button instanceof JCheckBox && !(button.getIcon() instanceof ThemedCheckboxIcon)) {
                button.setIcon(new ThemedCheckboxIcon());
            } else if (button instanceof JRadioButton && !(button.getIcon() instanceof ThemedRadioIcon)) {
                button.setIcon(new ThemedRadioIcon());
            }
            button.setForeground(foreground);
        } else if (component instanceof JSpinner spinner) {
            spinner.setBackground(input);
            spinner.setForeground(foreground);
            setBackgroundRecursively(spinner, input, foreground);
        } else if (component instanceof JTextField || component instanceof JTextArea || component instanceof JList<?> || component instanceof JTable) {
            component.setBackground(input);
            component.setForeground(foreground);
        } else if (component instanceof JScrollBar scrollBar) {
            scrollBar.setBackground(surface);
            scrollBar.setForeground(foreground);
        } else if (component instanceof JPopupMenu) {
            component.setBackground(surface);
            component.setForeground(foreground);
        } else if (component instanceof JComponent swingComponent) {
            swingComponent.setBackground(panel);
            swingComponent.setForeground(foreground);
        }

        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                applyComponentTheme(child);
            }
        }
    }

    private static final class ThemedButtonUI extends BasicButtonUI {
        private final Color borderColor;

        private ThemedButtonUI(Color borderColor) {
            this.borderColor = borderColor;
        }

        @Override
        protected void paintButtonPressed(java.awt.Graphics graphics, javax.swing.AbstractButton button) {
            // The themed paint method below already draws the pressed background.
            // Prevent BasicButtonUI from painting its own native/select color over it.
        }

        @Override
        public void paint(java.awt.Graphics graphics, JComponent component) {
            JButton button = (JButton) component;
            java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
            javax.swing.ButtonModel model = button.getModel();
            Color fill = button.getBackground();
            if (model.isPressed()) {
                fill = fill.darker();
            } else if (model.isRollover()) {
                fill = fill.brighter();
            }
            g.setColor(fill);
            g.fillRect(0, 0, button.getWidth(), button.getHeight());
            g.setColor(borderColor);
            g.drawRect(0, 0, button.getWidth() - 1, button.getHeight() - 1);
            if (button.isFocusPainted() && button.hasFocus()) {
                g.setColor(borderColor.brighter());
                g.drawRect(2, 2, button.getWidth() - 5, button.getHeight() - 5);
            }
            g.dispose();
            // Content-area painting is disabled by applyComponentTheme, so the
            // delegate paints text/icons without overwriting the themed background.
            super.paint(graphics, component);
        }
    }

    /**
     * Theme-aware checkbox glyph that replaces the native Look & Feel icon. The
     * box, border, and check mark are painted with the active theme colors and
     * react to the button's pressed, rollover, selected, and disabled states.
     */
    private static final class ThemedCheckboxIcon implements Icon {
        private static final int SIZE = 16;

        @Override
        public int getIconWidth() {
            return SIZE;
        }

        @Override
        public int getIconHeight() {
            return SIZE;
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            AbstractButton button = (AbstractButton) component;
            ButtonModel model = button.getModel();
            ThemeManager theme = ThemeManager.getInstance();

            Color fill = theme.getInputBackground();
            Color border = theme.getBorderColor();
            Color mark = theme.getForeground();

            if (!model.isEnabled()) {
                fill = theme.getPanelBackground();
                border = theme.getMutedForeground();
                mark = theme.getMutedForeground();
            } else if (model.isPressed() && model.isArmed()) {
                fill = theme.getSurfaceBackground();
                border = border.darker();
            } else if (model.isRollover()) {
                border = border.brighter();
            }

            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (button.isFocusPainted() && button.hasFocus()) {
                    g.setColor(theme.getSelectionBackground());
                    g.fillRoundRect(x - 2, y - 2, SIZE + 3, SIZE + 3, 6, 6);
                }
                g.setColor(fill);
                g.fillRoundRect(x, y, SIZE - 1, SIZE - 1, 4, 4);
                g.setColor(border);
                g.drawRoundRect(x, y, SIZE - 1, SIZE - 1, 4, 4);
                if (model.isSelected()) {
                    g.setColor(mark);
                    g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.drawPolyline(new int[]{x + 3, x + 7, x + 13}, new int[]{y + 8, y + 12, y + 4}, 3);
                }
            } finally {
                g.dispose();
            }
        }
    }

    /**
     * Theme-aware radio button glyph with the same state and theme handling as
     * the checkbox icon above.
     */
    private static final class ThemedRadioIcon implements Icon {
        private static final int SIZE = 16;

        @Override
        public int getIconWidth() {
            return SIZE;
        }

        @Override
        public int getIconHeight() {
            return SIZE;
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            AbstractButton button = (AbstractButton) component;
            ButtonModel model = button.getModel();
            ThemeManager theme = ThemeManager.getInstance();

            Color fill = theme.getInputBackground();
            Color border = theme.getBorderColor();
            Color dot = theme.getForeground();

            if (!model.isEnabled()) {
                fill = theme.getPanelBackground();
                border = theme.getMutedForeground();
                dot = theme.getMutedForeground();
            } else if (model.isPressed() && model.isArmed()) {
                fill = theme.getSurfaceBackground();
                border = border.darker();
            } else if (model.isRollover()) {
                border = border.brighter();
            }

            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (button.isFocusPainted() && button.hasFocus()) {
                    g.setColor(theme.getSelectionBackground());
                    g.fillOval(x - 2, y - 2, SIZE + 3, SIZE + 3);
                }
                g.setColor(fill);
                g.fillOval(x, y, SIZE - 1, SIZE - 1);
                g.setColor(border);
                g.drawOval(x, y, SIZE - 1, SIZE - 1);
                if (model.isSelected()) {
                    g.setColor(dot);
                    g.fillOval(x + SIZE / 2 - 4, y + SIZE / 2 - 4, 8, 8);
                }
            } finally {
                g.dispose();
            }
        }
    }

    public static void setBackgroundRecursively(Component component, Color background, Color foreground) {
        if (component instanceof JComponent swingComponent) {
            swingComponent.setBackground(background);
            swingComponent.setForeground(foreground);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                setBackgroundRecursively(child, background, foreground);
            }
        }
    }
}
