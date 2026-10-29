/*
 * Copyright (c) 2026 SuperSirvu
 *
 * Licensed under the MIT License.
 */

package net.supersirvu.gui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Utf8PropertiesTest {
    private static final String MOTD = "\u00A73>>> \u00A76\u00A7lJa\u00A79\u00A7lrock\u00A7r Server";

    @TempDir
    Path directory;

    @Test
    void keepsSectionSignsAcrossSaveAndLoad() throws IOException {
        Properties properties = new Properties();
        properties.setProperty("motd", MOTD);
        Path file = directory.resolve("server.properties");

        Utf8Properties.store(file, properties, "test");

        assertEquals(MOTD, Utf8Properties.load(file).getProperty("motd"));
        // The file is written as UTF-8, exactly like Minecraft writes it.
        assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains(MOTD));
    }

    @Test
    void readsFilesWrittenAsIso88591() throws IOException {
        Path file = directory.resolve("legacy.properties");
        Files.write(file, "motd=\u00A73legacy\n".getBytes(StandardCharsets.ISO_8859_1));

        assertEquals("\u00A73legacy", Utf8Properties.load(file).getProperty("motd"));
    }
}
