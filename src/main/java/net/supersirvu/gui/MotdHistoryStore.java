/*
 * Copyright (c) 2026 SuperSirvu
 *
 * Licensed under the MIT License.
 */

package net.supersirvu.gui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** Small, dependency-free store for the MOTD editor history. */
public final class MotdHistoryStore {
    private static final Path FILE = Path.of("config", "dedicatedpower-motd-history.properties");

    private MotdHistoryStore() {
    }

    public static State load() {
        State state = new State();
        if (!Files.isRegularFile(FILE)) {
            return state;
        }

        Properties properties;
        try {
            properties = Utf8Properties.load(FILE);
            state.enabled = Boolean.parseBoolean(properties.getProperty("enabled", "true"));
            int count = Integer.parseInt(properties.getProperty("count", "0"));
            for (int index = 0; index < count; index++) {
                String value = properties.getProperty("entry." + index);
                if (value != null && !value.isBlank()) {
                    state.entries.add(MotdTextUtils.repairEncoding(value));
                }
            }
        } catch (Exception ignored) {
            // A damaged history must never prevent the server GUI from opening.
        }
        return state;
    }

    public static void save(State state) {
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(state.enabled));
        properties.setProperty("count", Integer.toString(state.entries.size()));
        for (int index = 0; index < state.entries.size(); index++) {
            properties.setProperty("entry." + index, state.entries.get(index));
        }

        try {
            Utf8Properties.store(FILE, properties, "DedicatedPower MOTD history");
        } catch (IOException ignored) {
            // MOTD editing remains functional when history persistence is unavailable.
        }
    }

    public static final class State {
        private boolean enabled = true;
        private final List<String> entries = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getEntries() {
            return entries;
        }

        public void add(String motd) {
            motd = MotdTextUtils.repairEncoding(motd);
            entries.remove(motd);
            entries.add(0, motd);
            while (entries.size() > 50) {
                entries.remove(entries.size() - 1);
            }
        }
    }
}
