package io.github.ctbot000.jvminspector.inspect;

import io.github.ctbot000.jvminspector.model.Section;
import io.github.ctbot000.jvminspector.model.Table;
import io.github.ctbot000.jvminspector.model.Value;
import io.github.ctbot000.jvminspector.util.Format;

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.RuntimeMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** How many classes are loaded, who loaded them, and where they came from. */
public final class ClassLoadingInspector implements Inspector {

    @Override
    public String id() {
        return "classes";
    }

    @Override
    public String title() {
        return "Class Loading";
    }

    @Override
    public String description() {
        return "Load counts, the class loader hierarchy, the class path, and the shared archive.";
    }

    @Override
    public Section inspect(InspectionContext context) {
        Section section = new Section(title());

        context.bean(ClassLoadingMXBean.class).ifPresent(classes -> {
            section.put("Currently loaded classes", Value.of(classes.getLoadedClassCount()));
            section.put("Total classes loaded since start", Value.of(classes.getTotalLoadedClassCount()));
            section.put("Classes unloaded", Value.of(classes.getUnloadedClassCount()));
            section.put("Verbose class loading", Value.of(classes.isVerbose()));
        });

        if (context.inProcess()) {
            loaderChain(section);
        }
        classPath(section, context);

        sharedArchive(section, context);

        Section commands = section.sub("Diagnostic command output");
        context.dcmd("VM.classloader_stats").ifPresent(output -> commands.code("VM.classloader_stats", output));
        context.dcmd("VM.classloaders").ifPresent(output -> commands.code("VM.classloaders", output));
        if (context.full()) {
            context.dcmd("VM.systemdictionary").ifPresent(output -> commands.code("VM.systemdictionary", output));
        }
        if (commands.isEmpty()) {
            commands.note("The target exposes no class loading diagnostic commands.");
        }
        return section;
    }

    private void loaderChain(Section section) {
        Section loaders = section.sub("Class loader hierarchy");
        loaders.description("The built-in loaders, from the application loader up to the bootstrap loader.");
        Table.Builder table = Table.builder("Loaders", "Level", "Name", "Class", "Parent");
        List<ClassLoader> chain = new ArrayList<>();
        for (ClassLoader loader = ClassLoader.getSystemClassLoader(); loader != null; loader = loader.getParent()) {
            chain.add(loader);
        }
        for (int index = 0; index < chain.size(); index++) {
            ClassLoader loader = chain.get(index);
            ClassLoader parent = loader.getParent();
            table.row(index, name(loader), loader.getClass().getName(),
                    parent == null ? "bootstrap" : name(parent));
        }
        table.row(chain.size(), "bootstrap", "(native, no Java object)", "-");
        loaders.table(table);
        loaders.put("This tool's loader", name(ClassLoadingInspector.class.getClassLoader()));
        loaders.put("Platform loader", name(ClassLoader.getPlatformClassLoader()));
        loaders.put("This tool's module", ClassLoadingInspector.class.getModule().toString());
    }

    /** Class data sharing: whether an archive is mapped, and which one. */
    private void sharedArchive(Section section, InspectionContext context) {
        Table.Builder table = Table.builder("Class data sharing flags", "Flag", "Value", "Origin");
        for (String flag : List.of("UseSharedSpaces", "RequireSharedSpaces", "SharedArchiveFile",
                "ArchiveClassesAtExit", "AutoCreateSharedArchive", "SharedClassListFile",
                "UseCompressedClassPointers", "SharedBaseAddress")) {
            context.vmOption(flag).ifPresent(option -> table.row(option.getName(),
                    option.getValue().isEmpty() ? "(unset)" : option.getValue(), option.getOrigin().toString()));
        }
        if (table.size() > 0) {
            section.sub("Class data sharing").table(table);
        }
    }

    private static String name(ClassLoader loader) {
        if (loader == null) {
            return "bootstrap";
        }
        return (loader.getName() == null ? "(unnamed)" : loader.getName()) + " [" + loader.getClass().getSimpleName() + "]";
    }

    private void classPath(Section section, InspectionContext context) {
        String path = context.bean(RuntimeMXBean.class).map(RuntimeMXBean::getClassPath)
                .orElseGet(() -> context.property("java.class.path", ""));
        List<String> entries = Format.splitPath(path);
        Section classPath = section.sub("Class path");
        classPath.put("Entries", Value.of(entries.size()));
        if (entries.isEmpty()) {
            classPath.note("The class path is empty; the application is most likely on the module path.");
            return;
        }
        Table.Builder table = Table.builder("Class path entries", "#", "Entry", "Exists", "Size");
        for (int index = 0; index < entries.size(); index++) {
            String entry = entries.get(index);
            boolean checkable = context.inProcess();
            Path file = checkable ? Path.of(entry) : null;
            boolean exists = checkable && Files.exists(file);
            table.row(index + 1, context.redactor().host(entry),
                    checkable ? Value.of(exists) : Value.absent("not checked remotely"),
                    exists && Files.isRegularFile(file) ? Value.bytesShort(size(file)) : Value.of("-"));
        }
        classPath.table(table);
    }

    private static long size(Path file) {
        try {
            return Files.size(file);
        } catch (Exception ignored) {
            return -1;
        }
    }
}
