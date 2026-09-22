package io.github.ctbot000.jvminspector.agent;

import java.lang.instrument.Instrumentation;
import java.util.Optional;

/**
 * Holds the {@link Instrumentation} handle the agent was given, so an inspection running in the
 * same JVM can pick it up.
 */
public final class AgentSupport {

    private static volatile Instrumentation instrumentation;
    private static volatile String mode;
    private static volatile long loadedAt;

    private AgentSupport() {
    }

    static void install(Instrumentation newInstrumentation, String newMode) {
        instrumentation = newInstrumentation;
        mode = newMode;
        loadedAt = System.currentTimeMillis();
    }

    public static Optional<Instrumentation> instrumentation() {
        return Optional.ofNullable(instrumentation);
    }

    public static String mode() {
        return mode == null ? "not loaded" : mode;
    }

    public static long loadedAt() {
        return loadedAt;
    }

    public static boolean isLoaded() {
        return instrumentation != null;
    }
}
