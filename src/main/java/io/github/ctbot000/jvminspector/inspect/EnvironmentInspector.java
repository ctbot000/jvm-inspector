package io.github.ctbot000.jvminspector.inspect;

import io.github.ctbot000.jvminspector.model.Section;
import io.github.ctbot000.jvminspector.model.Table;
import io.github.ctbot000.jvminspector.model.Value;
import io.github.ctbot000.jvminspector.util.Format;
import io.github.ctbot000.jvminspector.util.Redactor;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The process environment.
 *
 * <p>Environment variables are the most likely place in a report to be carrying a credential, so
 * names that look like secrets are masked unless {@code --redact none} was asked for.
 */
public final class EnvironmentInspector implements Inspector {

    private static final Set<String> PATH_VARIABLES = Set.of("PATH", "CLASSPATH", "LD_LIBRARY_PATH",
            "DYLD_LIBRARY_PATH", "PYTHONPATH", "MANPATH", "INFOPATH");

    private static final List<String> JAVA_VARIABLES = List.of("JAVA_HOME", "JDK_HOME", "JRE_HOME",
            "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "JAVA_OPTS", "_JAVA_OPTIONS", "CLASSPATH",
            "JAVA_VERSION", "JAVA_DEBUG", "MALLOC_ARENA_MAX");

    @Override
    public String id() {
        return "environment";
    }

    @Override
    public String title() {
        return "Environment";
    }

    @Override
    public String description() {
        return "The environment variables the process was started with, secrets masked.";
    }

    @Override
    public boolean requiresInProcess() {
        return true;
    }

    @Override
    public Section inspect(InspectionContext context) {
        Section section = new Section(title());
        Map<String, String> environment = new TreeMap<>(System.getenv());
        section.put("Variable count", Value.of(environment.size()));
        section.put("Redaction mode", context.redactor().mode().toString().toLowerCase(java.util.Locale.ROOT));

        Section java = section.sub("Variables the JVM itself reads");
        Table.Builder javaTable = Table.builder("Java environment", "Variable", "Value");
        for (String name : JAVA_VARIABLES) {
            String value = environment.get(name);
            javaTable.row(name, value == null ? "(not set)" : display(context.redactor(), name, value));
        }
        java.table(javaTable);
        if (environment.containsKey("JAVA_TOOL_OPTIONS") || environment.containsKey("_JAVA_OPTIONS")
                || environment.containsKey("JDK_JAVA_OPTIONS")) {
            java.warn("This JVM picks up options from the environment, so its command line alone does not"
                    + " describe how it was configured.");
        }

        Table.Builder table = Table.builder("All variables", "Variable", "Value");
        environment.forEach((name, value) -> {
            if (PATH_VARIABLES.contains(name)) {
                table.row(name, Format.splitPath(value).size() + " entries (listed below)");
            } else {
                table.row(name, display(context.redactor(), name, value));
            }
        });
        section.table(table);

        Section paths = section.sub("Path variables");
        for (String name : PATH_VARIABLES) {
            String value = environment.get(name);
            if (value == null || value.isBlank()) {
                continue;
            }
            List<String> entries = Format.splitPath(value);
            Table.Builder pathTable = Table.builder(name, "#", "Entry");
            for (int index = 0; index < entries.size(); index++) {
                pathTable.row(index + 1, context.redactor().host(entries.get(index)));
            }
            paths.table(pathTable);
        }
        if (paths.isEmpty()) {
            paths.note("No path-shaped variables are set.");
        }
        return section;
    }

    private static String display(Redactor redactor, String name, String value) {
        return Format.escape(redactor.host(redactor.named(name, value)));
    }
}
