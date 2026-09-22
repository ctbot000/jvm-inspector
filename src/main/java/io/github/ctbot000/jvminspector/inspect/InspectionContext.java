package io.github.ctbot000.jvminspector.inspect;

import io.github.ctbot000.jvminspector.target.DiagnosticCommands;
import io.github.ctbot000.jvminspector.target.Target;
import io.github.ctbot000.jvminspector.util.Redactor;

import javax.management.MBeanServerConnection;
import java.lang.management.PlatformManagedObject;
import java.lang.management.RuntimeMXBean;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Everything an inspector is given: the target, the options, and shortcuts onto both. */
public final class InspectionContext {

    private final Target target;
    private final InspectionOptions options;
    private volatile Map<String, String> systemProperties;

    public InspectionContext(Target target, InspectionOptions options) {
        this.target = target;
        this.options = options;
    }

    public Target target() {
        return target;
    }

    public InspectionOptions options() {
        return options;
    }

    public Redactor redactor() {
        return options.redactor();
    }

    public boolean full() {
        return options.full();
    }

    public boolean expensive() {
        return options.expensive();
    }

    public int stackDepth() {
        return options.stackDepth();
    }

    public boolean inProcess() {
        return target.inProcess();
    }

    public MBeanServerConnection connection() {
        return target.connection();
    }

    public <T extends PlatformManagedObject> Optional<T> bean(Class<T> type) {
        return target.bean(type);
    }

    public <T extends PlatformManagedObject> List<T> beans(Class<T> type) {
        return target.beans(type);
    }

    public DiagnosticCommands diagnostics() {
        return target.diagnostics();
    }

    /** Runs a diagnostic command against the target. */
    public Optional<String> dcmd(String name, String... arguments) {
        return target.diagnostics().execute(name, arguments);
    }

    /**
     * The target's system properties, read through its runtime bean so the same call works for a
     * remote JVM. Read once and cached, because several sections want them.
     */
    public Map<String, String> systemProperties() {
        Map<String, String> cached = systemProperties;
        if (cached == null) {
            cached = bean(RuntimeMXBean.class)
                    .map(runtime -> (Map<String, String>) new TreeMap<>(runtime.getSystemProperties()))
                    .orElseGet(TreeMap::new);
            systemProperties = cached;
        }
        return cached;
    }

    /** Reads a single HotSpot VM option, absent when the VM has no such flag. */
    public Optional<com.sun.management.VMOption> vmOption(String name) {
        return bean(com.sun.management.HotSpotDiagnosticMXBean.class).flatMap(diagnostic -> {
            try {
                return Optional.ofNullable(diagnostic.getVMOption(name));
            } catch (RuntimeException ignored) {
                return Optional.empty();
            }
        });
    }

    public String property(String name) {
        return systemProperties().get(name);
    }

    public String property(String name, String fallback) {
        return systemProperties().getOrDefault(name, fallback);
    }
}
