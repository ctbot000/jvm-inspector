package io.github.ctbot000.jvminspector.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelTest {

    @Test
    void valuesKeepTheirRawTypeAlongsideTheirDisplay() {
        Value bytes = Value.bytes(2048);
        assertEquals(Value.Kind.BYTES, bytes.kind());
        assertEquals(2048L, bytes.raw());
        assertEquals("2.00 KiB (2,048 bytes)", bytes.display());

        assertTrue(Value.bytes(-1).isAbsent());
        assertTrue(Value.millis(-5).isAbsent());
        assertTrue(Value.timestamp(0).isAbsent());
        assertEquals("(not supported)", Value.unsupported().display());
    }

    @Test
    void objectConversionCoversWhatAnMbeanCanReturn() {
        assertEquals(Value.Kind.BOOLEAN, Value.ofObject(Boolean.TRUE).kind());
        assertEquals(Value.Kind.NUMBER, Value.ofObject(7).kind());
        assertEquals(Value.Kind.NUMBER, Value.ofObject(1.5d).kind());
        assertEquals(Value.Kind.STRING, Value.ofObject("text").kind());
        assertEquals(Value.Kind.LIST, Value.ofObject(new String[]{"a", "b"}).kind());
        assertEquals(Value.Kind.LIST, Value.ofObject(List.of("a")).kind());
        assertTrue(Value.ofObject(null).isAbsent());
        Value existing = Value.of(1L);
        assertEquals(existing, Value.ofObject(existing));
    }

    @Test
    void listsRenderAsCommaSeparatedText() {
        assertEquals("a, b", Value.list(List.of("a", "b")).display());
        assertEquals("(none)", Value.list(List.of()).display());
    }

    @Test
    void aTableRowMustMatchItsHeaders() {
        Table.Builder builder = Table.builder("Pools", "Name", "Used");
        builder.row("eden", 1);
        assertThrows(IllegalArgumentException.class, () -> builder.row("only one"));
        assertEquals(1, builder.size());
        assertEquals(2, builder.build().columnCount());
    }

    @Test
    void sectionsNestAndSkipEmptyTables() {
        Section section = new Section("Root");
        assertTrue(section.isEmpty());
        Section child = section.sub("Child");
        child.put("name", "value");
        section.table(Table.builder("Empty", "A").build());
        assertEquals(1, section.children().size());
        assertFalse(section.isEmpty());
        assertEquals("Child", ((Section) section.children().get(0)).title());
    }

    @Test
    void notesCarryTheirSeverity() {
        assertEquals(Note.Level.INFO, Note.info("x").level());
        assertEquals(Note.Level.WARN, Note.warn("x").level());
        assertEquals(Note.Level.ERROR, Note.error("x").level());
    }

    @Test
    void codeStripsTrailingWhitespaceAndToleratesNull() {
        assertEquals("body", new Code("t", "body\n\n").body());
        assertEquals("", new Code("t", null).body());
    }
}
