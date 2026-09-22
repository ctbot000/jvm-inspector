package io.github.ctbot000.jvminspector.target;

import org.junit.jupiter.api.Test;

import java.lang.management.MemoryPoolMXBean;
import java.lang.management.RuntimeMXBean;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalTargetTest {

    @Test
    void theLocalTargetNamesItselfAndServesBeans() {
        try (Target target = new LocalTarget()) {
            assertTrue(target.inProcess());
            assertTrue(target.description().contains(String.valueOf(ProcessHandle.current().pid())));
            Optional<RuntimeMXBean> runtime = target.bean(RuntimeMXBean.class);
            assertTrue(runtime.isPresent());
            assertEquals(ProcessHandle.current().pid(), runtime.get().getPid());
            assertFalse(target.beans(MemoryPoolMXBean.class).isEmpty());
        }
    }

    @Test
    void diagnosticCommandsAreDiscoveredAndRunnable() {
        try (Target target = new LocalTarget()) {
            DiagnosticCommands diagnostics = target.diagnostics();
            assertTrue(diagnostics.isAvailable(), "a HotSpot JVM publishes the diagnostic command bean");
            assertTrue(diagnostics.supports("VM.version"));
            assertTrue(diagnostics.commands().get("VM.version").operation().startsWith("vm"));

            String version = diagnostics.execute("VM.version").orElseThrow();
            assertTrue(version.contains("VM"), () -> "unexpected VM.version output: " + version);
            assertTrue(diagnostics.execute("No.Such.Command").isEmpty());
            assertTrue(diagnostics.executeFirst(java.util.List.of("No.Such.Command", "VM.uptime")).isPresent());
        }
    }

    @Test
    void aJmxUrlIsNormalisedFromHostAndPort() {
        assertEquals("service:jmx:rmi:///jndi/rmi://localhost:9010/jmxrmi",
                JmxTarget.normalise("localhost:9010"));
        assertEquals("service:jmx:custom://x", JmxTarget.normalise("service:jmx:custom://x"));
    }

    @Test
    void attachingToThisJvmIsRefusedWithAClearMessage() {
        java.io.IOException failure = org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class,
                () -> AttachTarget.attach(ProcessHandle.current().pid()));
        assertTrue(failure.getMessage().contains("own JVM"));
    }
}
