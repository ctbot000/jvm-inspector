package io.github.ctbot000.jvminspector.inspect;

import io.github.ctbot000.jvminspector.agent.InstrumentationDetailsMXBean;
import io.github.ctbot000.jvminspector.model.Section;
import io.github.ctbot000.jvminspector.model.Table;
import io.github.ctbot000.jvminspector.model.Value;
import io.github.ctbot000.jvminspector.util.Mbeans;

import java.lang.instrument.Instrumentation;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * What only a JVM agent can see: the true loaded class set, object header sizes, and which
 * redefinition capabilities this VM grants.
 */
public final class InstrumentationInspector implements Inspector {

    private static final int TOP_PACKAGES = 40;

    @Override
    public String id() {
        return "instrumentation";
    }

    @Override
    public String title() {
        return "Instrumentation";
    }

    @Override
    public String description() {
        return "Agent-only detail: every loaded class, by loader and by package, and object sizes.";
    }

    @Override
    public Section inspect(InspectionContext context) {
        Section section = new Section(title());
        Optional<Instrumentation> local = context.target().instrumentation();
        if (local.isPresent()) {
            fromInstrumentation(section, local.get(), context);
            return section;
        }
        if (readRemote(section, context)) {
            return section;
        }
        section.note("No agent is loaded in the target, so this section is empty.");
        section.note("Load one with -javaagent:jvm-inspector.jar at startup, or add --load-agent to an"
                + " inspection that uses --pid.");
        return section;
    }

    private void fromInstrumentation(Section section, Instrumentation instrumentation,
                                     InspectionContext context) {
        section.put("Agent mode", io.github.ctbot000.jvminspector.agent.AgentSupport.mode());
        section.put("Agent loaded at",
                Value.timestamp(io.github.ctbot000.jvminspector.agent.AgentSupport.loadedAt()));
        section.put("Can redefine classes", Value.of(instrumentation.isRedefineClassesSupported()));
        section.put("Can retransform classes", Value.of(instrumentation.isRetransformClassesSupported()));
        section.put("Can set a native method prefix",
                Value.of(instrumentation.isNativeMethodPrefixSupported()));

        Class<?>[] loaded = instrumentation.getAllLoadedClasses();
        section.put("Classes loaded (agent view)", Value.of(loaded.length));
        section.put("Size of a bare Object", Value.bytes(instrumentation.getObjectSize(new Object())));
        section.put("Size of an empty byte[]", Value.bytes(instrumentation.getObjectSize(new byte[0])));
        section.put("Size of an empty Object[]", Value.bytes(instrumentation.getObjectSize(new Object[0])));
        section.put("Size of a boxed Long", Value.bytes(instrumentation.getObjectSize(Long.valueOf(1))));

        Map<String, Integer> byLoader = new HashMap<>();
        Map<String, Integer> byPackage = new HashMap<>();
        int modifiable = 0;
        for (Class<?> type : loaded) {
            ClassLoader loader = type.getClassLoader();
            byLoader.merge(loader == null ? "<bootstrap>" : loader.getClass().getName(), 1, Integer::sum);
            Package definition = type.getPackage();
            byPackage.merge(definition == null ? "<default>" : definition.getName(), 1, Integer::sum);
            if (instrumentation.isModifiableClass(type)) {
                modifiable++;
            }
        }
        section.put("Modifiable classes", Value.of(modifiable));
        section.put("Distinct defining loaders", Value.of(byLoader.size()));
        section.put("Distinct packages", Value.of(byPackage.size()));

        section.table(histogram("Classes by defining loader", "Loader", byLoader, Integer.MAX_VALUE));
        int limit = context.full() ? Integer.MAX_VALUE : TOP_PACKAGES;
        section.table(histogram(context.full() ? "Classes by package"
                : "Classes by package (top " + TOP_PACKAGES + ")", "Package", byPackage, limit));
        if (!context.full()) {
            section.note("Run with --detail full to list every package.");
        }
    }

    /** Reads the bean the agent publishes, which is how a remote target answers this section. */
    private boolean readRemote(Section section, InspectionContext context) {
        Optional<javax.management.ObjectName> name = Mbeans.name(InstrumentationDetailsMXBean.OBJECT_NAME);
        if (name.isEmpty()) {
            return false;
        }
        Map<String, Object> attributes = Mbeans.readAll(context.connection(), name.get());
        if (attributes.isEmpty()) {
            return false;
        }
        section.note("Read from the agent bean published inside the target.");
        attributes.forEach((attribute, value) -> {
            if (value instanceof String[] lines) {
                Table.Builder table = Table.builder(attribute, "Count", "Name");
                Arrays.stream(lines).forEach(line -> {
                    String[] parts = line.split("\t", 2);
                    table.row(parts[0], parts.length > 1 ? parts[1] : "");
                });
                section.table(table);
            } else if ("AgentLoadedAt".equals(attribute) && value instanceof Number number) {
                section.put(attribute, Value.timestamp(number.longValue()));
            } else if (attribute.endsWith("Size") && value instanceof Number number) {
                section.put(attribute, Value.bytes(number.longValue()));
            } else {
                section.put(attribute, Value.ofObject(value));
            }
        });
        return true;
    }

    private static Table histogram(String title, String keyHeader, Map<String, Integer> counts, int limit) {
        Table.Builder table = Table.builder(title, "Count", keyHeader);
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .forEach(entry -> table.row(entry.getValue(), entry.getKey()));
        return table.build();
    }
}
