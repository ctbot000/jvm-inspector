package io.github.ctbot000.jvminspector.cli;

import io.github.ctbot000.jvminspector.inspect.Detail;
import io.github.ctbot000.jvminspector.render.OutputFormat;
import io.github.ctbot000.jvminspector.util.Redactor;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandLineTest {

    @Test
    void defaultsInspectThisJvmAsPlainText() {
        CommandLine command = CommandLine.parse(new String[0]);
        assertEquals(CommandLine.TargetKind.SELF, command.targetKind());
        assertEquals(OutputFormat.TEXT, command.format());
        assertEquals(Detail.STANDARD, command.inspectionOptions().detail());
        assertEquals(Redactor.Mode.SECRETS, command.inspectionOptions().redactor().mode());
        assertEquals(12, command.inspectionOptions().stackDepth());
        assertFalse(command.inspectionOptions().expensive());
        assertFalse(command.watching());
        assertTrue(command.only().isEmpty());
    }

    @Test
    void everyTargetFormIsAccepted() {
        CommandLine pid = CommandLine.parse(new String[]{"--pid", "1234"});
        assertEquals(CommandLine.TargetKind.PID, pid.targetKind());
        assertEquals(1234L, pid.pid());

        CommandLine jmx = CommandLine.parse(new String[]{"--jmx", "host:9010", "--jmx-user", "ops"});
        assertEquals(CommandLine.TargetKind.JMX, jmx.targetKind());
        assertEquals("host:9010", jmx.jmxUrl());
        assertEquals("ops", jmx.jmxUser());
    }

    @Test
    void outputOptionsAreParsed() {
        CommandLine command = CommandLine.parse(new String[]{
                "-f", "json", "-o", "out.json", "--only", "overview, memory", "--skip", "mbeans",
                "--detail", "full", "--expensive", "--stack-depth", "3", "--redact", "all"});
        assertEquals(OutputFormat.JSON, command.format());
        assertEquals("out.json", command.output().toString());
        assertEquals(Set.of("overview", "memory"), command.only());
        assertEquals(Set.of("mbeans"), command.skip());
        assertEquals(Detail.FULL, command.inspectionOptions().detail());
        assertTrue(command.inspectionOptions().expensive());
        assertEquals(3, command.inspectionOptions().stackDepth());
        assertEquals(Redactor.Mode.ALL, command.inspectionOptions().redactor().mode());
    }

    @Test
    void fullIsAShorthandForDetailFull() {
        assertEquals(Detail.FULL, CommandLine.parse(new String[]{"--full"}).inspectionOptions().detail());
    }

    @Test
    void watchingNeedsAnInterval() {
        CommandLine command = CommandLine.parse(new String[]{"--watch", "5", "--samples", "2"});
        assertTrue(command.watching());
        assertEquals(5, command.watchSeconds());
        assertEquals(2, command.samples());
    }

    @Test
    void badInputIsRejectedWithAnExplanation() {
        assertMessage("--nope", "unknown option");
        assertMessage(new String[]{"--only", "nonsense"}, "unknown section");
        assertMessage(new String[]{"--skip", "nonsense"}, "unknown section");
        assertMessage(new String[]{"--stack-depth", "-1"}, "cannot be negative");
        assertMessage(new String[]{"--load-agent"}, "--load-agent only applies to --pid");
        assertMessage(new String[]{"--jmx-user", "ops"}, "--jmx-user only applies to --jmx");
        assertMessage(new String[]{"--pid", "abc"}, "needs a number");
        assertMessage(new String[]{"--pid"}, "needs a value");
        assertThrows(IllegalArgumentException.class, () -> CommandLine.parse(new String[]{"-f", "yaml"}));
    }

    @Test
    void theUsageTextNamesEveryMajorOption() {
        String usage = CommandLine.usage();
        for (String option : new String[]{"--pid", "--jmx", "--format", "--only", "--skip", "--detail",
                "--expensive", "--stack-depth", "--redact", "--watch", "--load-agent", "-javaagent"}) {
            assertTrue(usage.contains(option), "usage does not mention " + option);
        }
    }

    private static void assertMessage(String argument, String expected) {
        assertMessage(new String[]{argument}, expected);
    }

    private static void assertMessage(String[] arguments, String expected) {
        CommandLineException failure =
                assertThrows(CommandLineException.class, () -> CommandLine.parse(arguments));
        assertTrue(failure.getMessage().contains(expected),
                () -> "message was: " + failure.getMessage());
    }
}
