package io.github.ctbot000.jvminspector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {

    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errors = new ByteArrayOutputStream();

    private int run(String... arguments) {
        return Main.run(arguments, new PrintStream(output, true, StandardCharsets.UTF_8),
                new PrintStream(errors, true, StandardCharsets.UTF_8));
    }

    private String out() {
        return output.toString(StandardCharsets.UTF_8);
    }

    @Test
    void helpAndVersionSucceed() {
        assertEquals(0, run("--help"));
        assertTrue(out().contains("Usage: jvm-inspector"));

        output.reset();
        assertEquals(0, run("--version"));
        assertTrue(out().startsWith("jvm-inspector "));
    }

    @Test
    void sectionsAreListedWithTheirIds() {
        assertEquals(0, run("--list-sections"));
        assertTrue(out().contains("overview"));
        assertTrue(out().contains("mbeans"));
        assertTrue(out().contains("Management Beans"));
    }

    @Test
    void localJvmsCanBeListed() {
        assertEquals(0, run("--list-jvms"));
        assertTrue(out().contains("PID") || out().contains("No attachable JVMs"));
    }

    @Test
    void anUnknownOptionIsAUsageError() {
        assertEquals(2, run("--wat"));
        assertTrue(errors.toString(StandardCharsets.UTF_8).contains("unknown option"));
    }

    @Test
    void aSelfInspectionReachesStandardOutput() {
        assertEquals(0, run("--only", "overview,memory", "--stack-depth", "0"));
        String report = out();
        assertTrue(report.contains("1. OVERVIEW"));
        assertTrue(report.contains("2. MEMORY"));
        assertTrue(report.contains("Heap used"));
        assertTrue(report.contains("Inspection Run"));
    }

    @Test
    void aReportCanBeWrittenToAFile(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("report.json");
        assertEquals(0, run("-f", "json", "-o", file.toString(), "--only", "overview"));
        assertTrue(Files.size(file) > 0);
        String json = Files.readString(file);
        assertTrue(json.startsWith("{"));
        assertTrue(json.contains("\"tool\": \"jvm-inspector\""));
        assertTrue(out().contains("Wrote"));
    }

    @Test
    void watchModeStopsAfterTheRequestedSamples() {
        assertEquals(0, run("--watch", "1", "--samples", "2", "--only", "overview"));
        String printed = out();
        assertTrue(printed.contains("heap used"));
        assertEquals(2, printed.lines().filter(line -> line.matches("\\d\\d:\\d\\d:\\d\\d.*")).count());
    }

    @Test
    void attachingToAMissingProcessFailsCleanly() {
        int code = run("--pid", "999999");
        assertEquals(Main.FAILURE, code);
        assertTrue(errors.toString(StandardCharsets.UTF_8).contains("jvm-inspector:"));
    }
}
