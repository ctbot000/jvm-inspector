package io.github.ctbot000.jvminspector.render;

import io.github.ctbot000.jvminspector.model.Note;
import io.github.ctbot000.jvminspector.model.Report;
import io.github.ctbot000.jvminspector.model.Section;
import io.github.ctbot000.jvminspector.model.Table;
import io.github.ctbot000.jvminspector.model.Value;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderersTest {

    private static Report sample() {
        Section section = new Section("Memory").description("Heap and friends");
        section.put("Heap used", Value.bytes(1_610_612_736L));
        section.put("Verbose", Value.of(false));
        section.add(Note.warn("Watch the \"old\" generation"));
        section.code("GC.heap_info", "line one\nline two");
        section.table(Table.builder("Pools", "Pool", "Used")
                .row("G1 Eden Space", Value.bytesShort(2048))
                .row("G1 Old Gen", Value.bytesShort(4096)));
        section.sub("Nested").put("Depth", Value.of(2));
        return new Report("jvm-inspector", "1.2.3", "this JVM (pid 1)", Instant.parse("2026-01-02T03:04:05Z"),
                List.of(section));
    }

    @Test
    void textCarriesEveryElement() {
        String text = new TextRenderer().renderToString(sample());
        assertTrue(text.contains("jvm-inspector 1.2.3"));
        assertTrue(text.contains("1. MEMORY"));
        assertTrue(text.contains("Heap used"));
        assertTrue(text.contains("1.50 GiB (1,610,612,736 bytes)"));
        assertTrue(text.contains("WARN:  Watch the \"old\" generation"));
        assertTrue(text.contains("[GC.heap_info]"));
        assertTrue(text.contains("Pools (2 rows)"));
        assertTrue(text.contains("-- Nested"));
    }

    @Test
    void textAlignsTableColumns() {
        String text = new TextRenderer().renderToString(sample());
        List<String> lines = text.lines().filter(line -> line.contains("G1 ")).toList();
        assertEquals(2, lines.size());
        int first = lines.get(0).indexOf("2.0 KiB");
        int second = lines.get(1).indexOf("4.0 KiB");
        assertEquals(first, second, "table cells should line up under their header");
    }

    @Test
    void jsonIsWellFormedAndKeepsRawValues() {
        String json = new JsonRenderer().renderToString(sample());
        Object parsed = TestJson.parse(json);
        Map<?, ?> document = assertInstanceOf(Map.class, parsed);
        assertEquals("jvm-inspector", document.get("tool"));
        assertEquals("1.2.3", document.get("toolVersion"));
        List<?> sections = assertInstanceOf(List.class, document.get("sections"));
        Map<?, ?> memory = assertInstanceOf(Map.class, sections.get(0));
        assertEquals("section", memory.get("type"));
        List<?> children = assertInstanceOf(List.class, memory.get("children"));
        Map<?, ?> heap = assertInstanceOf(Map.class, children.get(0));
        assertEquals("Heap used", heap.get("name"));
        assertEquals(1_610_612_736d, heap.get("value"));
        assertEquals("1.50 GiB (1,610,612,736 bytes)", heap.get("display"));
    }

    @Test
    void markdownUsesHeadingsAndTables() {
        String markdown = new MarkdownRenderer().renderToString(sample());
        assertTrue(markdown.startsWith("# jvm-inspector 1.2.3"));
        assertTrue(markdown.contains("## Memory"));
        assertTrue(markdown.contains("| Pool | Used |"));
        assertTrue(markdown.contains("```text"));
        assertTrue(markdown.contains("- **Heap used**"));
    }

    @Test
    void theTestParserRejectsMalformedJson() {
        assertThrows(IllegalArgumentException.class, () -> TestJson.parse("{\"a\": }"));
        assertThrows(IllegalArgumentException.class, () -> TestJson.parse("{\"a\": 1,}"));
        assertThrows(IllegalArgumentException.class, () -> TestJson.parse("[1, 2"));
    }

    @Test
    void formatsParseByNameAndRejectNonsense() {
        assertEquals(OutputFormat.MARKDOWN, OutputFormat.parse("md"));
        assertEquals(OutputFormat.JSON, OutputFormat.parse("JSON"));
        assertThrows(IllegalArgumentException.class, () -> OutputFormat.parse("yaml"));
        assertInstanceOf(TextRenderer.class, OutputFormat.TEXT.renderer());
    }
}
