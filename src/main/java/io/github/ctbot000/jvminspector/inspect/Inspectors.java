package io.github.ctbot000.jvminspector.inspect;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The inspectors that make up a report, in the order they are reported. */
public final class Inspectors {

    private static final List<Inspector> ALL = List.of(
            new OverviewInspector(),
            new VmInspector(),
            new ProcessInspector(),
            new OperatingSystemInspector(),
            new ContainerInspector(),
            new MemoryInspector(),
            new GarbageCollectorInspector(),
            new ThreadInspector(),
            new ClassLoadingInspector(),
            new CompilationInspector(),
            new ModuleInspector(),
            new VmFlagsInspector(),
            new SystemPropertiesInspector(),
            new EnvironmentInspector(),
            new FileSystemInspector(),
            new NetworkInspector(),
            new SecurityInspector(),
            new LocaleInspector(),
            new FlightRecorderInspector(),
            new InstrumentationInspector(),
            new DiagnosticsInspector(),
            new MBeanInspector());

    private Inspectors() {
    }

    public static List<Inspector> all() {
        return ALL;
    }

    public static Map<String, Inspector> byId() {
        Map<String, Inspector> byId = new LinkedHashMap<>();
        ALL.forEach(inspector -> byId.put(inspector.id(), inspector));
        return byId;
    }

    public static List<String> ids() {
        return ALL.stream().map(Inspector::id).toList();
    }
}
