package io.github.ctbot000.jvminspector.target;

import javax.management.MBeanServerConnection;
import java.lang.management.ManagementFactory;
import java.lang.management.PlatformManagedObject;
import java.util.List;
import java.util.Optional;

/** Shared plumbing: platform beans and diagnostic commands read through an MBean connection. */
abstract class AbstractTarget implements Target {

    private DiagnosticCommands diagnostics;

    @Override
    public <T extends PlatformManagedObject> Optional<T> bean(Class<T> type) {
        try {
            return Optional.ofNullable(ManagementFactory.getPlatformMXBean(connection(), type));
        } catch (Exception failure) {
            return Optional.empty();
        }
    }

    @Override
    public <T extends PlatformManagedObject> List<T> beans(Class<T> type) {
        try {
            return ManagementFactory.getPlatformMXBeans(connection(), type);
        } catch (Exception failure) {
            return List.of();
        }
    }

    @Override
    public synchronized DiagnosticCommands diagnostics() {
        if (diagnostics == null) {
            diagnostics = DiagnosticCommands.discover(connection());
        }
        return diagnostics;
    }

    /** Reads an attribute by name, which is how beans that have no public interface are reached. */
    static Optional<Object> attribute(MBeanServerConnection connection, String objectName, String attribute) {
        try {
            return Optional.ofNullable(
                    connection.getAttribute(javax.management.ObjectName.getInstance(objectName), attribute));
        } catch (Exception failure) {
            return Optional.empty();
        }
    }

    @Override
    public void close() {
    }
}
