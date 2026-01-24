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
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.dedicated.MinecraftDedicatedServer;

import javax.swing.*;
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
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class EnhancedLogPanel extends JPanel {
    private final MinecraftDedicatedServer server;

    // Components
    private final JTextPane logTextPane;
    private final JTextField commandInput;
    private final JScrollPane logScrollPane;
    private final StyledDocument logDocument;

    // Command suggestion
    private JWindow suggestionWindow;
    private DefaultListModel<SuggestionItem> suggestionModel;
    private JList<SuggestionItem> suggestionList;
    private CompletableFuture<Suggestions> pendingSuggestions;
    private Suggestions currentSuggestions;
    private CommandDocumentListener documentListener;

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

    public EnhancedLogPanel(MinecraftDedicatedServer server) {
        this.server = server;

        setLayout(new BorderLayout());
        setBackground(new Color(240, 240, 240));

        // Create log display FIRST (before initializing styles)
        logTextPane = new JTextPane();
        logTextPane.setEditable(false);
        logTextPane.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logTextPane.setBackground(Color.WHITE);
        logDocument = logTextPane.getStyledDocument();

        // NOW initialize styles (after logTextPane is created)
        initializeStyles();

        logScrollPane = new JScrollPane(logTextPane);
        logScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        // Create command input
        commandInput = new JTextField();
        commandInput.setFont(new Font("Monospaced", Font.PLAIN, 12));
        commandInput.addActionListener(e -> executeCommand());
        commandInput.addKeyListener(new CommandInputKeyListener());

        // Create and store document listener reference
        documentListener = new CommandDocumentListener();
        commandInput.getDocument().addDocumentListener(documentListener);

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
        logStyles.put(LogLevel.INFO, createStyle(new Color(52, 152, 219), false));
        logStyles.put(LogLevel.WARN, createStyle(new Color(243, 156, 18), false));
        logStyles.put(LogLevel.ERROR, createStyle(new Color(231, 76, 60), true));
        logStyles.put(LogLevel.DEBUG, createStyle(new Color(149, 165, 166), false));
        logStyles.put(LogLevel.CHAT, createStyle(new Color(46, 204, 113), false));

        // Command syntax styles
        commandStyle = createStyle(new Color(52, 152, 219), true);
        argumentStyle = createStyle(new Color(155, 89, 182), false);
        errorStyle = createStyle(new Color(231, 76, 60), false);
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
        controlPanel.setBackground(new Color(250, 250, 250));

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
        suggestionList.setBackground(Color.WHITE);
        suggestionList.setBorder(BorderFactory.createLineBorder(Color.GRAY));
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
        // Store the log entry
        LogEntry entry = new LogEntry(message, level);
        allLogEntries.add(entry);

        // Display if it passes filters
        SwingUtilities.invokeLater(() -> {
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
        try {
            // Add log level tag
            String levelTag = "[" + (entry.level == LogLevel.CHAT ? "CHAT" : "SERVER LOG") + "] ";
            logDocument.insertString(logDocument.getLength(), levelTag, logStyles.get(entry.level));

            // Add message
            logDocument.insertString(logDocument.getLength(), entry.message + "\n", logStyles.get(entry.level));
        } catch (BadLocationException e) {
            e.printStackTrace();
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
        server.enqueueCommand(command, server.getCommandSource());

        // Clear input
        commandInput.setText("");
        hideSuggestions();
    }

    private void updateSuggestions() {
        String text = commandInput.getText();
        if (text.isEmpty()) {
            hideSuggestions();
            return;
        }

        // Validate command syntax
        validateCommand(text);

        try {
            // Get command dispatcher
            CommandDispatcher<ServerCommandSource> dispatcher = server.getCommandManager().getDispatcher();

            // Parse and get suggestions - with bounds checking
            StringReader reader = new StringReader(text);
            ParseResults<ServerCommandSource> parse = dispatcher.parse(reader, server.getCommandSource());

            // Ensure cursor position is within bounds
            int cursorPos = Math.min(commandInput.getCaretPosition(), text.length());

            pendingSuggestions = dispatcher.getCompletionSuggestions(parse, cursorPos);
            pendingSuggestions.thenAccept(suggestions -> {
                SwingUtilities.invokeLater(() -> {
                    suggestionModel.clear();
                    currentSuggestions = suggestions;

                    if (!suggestions.isEmpty()) {
                        for (Suggestion suggestion : suggestions.getList()) {
                            suggestionModel.addElement(new SuggestionItem(suggestion));
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
                // Silently handle suggestion errors
                SwingUtilities.invokeLater(() -> hideSuggestions());
                return null;
            });
        } catch (Exception e) {
            // If any error occurs, just hide suggestions
            hideSuggestions();
        }
    }

    private void validateCommand(String command) {
        // Simple validation - check if command exists
        CommandDispatcher<ServerCommandSource> dispatcher = server.getCommandManager().getDispatcher();
        StringReader reader = new StringReader(command);
        ParseResults<ServerCommandSource> parse = dispatcher.parse(reader, server.getCommandSource());

        // Highlight syntax in command input
        try {
            StyledDocument doc = (StyledDocument) commandInput.getDocument();
            Style defaultStyle = doc.addStyle("default", null);
            StyleConstants.setForeground(defaultStyle, Color.BLACK);

            if (!parse.getExceptions().isEmpty()) {
                // Show error
                commandInput.setForeground(Color.RED);
            } else {
                commandInput.setForeground(Color.BLACK);
            }
        } catch (Exception e) {
            // Ignore styling errors
        }
    }

    private void showSuggestions() {
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
        if (selected != null && currentSuggestions != null) {
            Suggestion suggestion = selected.suggestion;

            // Get the current text
            String currentText = commandInput.getText();

            // Get the range to replace from the suggestion
            int start = suggestion.getRange().getStart();
            int end = suggestion.getRange().getEnd();

            // Ensure bounds are valid
            if (start < 0 || end > currentText.length() || start > end) {
                hideSuggestions();
                return;
            }

            // Build the new command text
            String before = currentText.substring(0, start);
            String after = currentText.substring(end);
            String newText = before + suggestion.getText() + after;

            // CRITICAL: Remove the document listener before modifying text
            commandInput.getDocument().removeDocumentListener(documentListener);

            // Set the new text
            commandInput.setText(newText);

            // Position cursor at the end of the inserted suggestion
            int newCaretPos = start + suggestion.getText().length();
            commandInput.setCaretPosition(newCaretPos);

            // Re-add the document listener after modification is complete
            commandInput.getDocument().addDocumentListener(documentListener);

            hideSuggestions();
            commandInput.requestFocus();
        }
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

    private void refreshLogs() {
        // Clear display
        try {
            logDocument.remove(0, logDocument.getLength());
        } catch (BadLocationException e) {
            e.printStackTrace();
        }

        // Re-display all entries that match current filters
        for (LogEntry entry : allLogEntries) {
            if (shouldDisplayEntry(entry)) {
                displayLogEntry(entry);
            }
        }
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
            switch (e.getKeyCode()) {
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
        if (commandHistory.isEmpty()) return;

        historyIndex = Math.max(0, Math.min(commandHistory.size(), historyIndex + direction));

        if (historyIndex < commandHistory.size()) {
            commandInput.setText(commandHistory.get(historyIndex));
        } else {
            commandInput.setText("");
        }
    }

    private class CommandDocumentListener implements DocumentListener {
        @Override
        public void insertUpdate(DocumentEvent e) {
            updateSuggestions();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            updateSuggestions();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            updateSuggestions();
        }
    }

    // Wrapper class for suggestions
    private static class SuggestionItem {
        final Suggestion suggestion;

        SuggestionItem(Suggestion suggestion) {
            this.suggestion = suggestion;
        }

        @Override
        public String toString() {
            return suggestion.getText();
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