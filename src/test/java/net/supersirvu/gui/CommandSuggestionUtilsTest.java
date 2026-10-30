/*
 * Copyright (c) 2026 SuperSirvu
 *
 * Licensed under the MIT License.
 */

package net.supersirvu.gui;

import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandSuggestionUtilsTest {
    private static final Predicate<String> ITEMS = id -> id.equals("minecraft:oak_button");

    @Test
    void acceptsCommandsWithAndWithoutLeadingSlash() {
        assertEquals(0, CommandSuggestionUtils.parseOffset("give Steve oak_button"));
        assertEquals(1, CommandSuggestionUtils.parseOffset("/give Steve oak_button"));
        assertEquals("give Steve oak_button", CommandSuggestionUtils.parseText("/give Steve oak_button"));
        // A lone slash is kept as an empty command, so the full list is offered.
        assertEquals("", CommandSuggestionUtils.parseText("/"));
    }

    @Test
    void completesArgumentsAsNamespacedRegistryIds() {
        assertEquals("minecraft:oak_button", CommandSuggestionUtils.displayText("oak_button", 11, ITEMS));
        assertEquals("minecraft:oak_button", CommandSuggestionUtils.displayText("oak_button", 1, ITEMS));
    }

    @Test
    void neverQualifiesTheCommandNameOrUnknownArguments() {
        assertEquals("give", CommandSuggestionUtils.displayText("give", 0, ITEMS));
        assertEquals("oak_button", CommandSuggestionUtils.displayText("oak_button", 0, ITEMS));
        assertEquals("Steve", CommandSuggestionUtils.displayText("Steve", 5, ITEMS));
        assertEquals("minecraft:oak_button", CommandSuggestionUtils.displayText("minecraft:oak_button", 11, ITEMS));
        assertEquals("12", CommandSuggestionUtils.displayText("12", 5, ITEMS));
    }

    @Test
    void recognizesBareResourceIdSyntax() {
        assertTrue(CommandSuggestionUtils.isBareId("oak_button"));
        assertTrue(CommandSuggestionUtils.isBareId("minecraft"));
        assertFalse(CommandSuggestionUtils.isBareId("minecraft:oak_button"));
        assertFalse(CommandSuggestionUtils.isBareId("Steve"));
        assertFalse(CommandSuggestionUtils.isBareId(""));
    }
}
