/*
 * Copyright (c) 2026 SuperSirvu
 *
 * Licensed under the MIT License.
 */

package net.supersirvu.gui;

import java.util.function.Predicate;

/**
 * Text rules for the console command completion, kept free of Swing and
 * Minecraft types so they can be covered by the unit tests.
 */
public final class CommandSuggestionUtils {
    private static final String DEFAULT_NAMESPACE = "minecraft:";

    private CommandSuggestionUtils() {
    }

    /**
     * Characters in front of the command that are not part of Brigadier's
     * syntax. The server console accepts a leading slash, and Brigadier does
     * not, so suggestions are always computed on the text after it.
     */
    public static int parseOffset(String input) {
        return input != null && input.startsWith("/") ? 1 : 0;
    }

    /** The command text as Brigadier sees it, i.e. without a leading slash. */
    public static String parseText(String input) {
        return input == null ? "" : input.substring(parseOffset(input));
    }

    /** True for an unqualified resource id such as {@code oak_button}. */
    public static boolean isBareId(String text) {
        if (text == null || text.isEmpty() || text.indexOf(':') >= 0) {
            return false;
        }
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            boolean allowed = (character >= 'a' && character <= 'z')
                    || (character >= '0' && character <= '9')
                    || character == '_' || character == '-' || character == '.' || character == '/';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the text to show and insert for one suggestion. Arguments that
     * are known registry ids get the default namespace, the way the Aternos
     * console completes them ({@code oak_button} becomes
     * {@code minecraft:oak_button}); the command name itself is never touched,
     * and anything that is not a registry id (player names, numbers, text) is
     * left exactly as Brigadier proposed it.
     *
     * @param suggestion the text Brigadier proposed
     * @param tokenStart the position of the completed token, 0 for the command
     * @param knownId    whether a fully qualified id exists in a registry
     */
    public static String displayText(String suggestion, int tokenStart, Predicate<String> knownId) {
        if (suggestion == null || tokenStart <= 0 || !isBareId(suggestion)) {
            return suggestion;
        }
        String namespaced = DEFAULT_NAMESPACE + suggestion;
        return knownId != null && knownId.test(namespaced) ? namespaced : suggestion;
    }
}
