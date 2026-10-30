/*
 * Copyright (c) 2026 SuperSirvu
 *
 * Licensed under the MIT License.
 */

package net.supersirvu.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MotdTextUtilsTest {
    @Test
    void repairsRepeatedMojibakeSectionSigns() {
        String repeated = "\u00C3\u0192\u00E2\u20AC\u0161\u00C3\u201A\u00A73>>> "
                + "\u00C3\u0192\u00E2\u20AC\u0161\u00C3\u201A\u00A76\u00C3\u0192\u00E2\u20AC\u0161\u00C3\u201A\u00A7lJa"
                + "\u00C3\u0192\u00E2\u20AC\u0161\u00C3\u201A\u00A79\u00C3\u0192\u00E2\u20AC\u0161\u00C3\u201A\u00A7lrock"
                + "\u00C3\u0192\u00E2\u20AC\u0161\u00C3\u201A\u00A7r Minecraft 26.2 Server \u00C3\u0192\u00E2\u20AC\u0161\u00C3\u201A\u00A7r\n"
                + "Visit \u00C3\u0192\u00E2\u20AC\u0161\u00C3\u201A\u00A73piboh.github.io/jarock"
                + "\u00C3\u0192\u00E2\u20AC\u0161\u00C3\u201A\u00A7r for more informations.";
        String expected = "§3>>> §6§lJa§9§lrock§r Minecraft 26.2 Server §r\n"
                + "Visit §3piboh.github.io/jarock§r for more informations.";

        assertEquals(expected, MotdTextUtils.repairEncoding(repeated));
        assertEquals(expected, MotdTextUtils.repairEncoding(MotdTextUtils.repairEncoding(repeated)));
        assertEquals(expected, MotdTextUtils.normalize(repeated));
    }

    @Test
    void repairsUtf8MojibakeSectionSigns() {
        String mojibake = "Â§3>>> Â§6Â§lJaÂ§9Â§lrockÂ§r Minecraft 26.2 Server Â§r\n"
                + "Visit Â§3piboh.github.io/jarockÂ§r for more informations.";
        String expected = "§3>>> §6§lJa§9§lrock§r Minecraft 26.2 Server §r\n"
                + "Visit §3piboh.github.io/jarock§r for more informations.";

        assertEquals(expected, MotdTextUtils.repairEncoding(mojibake));
        assertEquals(expected, MotdTextUtils.normalize(mojibake));
    }

    @Test
    void preservesLiteralSectionSignFormatting() {
        assertEquals("\u00A73>>> \u00A76\u00A7lJarock\u00A7r", 
                MotdTextUtils.normalize("\u00A73>>> \u00A76\u00A7lJarock\u00A7r"));
    }

    @Test
    void decodesBackslashAndSlashUnicodeSectionSignEscapes() {
        assertEquals("\u00A73Green \u00A7ltext", MotdTextUtils.normalize("\\u00A73Green \\u00a7ltext"));
        assertEquals("\u00A73Green \u00A7ltext", MotdTextUtils.normalize("/u00A73Green /u00a7ltext"));
    }

    @Test
    void decodesNewlineEscapesWithoutChangingOrdinaryUrlText() {
        assertEquals("First\nSecond", MotdTextUtils.normalize("First\\nSecond"));
        assertEquals("First/nSecond", MotdTextUtils.normalize("First/nSecond"));
        assertEquals("https://example.test/nVisit", MotdTextUtils.normalize("https://example.test/nVisit"));
        assertEquals("\u00A7r\nVisit", MotdTextUtils.normalize("\u00A7r/nVisit"));
    }

    @Test
    void normalizesTheCompleteExampleMotd() {
        String input = "/u00A73>>> /u00A76/u00A7lJa/u00A79/u00A7lrock/u00A7r Minecraft 26.2 Server /u00A7r/n"
                + "Visit /u00A73piboh.github.io/jarock/u00A7r for more informations.";
        String expected = "\u00A73>>> \u00A76\u00A7lJa\u00A79\u00A7lrock\u00A7r Minecraft 26.2 Server \u00A7r\n"
                + "Visit \u00A73piboh.github.io/jarock\u00A7r for more informations.";

        assertEquals(expected, MotdTextUtils.normalize(input));
    }

    @Test
    void supportsEveryMctoolsColorAndFormattingCode() {
        String input = "\\u00A70Black\\u00A71DarkBlue\\u00A72DarkGreen\\u00A73DarkAqua"
                + "\\u00A74DarkRed\\u00A75DarkPurple\\u00A76Gold\\u00A77Gray\\u00A78DarkGray"
                + "\\u00A79Blue\\u00A7aGreen\\u00A7bAqua\\u00A7cRed\\u00A7dLightPurple"
                + "\\u00A7eYellow\\u00A7fWhite\\u00A7r\\n"
                + "\\u00A7lBold\\u00A7nUnderline\\u00A7oItalic\\u00A7mStrikethrough"
                + "\\u00A7k\\u00A7kObfuscated\\u00A7rReset";
        String expected = "\u00A70Black\u00A71DarkBlue\u00A72DarkGreen\u00A73DarkAqua"
                + "\u00A74DarkRed\u00A75DarkPurple\u00A76Gold\u00A77Gray\u00A78DarkGray"
                + "\u00A79Blue\u00A7aGreen\u00A7bAqua\u00A7cRed\u00A7dLightPurple"
                + "\u00A7eYellow\u00A7fWhite\u00A7r\n"
                + "\u00A7lBold\u00A7nUnderline\u00A7oItalic\u00A7mStrikethrough"
                + "\u00A7k\u00A7kObfuscated\u00A7rReset";

        assertEquals(expected, MotdTextUtils.normalize(input));
    }

    @Test
    void doesNotDecodeSlashNewlineInAnInsertedMiddleFragmentUnlessAfterReset() {
        assertEquals("/nSuffix", MotdTextUtils.normalize("/nSuffix", false));
        assertEquals("§r\nSuffix", MotdTextUtils.normalize("§r/nSuffix", false));
    }

    @Test
    void repairsEncodingWithoutChangingNormalText() {
        assertEquals("plain text", MotdTextUtils.repairEncoding("plain text"));
        assertEquals("§ is repaired", MotdTextUtils.repairEncoding("Â§ is repaired"));
    }

    @Test
    void normalizesWindowsAndUnixLineEndings() {
        assertEquals("First\nSecond", MotdTextUtils.normalize("First\r\nSecond"));
        assertEquals("First\nSecond", MotdTextUtils.normalize("First\rSecond"));
    }

    @Test
    void handlesLineLimitBoundaries() {
        assertEquals("single line", MotdTextUtils.limitLines("single line", 2));
        assertEquals("first", MotdTextUtils.limitLines("first\nsecond", 1));
        assertEquals("first", MotdTextUtils.limitLines("first\nsecond", 0));
    }

    @Test
    void limitsMotdToTwoLinesWithoutLimitingLineLength() {
        String longFirstLine = "A".repeat(500);
        String motd = longFirstLine + "\nSecond line\nThird line";

        assertEquals(longFirstLine + "\nSecond line", MotdTextUtils.limitLines(motd, 2));
    }
}
