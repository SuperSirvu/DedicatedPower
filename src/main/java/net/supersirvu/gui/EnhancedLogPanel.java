/*
 * Copyright (c) 2026 SuperSirvu
 *
 * Licensed under the MIT License.
 */

package net.supersirvu.gui;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dedicated.DedicatedServer;

import javax.swing.*;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.datatransfer.StringSelection;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class EnhancedLogPanel extends JPanel implements ThemeManager.ThemeListener {
    private final DedicatedServer server;

    // Components
    private final JTextPane logTextPane;
    private final JTextField commandInput;
    private final JScrollPane logScrollPane;
    private final StyledDocument logDocument;
    private boolean followLogBottom = true;
    private boolean updatingLogDisplay;
    private boolean autoScrollPending;
    private boolean programmaticScroll;
    private long scrollGeneration;

    // Command suggestion
    private JWindow suggestionWindow;
    private DefaultListModel<SuggestionItem> suggestionModel;
    private JList<SuggestionItem> suggestionList;
    private CompletableFuture<Suggestions> pendingSuggestions;
    private long suggestionGeneration;
    private boolean suggestionsSuppressed;

    // Command history
    private final List<String> commandHistory = new ArrayList<>();
    private int historyIndex = -1;

    // Filtering and display
    private final Set<LogLevel> enabledLevels = new HashSet<>(Arrays.asList(LogLevel.values()));
    private String searchQuery = "";

    // Store all log entries for filtering
    private final List<LogEntry> allLogEntries = new ArrayList<>();

    // Styles
    private final Map<LogLevel, Style> logStyles = new HashMap<>();
    private Style commandStyle;
    private Style argumentStyle;
    private Style errorStyle;

    // Control panel
    private JPanel controlPanel;
    private JTextField searchField;

    public EnhancedLogPanel(DedicatedServer server) {
        this.server = server;

        ThemeManager themeManager = ThemeManager.getInstance();
        themeManager.addListener(this);

        setLayout(new BorderLayout());
        setBackground(themeManager.getPanelBackground());

        // Create log display FIRST (before initializing styles)
        logTextPane = new JTextPane();
        logTextPane.setEditable(false);
        logTextPane.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logTextPane.setBackground(themeManager.getInputBackground());
        logTextPane.setForeground(themeManager.getForeground());
        logDocument = logTextPane.getStyledDocument();
        installLogContextMenu();

        // NOW initialize styles (after logTextPane is created)
        initializeStyles();

        logScrollPane = new JScrollPane(logTextPane);
        logScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        JScrollBar verticalScrollBar = logScrollPane.getVerticalScrollBar();
        verticalScrollBar.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                if (!programmaticScroll) {
                    autoScrollPending = false;
                    scrollGeneration++;
                    updateFollowLogBottom();
                }
            }
        });
        logScrollPane.addMouseWheelListener(event -> {
            if (!programmaticScroll) {
                autoScrollPending = false;
                scrollGeneration++;
                SwingUtilities.invokeLater(EnhancedLogPanel.this::updateFollowLogBottom);
            }
        });
        verticalScrollBar.addAdjustmentListener(event -> {
            if (!programmaticScroll && !autoScrollPending) {
                updateFollowLogBottom();
            }
        });

        // Create command input
        commandInput = new JTextField();
        commandInput.setFont(new Font("Monospaced", Font.PLAIN, 12));
        commandInput.setBackground(themeManager.getInputBackground());
        commandInput.setForeground(themeManager.getForeground());
        commandInput.addActionListener(e -> executeCommand());
        commandInput.addKeyListener(new CommandInputKeyListener());
        commandInput.addCaretListener(new CommandCaretListener());
        commandInput.getDocument().addDocumentListener(new CommandDocumentListener());

        // Disable TAB focus traversal so TAB can be used for completion
        commandInput.setFocusTraversalKeysEnabled(false);

        // Create control panel
        createControlPanel();

        // Layout
        add(controlPanel, BorderLayout.NORTH);
        add(logScrollPane, BorderLayout.CENTER);
        add(commandInput, BorderLayout.SOUTH);

        // Initialize suggestion window
        initializeSuggestionWindow();
    }

    private void installLogContextMenu() {
        JPopupMenu contextMenu = new JPopupMenu();
        JMenuItem copyItem = new JMenuItem("Copy");
        JMenuItem searchItem = new JMenuItem("Search in the web");

        copyItem.addActionListener(event -> copySelectedLogText());
        searchItem.addActionListener(event -> searchSelectedLogText());
        contextMenu.add(copyItem);
        contextMenu.add(searchItem);
        contextMenu.putClientProperty("dedicatedpower.copyItem", copyItem);
        contextMenu.putClientProperty("dedicatedpower.searchItem", searchItem);

        logTextPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                showLogContextMenu(event, contextMenu);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                showLogContextMenu(event, contextMenu);
            }
        });
    }

    private void showLogContextMenu(MouseEvent event, JPopupMenu contextMenu) {
        if (!event.isPopupTrigger()) {
            return;
        }

        String selectedText = logTextPane.getSelectedText();
        boolean hasSelection = selectedText != null && !selectedText.isBlank();
        ((JMenuItem) contextMenu.getClientProperty("dedicatedpower.copyItem")).setEnabled(hasSelection);
        ((JMenuItem) contextMenu.getClientProperty("dedicatedpower.searchItem")).setEnabled(hasSelection);

        ThemeManager.getInstance().applyComponentTheme(contextMenu);
        contextMenu.show(logTextPane, event.getX(), event.getY());
    }

    private String getSelectedLogText() {
        String selectedText = logTextPane.getSelectedText();
        return selectedText == null ? "" : selectedText;
    }

    private void copySelectedLogText() {
        String selectedText = getSelectedLogText();
        if (selectedText.isEmpty()) {
            return;
        }
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(selectedText), null);
        } catch (IllegalStateException error) {
            JOptionPane.showMessageDialog(this,
                    "The system clipboard is currently unavailable.",
                    "Copy", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void searchSelectedLogText() {
        String selectedText = getSelectedLogText().trim();
        if (selectedText.isEmpty()) {
            return;
        }

        if (!Desktop.isDesktopSupported()) {
            JOptionPane.showMessageDialog(this,
                    "Opening a web browser is not supported on this system.",
                    "Search in the web", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            Desktop desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.BROWSE)) {
                throw new UnsupportedOperationException("Browser opening is not supported");
            }
            String query = URLEncoder.encode(selectedText, StandardCharsets.UTF_8);
            desktop.browse(URI.create("https://www.google.com/search?q=" + query));
        } catch (Exception error) {
            JOptionPane.showMessageDialog(this,
                    "Could not open the web browser: " + error.getMessage(),
                    "Search in the web", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void processLogMessage(String message) {
        // Parse log level from message
        LogLevel level = detectLogLevel(message);
        appendLog(message, level);
    }

    private LogLevel detectLogLevel(String message) {
        if (message.contains("[ERROR]") || message.contains("ERROR:")) {
            return LogLevel.ERROR;
        } else if (message.contains("[WARN]") || message.contains("WARN:")) {
            return LogLevel.WARN;
        } else if (message.contains("[DEBUG]") || message.contains("DEBUG:")) {
            return LogLevel.DEBUG;
        } else if (message.contains("<") && message.contains(">")) {
            // Chat messages typically have <PlayerName> format
            return LogLevel.CHAT;
        } else {
            return LogLevel.INFO;
        }
    }

    private void initializeStyles() {
        // Log level styles
        ThemeManager themeManager = ThemeManager.getInstance();
        logStyles.put(LogLevel.INFO, createStyle(themeManager.getLogColor(LogLevel.INFO), false));
        logStyles.put(LogLevel.WARN, createStyle(themeManager.getLogColor(LogLevel.WARN), false));
        logStyles.put(LogLevel.ERROR, createStyle(themeManager.getLogColor(LogLevel.ERROR), true));
        logStyles.put(LogLevel.DEBUG, createStyle(themeManager.getLogColor(LogLevel.DEBUG), false));
        logStyles.put(LogLevel.CHAT, createStyle(themeManager.getLogColor(LogLevel.CHAT), false));

        // Command syntax styles
        commandStyle = createStyle(themeManager.getLogColor(LogLevel.INFO), true);
        argumentStyle = createStyle(new Color(155, 89, 182), false);
        errorStyle = createStyle(themeManager.getLogColor(LogLevel.ERROR), false);
    }

    private Style createStyle(Color color, boolean bold) {
        Style style = logTextPane.addStyle(null, null);
        StyleConstants.setForeground(style, color);
        StyleConstants.setBold(style, bold);
        return style;
    }

    private void createControlPanel() {
        controlPanel = new JPanel(new GridBagLayout());
        controlPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        controlPanel.setBackground(ThemeManager.getInstance().getSurfaceBackground());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 5, 0, 5);
        gbc.gridy = 0;

        // Search label
        gbc.gridx = 0;
        gbc.weightx = 0;
        controlPanel.add(new JLabel("Search:"), gbc);

        // Search field - expands to fill available space
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        searchField = new JTextField();
        searchField.setMinimumSize(new Dimension(100, 24));
        searchField.setPreferredSize(new Dimension(250, 24));
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updateSearch(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updateSearch(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updateSearch(); }

            private void updateSearch() {
                searchQuery = searchField.getText().toLowerCase();
                refreshLogs();
            }
        });
        controlPanel.add(searchField, gbc);

        // Filter button
        gbc.gridx = 2;
        gbc.weightx = 0;
        JButton filterButton = createFilterIconButton();
        filterButton.addActionListener(e -> showFilterDialog());
        controlPanel.add(filterButton, gbc);

        // Spacer to push right buttons to the right
        gbc.gridx = 3;
        gbc.weightx = 0.1;
        controlPanel.add(Box.createHorizontalGlue(), gbc);

        // Clear button
        gbc.gridx = 4;
        gbc.weightx = 0;
        JButton clearButton = createClearIconButton();
        clearButton.addActionListener(e -> clearLogs());
        controlPanel.add(clearButton, gbc);

        // Export button
        gbc.gridx = 5;
        gbc.weightx = 0;
        JButton exportButton = createExportIconButton();
        exportButton.addActionListener(e -> exportLogs());
        controlPanel.add(exportButton, gbc);
    }

    private JButton createFilterIconButton() {
        JButton button = new JButton("≡");
        button.setFont(new Font("Dialog", Font.BOLD, 14));
        button.setToolTipText("Filters & Mode");
        button.setFocusPainted(false);
        button.setMargin(new Insets(2, 2, 2, 2));
        button.setPreferredSize(new Dimension(24, 24));
        button.setMinimumSize(new Dimension(24, 24));
        button.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
        return button;
    }

    private JButton createClearIconButton() {
        JButton button = new JButton("✕");
        button.setFont(new Font("Dialog", Font.BOLD, 14));
        button.setToolTipText("Clear Logs");
        button.setFocusPainted(false);
        button.setMargin(new Insets(2, 2, 2, 2));
        button.setPreferredSize(new Dimension(24, 24));
        button.setMinimumSize(new Dimension(24, 24));
        button.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
        return button;
    }

    private JButton createExportIconButton() {
        JButton button = new JButton("↓");
        button.setFont(new Font("Dialog", Font.BOLD, 16));
        button.setToolTipText("Export Logs");
        button.setFocusPainted(false);
        button.setMargin(new Insets(2, 2, 2, 2));
        button.setPreferredSize(new Dimension(24, 24));
        button.setMinimumSize(new Dimension(24, 24));
        button.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
        return button;
    }

    private void initializeSuggestionWindow() {
        suggestionWindow = new JWindow();
        suggestionWindow.setFocusable(false);

        suggestionModel = new DefaultListModel<>();
        suggestionList = new JList<>(suggestionModel);
        suggestionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        suggestionList.setBackground(ThemeManager.getInstance().getInputBackground());
        suggestionList.setForeground(ThemeManager.getInstance().getForeground());
        suggestionList.setBorder(BorderFactory.createLineBorder(ThemeManager.getInstance().getBorderColor()));
        suggestionList.setFont(new Font("Monospaced", Font.PLAIN, 11));

        suggestionList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    applySuggestion();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(suggestionList);
        scrollPane.setPreferredSize(new Dimension(300, 150));
        suggestionWindow.add(scrollPane);
        suggestionWindow.pack();
    }

    public void appendLog(String message, LogLevel level) {
        LogEntry entry = new LogEntry(message, level);

        // Keep storage and rendering on the EDT. This prevents a theme refresh
        // from racing with a queued log update and displaying an entry twice.
        SwingUtilities.invokeLater(() -> {
            allLogEntries.add(entry);
            if (shouldDisplayEntry(entry)) {
                displayLogEntry(entry);
            }
        });
    }

    private boolean shouldDisplayEntry(LogEntry entry) {
        // Check log level filter
        if (!enabledLevels.contains(entry.level)) return false;

        // Check search filter
        if (!searchQuery.isEmpty() && !entry.message.toLowerCase().contains(searchQuery)) return false;

        return true;
    }

    private void displayLogEntry(LogEntry entry) {
        boolean shouldFollow = followLogBottom;
        if (shouldFollow) {
            autoScrollPending = true;
        }
        updatingLogDisplay = true;
        try {
            // Add log level tag
            String levelTag = "[" + (entry.level == LogLevel.CHAT ? "CHAT" : "SERVER LOG") + "] ";
            logDocument.insertString(logDocument.getLength(), levelTag, logStyles.get(entry.level));

            // Add message
            logDocument.insertString(logDocument.getLength(), entry.message + "\n", logStyles.get(entry.level));
        } catch (BadLocationException e) {
            e.printStackTrace();
        } finally {
            updatingLogDisplay = false;
        }
        if (shouldFollow) {
            followLogBottom = true;
            scrollLogToBottom();
        }
    }

    private void executeCommand() {
        String command = commandInput.getText().trim();
        if (command.isEmpty()) return;

        // Add to history
        commandHistory.add(command);
        historyIndex = commandHistory.size();

        // Log the command
        appendLog("> " + command, LogLevel.INFO);

        // Execute command
        server.handleConsoleInput(command, server.createCommandSourceStack());

        // Clear input
        commandInput.setText("");
        suggestionGeneration++;
        hideSuggestions();
    }

    private void updateSuggestions() {
        long generation = ++suggestionGeneration;
        String text = commandInput.getText();
        if (text.isEmpty()) {
            hideSuggestions();
            return;
        }

        // A leading slash is accepted by the console but is not part of the
        // command syntax, so suggestions are computed on the text after it and
        // every completion range is shifted back by one.
        int offset = CommandSuggestionUtils.parseOffset(text);
        String parseText = CommandSuggestionUtils.parseText(text);

        // Validate command syntax after Swing has completed the document change.
        validateCommand(parseText);

        try {
            // Get command dispatcher
            CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();

            // Parse and get suggestions - with bounds checking
            StringReader reader = new StringReader(parseText);
            ParseResults<CommandSourceStack> parse = dispatcher.parse(reader, server.createCommandSourceStack());

            // Capture the caret together with the text. A completion range is
            // valid only if neither the text nor the caret moved while Brigadier
            // calculated the asynchronous result.
            int cursorPos = cursorPosition(text);

            pendingSuggestions = dispatcher.getCompletionSuggestions(parse, cursorPos);
            pendingSuggestions.thenAccept(suggestions -> {
                SwingUtilities.invokeLater(() -> {
                    // Ignore completions calculated for an older text/caret state.
                    // They must never overwrite the user's current typing state.
                    if (generation != suggestionGeneration
                            || !text.equals(commandInput.getText())
                            || cursorPos != cursorPosition(text)) {
                        return;
                    }
                    suggestionModel.clear();

                    if (!suggestions.isEmpty()) {
                        for (Suggestion suggestion : suggestions.getList()) {
                            suggestionModel.addElement(new SuggestionItem(suggestion, offset));
                        }

                        if (suggestionModel.size() > 0) {
                            showSuggestions();
                        } else {
                            hideSuggestions();
                        }
                    } else {
                        hideSuggestions();
                    }
                });
            }).exceptionally(throwable -> {
                // A failed request may also be stale; do not hide a popup created
                // for newer input.
                SwingUtilities.invokeLater(() -> {
                    if (generation == suggestionGeneration
                            && text.equals(commandInput.getText())
                            && cursorPosition(text) == cursorPos) {
                        hideSuggestions();
                    }
                });
                return null;
            });
        } catch (Exception e) {
            // If any error occurs, just hide suggestions
            hideSuggestions();
        }
    }

    private void validateCommand(String command) {
        // Simple validation - check if command exists
        CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
        StringReader reader = new StringReader(command);
        ParseResults<CommandSourceStack> parse = dispatcher.parse(reader, server.createCommandSourceStack());

        // JTextField uses a PlainDocument, so only update its foreground here;
        // attempting to cast its document to StyledDocument caused a swallowed
        // ClassCastException on every validation.
        commandInput.setForeground(parse.getExceptions().isEmpty()
                ? ThemeManager.getInstance().getForeground()
                : ThemeManager.getInstance().getLogColor(LogLevel.ERROR));
    }

    /** The caret position as Brigadier sees it, i.e. relative to the parse text. */
    private int cursorPosition(String text) {
        int offset = CommandSuggestionUtils.parseOffset(text);
        int length = CommandSuggestionUtils.parseText(text).length();
        return Math.min(Math.max(0, commandInput.getCaretPosition() - offset), length);
    }

    /**
     * Whether a fully qualified id exists in a built-in registry. This is what
     * lets an argument such as {@code oak_button} complete as
     * {@code minecraft:oak_button} without touching player names, numbers or
     * free text.
     */
    private static boolean isRegistryId(String id) {
        Identifier identifier = Identifier.tryParse(id);
        return identifier != null && KnownIds.contains(identifier.toString());
    }

    /**
     * Every id of every built-in registry, collected once on first use so a
     * keystroke never walks the registries for each of the suggestions.
     */
    private static final class KnownIds {
        private static volatile Set<String> ids;

        private KnownIds() {
        }

        static boolean contains(String id) {
            Set<String> current = ids;
            if (current == null) {
                try {
                    Set<String> collected = new HashSet<>();
                    for (Registry<?> registry : BuiltInRegistries.REGISTRY) {
                        for (Identifier identifier : registry.keySet()) {
                            collected.add(identifier.toString());
                        }
                    }
                    current = collected;
                    ids = collected;
                } catch (RuntimeException ignored) {
                    // Registries are not always reachable (for example during
                    // startup); completion still works, it just stays unqualified.
                    return false;
                }
            }
            return current.contains(id);
        }
    }

    private void showSuggestions() {
        if (!commandInput.isShowing()) {
            hideSuggestions();
            return;
        }
        Point location = commandInput.getLocationOnScreen();
        suggestionWindow.setLocation(location.x, location.y - suggestionWindow.getHeight());
        suggestionWindow.setVisible(true);
        suggestionList.setSelectedIndex(0);
    }

    private void hideSuggestions() {
        suggestionWindow.setVisible(false);
    }

    private void applySuggestion() {
        SuggestionItem selected = suggestionList.getSelectedValue();
        if (selected == null) {
            hideSuggestions();
            return;
        }

        String currentText = commandInput.getText();
        if (selected.start < 0 || selected.end > currentText.length() || selected.start > selected.end) {
            hideSuggestions();
            return;
        }

        // Build the new command text from the token range Brigadier proposed,
        // shifted back by the leading slash when the console has one.
        String newText = currentText.substring(0, selected.start)
                + selected.text
                + currentText.substring(selected.end);

        // The replacement is intentional, so the document listener must not
        // reopen the list on the text it just produced.
        suggestionsSuppressed = true;
        commandInput.setText(newText);
        commandInput.setCaretPosition(Math.min(newText.length(), selected.start + selected.text.length()));

        suggestionGeneration++;
        hideSuggestions();
        commandInput.requestFocus();
    }

    private void showFilterDialog() {
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Log Filters", true);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setSize(300, 250);
        dialog.setLocationRelativeTo(this);

        JPanel checkboxPanel = new JPanel(new GridLayout(0, 1, 5, 5));
        checkboxPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        Map<LogLevel, JCheckBox> checkboxes = new HashMap<>();
        for (LogLevel level : LogLevel.values()) {
            JCheckBox checkbox = new JCheckBox(level.name(), enabledLevels.contains(level));
            checkboxes.put(level, checkbox);
            checkboxPanel.add(checkbox);
        }

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton applyButton = new JButton("Apply");
        applyButton.addActionListener(e -> {
            enabledLevels.clear();
            for (Map.Entry<LogLevel, JCheckBox> entry : checkboxes.entrySet()) {
                if (entry.getValue().isSelected()) {
                    enabledLevels.add(entry.getKey());
                }
            }
            refreshLogs();
            dialog.dispose();
        });

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dialog.dispose());

        buttonPanel.add(applyButton);
        buttonPanel.add(cancelButton);

        dialog.add(checkboxPanel, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void clearLogs() {
        allLogEntries.clear();
        try {
            logDocument.remove(0, logDocument.getLength());
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void themeChanged(ThemeManager themeManager) {
        SwingUtilities.invokeLater(() -> {
            setBackground(themeManager.getPanelBackground());
            controlPanel.setBackground(themeManager.getSurfaceBackground());
            logTextPane.setBackground(themeManager.getInputBackground());
            logTextPane.setForeground(themeManager.getForeground());
            commandInput.setBackground(themeManager.getInputBackground());
            commandInput.setForeground(themeManager.getForeground());
            suggestionList.setBackground(themeManager.getInputBackground());
            suggestionList.setForeground(themeManager.getForeground());
            suggestionList.setBorder(BorderFactory.createLineBorder(themeManager.getBorderColor()));

            for (LogLevel level : LogLevel.values()) {
                StyleConstants.setForeground(logStyles.get(level), themeManager.getLogColor(level));
            }
            StyleConstants.setForeground(commandStyle, themeManager.getLogColor(LogLevel.INFO));
            StyleConstants.setForeground(errorStyle, themeManager.getLogColor(LogLevel.ERROR));
            refreshLogs();
            revalidate();
            repaint();
        });
    }

    private void refreshLogs() {
        // Clear display
        try {
            logDocument.remove(0, logDocument.getLength());
        } catch (BadLocationException e) {
            e.printStackTrace();
        }

        // Re-display the stored entries. Storage and refresh both run on the EDT.
        for (LogEntry entry : new ArrayList<>(allLogEntries)) {
            if (shouldDisplayEntry(entry)) {
                displayLogEntry(entry);
            }
        }
        if (followLogBottom) {
            scrollLogToBottom();
        }
    }

    private void updateFollowLogBottom() {
        if (updatingLogDisplay || autoScrollPending || programmaticScroll) {
            return;
        }
        BoundedRangeModel model = logScrollPane.getVerticalScrollBar().getModel();
        int distanceFromBottom = model.getMaximum() - model.getValue() - model.getExtent();
        followLogBottom = distanceFromBottom <= 4;
    }

    private void scrollLogToBottom() {
        long generation = scrollGeneration;
        SwingUtilities.invokeLater(() -> {
            if (generation != scrollGeneration || !followLogBottom) {
                autoScrollPending = false;
                return;
            }
            JScrollBar scrollBar = logScrollPane.getVerticalScrollBar();
            BoundedRangeModel model = scrollBar.getModel();
            updatingLogDisplay = true;
            programmaticScroll = true;
            try {
                scrollBar.setValue(Math.max(0, model.getMaximum() - model.getExtent()));
            } finally {
                programmaticScroll = false;
                updatingLogDisplay = false;
                // Let layout notifications caused by this operation finish before
                // automatic/manual scroll detection becomes active again.
                SwingUtilities.invokeLater(() -> autoScrollPending = false);
            }
        });
    }

    private void exportLogs() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export Logs");
        fileChooser.setSelectedFile(new java.io.File("server-logs-" +
                new SimpleDateFormat("yyyy-MM-dd-HHmmss").format(new Date()) + ".txt"));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (FileWriter writer = new FileWriter(fileChooser.getSelectedFile())) {
                writer.write(logTextPane.getText());
                JOptionPane.showMessageDialog(this, "Logs exported successfully!",
                        "Export Complete", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Failed to export logs: " + e.getMessage(),
                        "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private class CommandInputKeyListener extends KeyAdapter {
        @Override
        public void keyPressed(KeyEvent e) {
            int keyCode = e.getKeyCode();
            if (keyCode != KeyEvent.VK_UP
                    && keyCode != KeyEvent.VK_DOWN
                    && keyCode != KeyEvent.VK_TAB
                    && keyCode != KeyEvent.VK_ENTER
                    && keyCode != KeyEvent.VK_ESCAPE) {
                // Any normal edit invalidates pending completion ranges. Never
                // let an async suggestion act on text that changed afterwards.
                // Typing also re-enables live suggestions after a completion.
                suggestionsSuppressed = false;
                suggestionGeneration++;
                hideSuggestions();
            }

            switch (keyCode) {
                case KeyEvent.VK_UP:
                    if (suggestionWindow.isVisible() && suggestionModel.size() > 0) {
                        // Navigate suggestions up
                        int currentIndex = suggestionList.getSelectedIndex();
                        if (currentIndex > 0) {
                            suggestionList.setSelectedIndex(currentIndex - 1);
                            suggestionList.ensureIndexIsVisible(currentIndex - 1);
                        }
                    } else {
                        // Navigate command history
                        navigateHistory(-1);
                    }
                    e.consume();
                    break;

                case KeyEvent.VK_DOWN:
                    if (suggestionWindow.isVisible() && suggestionModel.size() > 0) {
                        // Navigate suggestions down
                        int currentIndex = suggestionList.getSelectedIndex();
                        if (currentIndex < suggestionModel.size() - 1) {
                            suggestionList.setSelectedIndex(currentIndex + 1);
                            suggestionList.ensureIndexIsVisible(currentIndex + 1);
                        }
                    } else {
                        // Navigate command history
                        navigateHistory(1);
                    }
                    e.consume();
                    break;

                case KeyEvent.VK_TAB:
                    if (suggestionWindow.isVisible() && suggestionModel.size() > 0) {
                        // If no selection, select first item
                        if (suggestionList.getSelectedIndex() < 0) {
                            suggestionList.setSelectedIndex(0);
                        }

                        // Apply the selected suggestion on TAB
                        applySuggestion();
                    } else if (!commandInput.getText().isEmpty()) {
                        // No suggestions visible - try to trigger suggestion update
                        updateSuggestions();
                    }
                    e.consume(); // Always consume TAB to prevent focus change
                    break;

                case KeyEvent.VK_ENTER:
                    // ENTER always executes the command, never applies suggestions
                    if (suggestionWindow.isVisible()) {
                        // Just hide suggestions, let command execute
                        hideSuggestions();
                    }
                    // Don't consume - let the ActionListener handle command execution
                    break;

                case KeyEvent.VK_ESCAPE:
                    hideSuggestions();
                    e.consume();
                    break;
            }
        }
    }

    private void navigateHistory(int direction) {
        suggestionGeneration++;
        hideSuggestions();
        if (commandHistory.isEmpty()) return;

        historyIndex = Math.max(0, Math.min(commandHistory.size(), historyIndex + direction));

        if (historyIndex < commandHistory.size()) {
            commandInput.setText(commandHistory.get(historyIndex));
        } else {
            commandInput.setText("");
        }
    }

    private class CommandCaretListener implements CaretListener {
        @Override
        public void caretUpdate(CaretEvent event) {
            // Mouse clicks and programmatic history changes can move the caret
            // without producing a key event. Invalidate completion ranges then.
            suggestionGeneration++;
            hideSuggestions();
        }
    }

    /**
     * Live completion: the list follows what is typed. It only reads the text
     * and moves a non-focusable popup, so it can never reorder the characters
     * of the command itself.
     */
    private class CommandDocumentListener implements DocumentListener {
        @Override
        public void insertUpdate(DocumentEvent event) {
            scheduleSuggestions();
        }

        @Override
        public void removeUpdate(DocumentEvent event) {
            scheduleSuggestions();
        }

        @Override
        public void changedUpdate(DocumentEvent event) {
        }
    }

    private void scheduleSuggestions() {
        if (suggestionsSuppressed) {
            return;
        }
        // Run after Swing has finished the document change, so Brigadier always
        // parses the text the user can see.
        SwingUtilities.invokeLater(this::updateSuggestions);
    }

    // Wrapper class for suggestions
    private static class SuggestionItem {
        final String text;
        final int start;
        final int end;

        SuggestionItem(Suggestion suggestion, int offset) {
            this.text = CommandSuggestionUtils.displayText(suggestion.getText(),
                    suggestion.getRange().getStart(), EnhancedLogPanel::isRegistryId);
            this.start = suggestion.getRange().getStart() + offset;
            this.end = suggestion.getRange().getEnd() + offset;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    public enum LogLevel {
        INFO, WARN, ERROR, DEBUG, CHAT
    }

    public enum ConsoleMode {
        SERVER_LOG("Server Log"),
        CHAT_ONLY("Chat Only");

        private final String displayName;

        ConsoleMode(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    // Log entry class to store all log data
    private static class LogEntry {
        final String message;
        final LogLevel level;

        LogEntry(String message, LogLevel level) {
            this.message = message;
            this.level = level;
        }
    }
}
