package io.github.ctbot000.jvminspector.target;

import javax.management.MBeanServerConnection;
import java.lang.instrument.Instrumentation;
import java.lang.management.PlatformManagedObject;
import java.util.List;
import java.util.Optional;

/**
 * The JVM being inspected: this one, a local one reached with the attach API, or a remote one
 * reached over JMX. Everything an inspector needs goes through here, so a single inspector body
 * serves all three.
 */
public interface Target extends AutoCloseable {

    /** A human readable identification of the target, printed at the top of the report. */
    String description();

    /**
     * Whether the inspector is running inside the target. In-process targets can use the full JDK
     * API - modules, security providers, file stores - while remote ones are limited to what the
     * management beans and diagnostic commands expose.
     */
    boolean inProcess();

    MBeanServerConnection connection();

    /** A platform management bean of the given type, absent when the target does not publish it. */
    <T extends PlatformManagedObject> Optional<T> bean(Class<T> type);

    /** Every instance of a multi-instance platform bean, such as the memory pools. */
    <T extends PlatformManagedObject> List<T> beans(Class<T> type);

    DiagnosticCommands diagnostics();

    /** The {@link Instrumentation} handle, present only when the agent is loaded in this JVM. */
    default Optional<Instrumentation> instrumentation() {
        return Optional.empty();
    }

    @Override
    void close();
}
