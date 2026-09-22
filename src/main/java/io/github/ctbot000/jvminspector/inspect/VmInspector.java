package io.github.ctbot000.jvminspector.inspect;

import io.github.ctbot000.jvminspector.model.Section;
import io.github.ctbot000.jvminspector.model.Table;
import io.github.ctbot000.jvminspector.model.Value;
import io.github.ctbot000.jvminspector.util.Format;

import java.lang.management.RuntimeMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Identity and launch configuration: which VM this is, and exactly how it was started. */
public final class VmInspector implements Inspector {

    @Override
    public String id() {
        return "vm";
    }

    @Override
    public String title() {
        return "Virtual Machine";
    }

    @Override
    public String description() {
        return "Version, specification, launch arguments and the paths the VM was given.";
    }

    @Override
    public Section inspect(InspectionContext context) {
        Section section = new Section(title());
        RuntimeMXBean runtime = context.bean(RuntimeMXBean.class).orElse(null);
        if (runtime == null) {
            return section.warn("The target does not publish a runtime management bean.");
        }

        Section identity = section.sub("Identity");
        identity.put("Management name", runtime.getName());
        identity.put("Process id", Value.of(runtime.getPid()));
        identity.put("VM name", runtime.getVmName());
        identity.put("VM vendor", runtime.getVmVendor());
        identity.put("VM version", runtime.getVmVersion());
        identity.put("VM info", context.property("java.vm.info", "unknown"));
        identity.put("VM specification", runtime.getSpecName() + " " + runtime.getSpecVersion()
                + " (" + runtime.getSpecVendor() + ")");
        identity.put("Management spec version", runtime.getManagementSpecVersion());

        Section release = section.sub("Java release");
        release.put("java.version", context.property("java.version", "unknown"));
        release.put("java.version.date", context.property("java.version.date", "unknown"));
        release.put("java.runtime.name", context.property("java.runtime.name", "unknown"));
        release.put("java.runtime.version", context.property("java.runtime.version", "unknown"));
        release.put("java.vendor", context.property("java.vendor", "unknown"));
        release.put("java.vendor.url", context.property("java.vendor.url", "unknown"));
        release.put("java.vendor.version", context.property("java.vendor.version", "(not set)"));
        release.put("java.class.version", context.property("java.class.version", "unknown"));
        release.put("java.specification.version", context.property("java.specification.version", "unknown"));
        release.put("java.home", context.redactor().host(context.property("java.home", "unknown")));
        if (context.inProcess()) {
            Runtime.Version version = Runtime.version();
            release.put("Feature", Value.of(version.feature()));
            release.put("Interim", Value.of(version.interim()));
            release.put("Update", Value.of(version.update()));
            release.put("Patch", Value.of(version.patch()));
            release.put("Pre-release", version.pre().orElse("(none)"));
            release.put("Build", version.build().map(String::valueOf).orElse("(none)"));
            release.put("Optional", version.optional().orElse("(none)"));
        }

        Section lifecycle = section.sub("Lifecycle");
        lifecycle.put("Start time", Value.timestamp(runtime.getStartTime()));
        lifecycle.put("Uptime", Value.millis(runtime.getUptime()));

        List<String> arguments = runtime.getInputArguments().stream()
                .map(argument -> context.redactor().named(argument, argument))
                .map(argument -> context.redactor().text(argument))
                .toList();
        Section launch = section.sub("Launch");
        launch.put("Input argument count", Value.of(arguments.size()));
        if (arguments.isEmpty()) {
            launch.note("The VM was started with no explicit arguments.");
        } else {
            Table.Builder table = Table.builder("Input arguments (-X, -XX, -D, -agent)", "#", "Argument");
            for (int index = 0; index < arguments.size(); index++) {
                table.row(index + 1, arguments.get(index));
            }
            launch.table(table);
        }
        launch.put("sun.java.command",
                context.redactor().text(context.property("sun.java.command", "(not set)")));
        launch.put("sun.java.launcher", context.property("sun.java.launcher", "(not set)"));
        context.dcmd("VM.command_line").ifPresent(output -> launch.code("VM.command_line", context.redactor().text(output)));

        Section paths = section.sub("Paths");
        addPath(paths, context, "Class path", runtime.getClassPath());
        addPath(paths, context, "Library path", runtime.getLibraryPath());
        if (runtime.isBootClassPathSupported()) {
            addPath(paths, context, "Boot class path", runtime.getBootClassPath());
        } else {
            paths.put("Boot class path", Value.unsupported());
        }
        addPath(paths, context, "Module path", context.property("jdk.module.path", ""));
        addPath(paths, context, "Upgrade module path", context.property("jdk.module.upgrade.path", ""));

        if (context.inProcess()) {
            readReleaseFile(context).ifPresent(content -> section.sub("release file")
                    .description("The metadata file shipped in java.home, naming the exact build.")
                    .code("$JAVA_HOME/release", content));
        }
        return section;
    }

    private static void addPath(Section section, InspectionContext context, String name, String path) {
        List<String> entries = Format.splitPath(path);
        if (entries.isEmpty()) {
            section.put(name, Value.absent("empty"));
            return;
        }
        if (entries.size() == 1) {
            section.put(name, context.redactor().host(entries.get(0)));
            return;
        }
        Table.Builder table = Table.builder(name, "#", "Entry");
        for (int index = 0; index < entries.size(); index++) {
            table.row(index + 1, context.redactor().host(entries.get(index)));
        }
        section.table(table);
    }

    private static java.util.Optional<String> readReleaseFile(InspectionContext context) {
        String home = context.property("java.home");
        if (home == null) {
            return java.util.Optional.empty();
        }
        Path file = Path.of(home, "release");
        try {
            return Files.isReadable(file) ? java.util.Optional.of(Files.readString(file).stripTrailing())
                    : java.util.Optional.empty();
        } catch (Exception failure) {
            return java.util.Optional.empty();
        }
    }
}
