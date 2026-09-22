package io.github.ctbot000.jvminspector.inspect;

import io.github.ctbot000.jvminspector.model.Section;
import io.github.ctbot000.jvminspector.model.Table;
import io.github.ctbot000.jvminspector.model.Value;
import io.github.ctbot000.jvminspector.util.Format;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Every system property the target holds, including the ones set on the command line. */
public final class SystemPropertiesInspector implements Inspector {

    private static final Set<String> PATH_PROPERTIES = Set.of(
            "java.class.path", "java.library.path", "sun.boot.library.path", "jdk.module.path",
            "jdk.module.upgrade.path", "java.endorsed.dirs", "java.ext.dirs");

    @Override
    public String id() {
        return "system-properties";
    }

    @Override
    public String title() {
        return "System Properties";
    }

    @Override
    public String description() {
        return "Every property in the target, with path-shaped values broken into their entries.";
    }

    @Override
    public Section inspect(InspectionContext context) {
        Section section = new Section(title());
        Map<String, String> properties = context.systemProperties();
        if (properties.isEmpty()) {
            return section.warn("The target reported no system properties.");
        }

        section.put("Property count", Value.of(properties.size()));
        long userDefined = properties.keySet().stream()
                .filter(name -> !name.startsWith("java.") && !name.startsWith("sun.")
                        && !name.startsWith("jdk.") && !name.startsWith("os.")
                        && !name.startsWith("user.") && !name.startsWith("file.")
                        && !name.startsWith("path.") && !name.startsWith("line.")
                        && !name.startsWith("native."))
                .count();
        section.put("Properties outside the JDK namespaces", Value.of(userDefined));

        Table.Builder table = Table.builder("All properties", "Property", "Value");
        properties.forEach((name, value) -> {
            if (PATH_PROPERTIES.contains(name)) {
                List<String> entries = Format.splitPath(value);
                table.row(name, entries.size() + " entries (listed below)");
            } else {
                table.row(name, Format.escape(context.redactor().host(context.redactor().named(name, value))));
            }
        });
        section.table(table);

        Section paths = section.sub("Path properties");
        for (String name : PATH_PROPERTIES) {
            String value = properties.get(name);
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
            paths.note("No path-shaped properties are set.");
        }
        return section;
    }
}
