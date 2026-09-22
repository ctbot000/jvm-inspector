package io.github.ctbot000.jvminspector.target;

import io.github.ctbot000.jvminspector.agent.AgentSupport;

import javax.management.MBeanServerConnection;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.lang.management.PlatformManagedObject;
import java.util.List;
import java.util.Optional;

/** The JVM the inspector is running in. Beans are used directly rather than through a proxy. */
public final class LocalTarget extends AbstractTarget {

    private final MBeanServerConnection connection = ManagementFactory.getPlatformMBeanServer();

    @Override
    public String description() {
        return "this JVM (pid " + ProcessHandle.current().pid() + ")";
    }

    @Override
    public boolean inProcess() {
        return true;
    }

    @Override
    public MBeanServerConnection connection() {
        return connection;
    }

    @Override
    public <T extends PlatformManagedObject> Optional<T> bean(Class<T> type) {
        try {
            return Optional.ofNullable(ManagementFactory.getPlatformMXBean(type));
        } catch (Exception failure) {
            return super.bean(type);
        }
    }

    @Override
    public <T extends PlatformManagedObject> List<T> beans(Class<T> type) {
        try {
            return ManagementFactory.getPlatformMXBeans(type);
        } catch (Exception failure) {
            return super.beans(type);
        }
    }

    @Override
    public Optional<Instrumentation> instrumentation() {
        return AgentSupport.instrumentation();
    }
}
