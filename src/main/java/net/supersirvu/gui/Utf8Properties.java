/*
 * Copyright (c) 2026 SuperSirvu
 *
 * Licensed under the MIT License.
 */

package net.supersirvu.gui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

/**
 * Reads and writes {@code .properties} files the way Minecraft does: UTF-8
 * first, with an ISO-8859-1 fallback for files written by older tools.
 *
 * <p>{@link Properties#load(java.io.InputStream)} and
 * {@link Properties#store(java.io.OutputStream, String)} always use
 * ISO-8859-1, so a MOTD saved through them turned every {@code §} into
 * {@code Â§}. Reading and writing with explicit UTF-8 readers keeps the section
 * sign intact across the GUI, the history file and server.properties.</p>
 */
final class Utf8Properties {
    private Utf8Properties() {
    }

    static Properties load(Path path) throws IOException {
        Properties properties = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
            return properties;
        } catch (CharacterCodingException legacyEncoding) {
            // A file written by an older build may hold single-byte section
            // signs. Reading it as ISO-8859-1 recovers the original text.
            Properties legacy = new Properties();
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.ISO_8859_1)) {
                legacy.load(reader);
            }
            return legacy;
        }
    }

    static void store(Path path, Properties properties, String comment) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            properties.store(writer, comment);
        }
    }
}
