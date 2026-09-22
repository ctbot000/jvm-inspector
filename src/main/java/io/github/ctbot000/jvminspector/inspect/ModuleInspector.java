package io.github.ctbot000.jvminspector.inspect;

import io.github.ctbot000.jvminspector.model.Section;
import io.github.ctbot000.jvminspector.model.Table;
import io.github.ctbot000.jvminspector.model.Value;

import java.lang.module.ModuleDescriptor;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** The module graph the VM resolved at startup, module by module. */
public final class ModuleInspector implements Inspector {

    @Override
    public String id() {
        return "modules";
    }

    @Override
    public String title() {
        return "Modules";
    }

    @Override
    public String description() {
        return "The resolved boot layer: every module, what it requires, and what it exports.";
    }

    @Override
    public boolean requiresInProcess() {
        return true;
    }

    @Override
    public Section inspect(InspectionContext context) {
        Section section = new Section(title());
        ModuleLayer boot = ModuleLayer.boot();
        List<Module> modules = boot.modules().stream()
                .sorted(Comparator.comparing(Module::getName))
                .toList();

        section.put("Modules in the boot layer", Value.of(modules.size()));
        section.put("Parent layers", Value.of(boot.parents().size()));
        section.put("Automatic modules",
                Value.of(modules.stream().filter(module -> module.getDescriptor().isAutomatic()).count()));
        section.put("Open modules",
                Value.of(modules.stream().filter(module -> module.getDescriptor().isOpen()).count()));
        section.put("This tool's module", ModuleInspector.class.getModule().toString());
        section.put("Main module", context.property("jdk.module.main", "(none: started from the class path)"));
        section.put("Added modules", context.property("jdk.module.addmods", "(none)"));

        Table.Builder table = Table.builder("Resolved modules", "Module", "Version", "Kind", "Packages",
                "Requires", "Exports", "Opens", "Uses", "Provides", "Loader");
        for (Module module : modules) {
            ModuleDescriptor descriptor = module.getDescriptor();
            table.row(module.getName(),
                    descriptor.rawVersion().orElse("-"),
                    kind(descriptor),
                    descriptor.packages().size(),
                    descriptor.requires().size(),
                    descriptor.exports().size(),
                    descriptor.opens().size(),
                    descriptor.uses().size(),
                    descriptor.provides().size(),
                    loaderName(module.getClassLoader()));
        }
        section.table(table);

        if (context.full()) {
            Section detail = section.sub("Module detail");
            modules.forEach(module -> describe(detail.sub(module.getName()), module));
        } else {
            section.note("Run with --detail full for each module's requires, exports, opens and services.");
        }
        return section;
    }

    private static void describe(Section section, Module module) {
        ModuleDescriptor descriptor = module.getDescriptor();
        section.put("Kind", kind(descriptor));
        section.put("Version", descriptor.rawVersion().orElse("(none)"));
        section.put("Main class", descriptor.mainClass().orElse("(none)"));
        section.put("Loader", loaderName(module.getClassLoader()));
        section.put("Packages", Value.of(descriptor.packages().size()));

        section.put("Requires", Value.list(descriptor.requires().stream()
                .sorted(Comparator.comparing(ModuleDescriptor.Requires::name))
                .map(requires -> requires.name() + modifiers(requires.modifiers()))
                .toList()));
        section.put("Exports", Value.list(descriptor.exports().stream()
                .sorted(Comparator.comparing(ModuleDescriptor.Exports::source))
                .map(exports -> exports.source()
                        + (exports.isQualified() ? " to " + new TreeSet<>(exports.targets()) : ""))
                .toList()));
        section.put("Opens", Value.list(descriptor.opens().stream()
                .sorted(Comparator.comparing(ModuleDescriptor.Opens::source))
                .map(opens -> opens.source()
                        + (opens.isQualified() ? " to " + new TreeSet<>(opens.targets()) : ""))
                .toList()));
        section.put("Uses", Value.list(new TreeSet<>(descriptor.uses()).stream().toList()));
        section.put("Provides", Value.list(descriptor.provides().stream()
                .sorted(Comparator.comparing(ModuleDescriptor.Provides::service))
                .map(provides -> provides.service() + " with " + provides.providers())
                .toList()));
    }

    private static String modifiers(Set<ModuleDescriptor.Requires.Modifier> modifiers) {
        return modifiers.isEmpty() ? "" : " " + new TreeSet<>(modifiers.stream().map(Enum::toString).toList());
    }

    private static String kind(ModuleDescriptor descriptor) {
        if (descriptor.isAutomatic()) {
            return "automatic";
        }
        return descriptor.isOpen() ? "open" : "named";
    }

    private static String loaderName(ClassLoader loader) {
        if (loader == null) {
            return "bootstrap";
        }
        return loader.getName() == null ? loader.getClass().getSimpleName() : loader.getName();
    }
}
