package io.github.ctbot000.jvminspector.util;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormatTest {

    @Test
    void bytesUsesBinaryUnitsAndKeepsTheExactFigure() {
        assertEquals("0 bytes", Format.bytes(0));
        assertEquals("1 byte", Format.bytes(1));
        assertEquals("1023 bytes", Format.bytes(1023));
        assertEquals("1.00 KiB (1,024 bytes)", Format.bytes(1024));
        assertEquals("1.50 GiB (1,610,612,736 bytes)", Format.bytes(1_610_612_736L));
    }

    @Test
    void shortBytesFitInATableCell() {
        assertEquals("n/a", Format.bytesShort(-1));
        assertEquals("512 B", Format.bytesShort(512));
        assertEquals("1.0 KiB", Format.bytesShort(1024));
        assertEquals("1.5 GiB", Format.bytesShort(1_610_612_736L));
    }

    @Test
    void durationPicksAUsefulPrecision() {
        assertEquals("n/a", Format.duration(-1));
        assertEquals("999 ns", Format.duration(999));
        assertEquals("1.5 us", Format.duration(1_500));
        assertEquals("2.0 ms", Format.duration(2_000_000));
        assertEquals("1.500 s", Format.duration(1_500_000_000L));
        assertEquals("2m 5s", Format.duration(125_000_000_000L));
        assertEquals("1h 0m 0s", Format.duration(3_600_000_000_000L));
        assertEquals("1d 2h 3m 4s", Format.duration(93_784_000_000_000L));
    }

    @Test
    void escapeMakesControlCharactersVisible() {
        assertEquals("\\n", Format.escape("\n"));
        assertEquals("a\\tb", Format.escape("a\tb"));
        assertEquals("c:\\\\path", Format.escape("c:\\path"));
    }

    @Test
    void ellipsisFlattensAndTruncates() {
        assertEquals("one two", Format.ellipsis("one\ntwo", 20));
        assertEquals("abc\u2026", Format.ellipsis("abcdef", 4));
        assertEquals("abcd", Format.ellipsis("abcd", 4));
    }

    @Test
    void splitPathUsesThePlatformSeparatorAndDropsBlanks() {
        String path = String.join(File.pathSeparator, "a", "", "b");
        assertEquals(List.of("a", "b"), Format.splitPath(path));
        assertEquals(List.of(), Format.splitPath(""));
        assertEquals(List.of(), Format.splitPath(null));
    }

    @Test
    void percentAndCountAreLocaleIndependent() {
        assertEquals("12.5%", Format.percent(0.125));
        assertEquals("1,234,567", Format.count(1_234_567));
        assertTrue(Format.timestamp(0L).startsWith("19") || Format.timestamp(0L).startsWith("197"));
    }
}
