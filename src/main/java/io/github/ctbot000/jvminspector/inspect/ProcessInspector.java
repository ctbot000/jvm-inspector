package io.github.ctbot000.jvminspector.inspect;

import io.github.ctbot000.jvminspector.model.Section;
import io.github.ctbot000.jvminspector.model.Table;
import io.github.ctbot000.jvminspector.model.Value;
import io.github.ctbot000.jvminspector.util.Format;

import java.lang.management.RuntimeMXBean;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** The operating system process: its identity, its command line and its ancestry. */
public final class ProcessInspector implements Inspector {

    @Override
    public String id() {
        return "process";
    }

    @Override
    public String title() {
        return "Process";
    }

    @Override
    public String description() {
        return "The process the VM is running as, its parent, and the command that started it.";
    }

    @Override
    public Section inspect(InspectionContext context) {
        Section section = new Section(title());
        context.bean(RuntimeMXBean.class)
                .ifPresent(runtime -> section.put("Process id", Value.of(runtime.getPid())));

        if (!context.inProcess()) {
            section.note("Process detail beyond the process id is only readable from inside the target;"
                    + " the command line below comes from the target's own system properties.");
            section.put("sun.java.command",
                    context.redactor().text(context.property("sun.java.command", "(not set)")));
            return section;
        }

        ProcessHandle current = ProcessHandle.current();
        describe(section, context, "This process", current);

        current.parent().ifPresentOrElse(
                parent -> describe(section.sub("Parent process"), context, "Parent", parent),
                () -> section.note("This process has no visible parent."));

        List<ProcessHandle> children = current.children().toList();
        section.put("Direct children", Value.of(children.size()));
        if (!children.isEmpty()) {
            Table.Builder table = Table.builder("Child processes", "PID", "Command", "Started");
            for (ProcessHandle child : children) {
                ProcessHandle.Info info = child.info();
                table.row(child.pid(),
                        context.redactor().host(info.command().orElse("(unknown)")),
                        info.startInstant().map(Format::timestamp).orElse("(unknown)"));
            }
            section.table(table);
        }
        return section;
    }

    private void describe(Section section, InspectionContext context, String label, ProcessHandle handle) {
        ProcessHandle.Info info = handle.info();
        section.put(label + " pid", Value.of(handle.pid()));
        section.put(label + " alive", Value.of(handle.isAlive()));
        section.put(label + " user",
                context.redactor().host(info.user().orElse("(unknown)")));
        section.put(label + " command",
                context.redactor().host(info.command().orElse("(unknown)")));
        Optional<String> commandLine = info.commandLine();
        section.put(label + " command line", commandLine
                .map(line -> context.redactor().host(context.redactor().text(line)))
                .map(Value::of)
                .orElseGet(() -> Value.absent("not exposed by this platform")));
        info.arguments().ifPresent(arguments -> section.put(label + " arguments",
                Value.list(Arrays.stream(arguments)
                        .map(argument -> context.redactor().host(context.redactor().text(argument)))
                        .toList())));
        section.put(label + " started", info.startInstant()
                .map(instant -> Value.timestamp(instant.toEpochMilli()))
                .orElseGet(() -> Value.absent("not exposed by this platform")));
        info.startInstant().ifPresent(start -> section.put(label + " age",
                Value.millis(Instant.now().toEpochMilli() - start.toEpochMilli())));
        section.put(label + " cpu time", info.totalCpuDuration()
                .map(duration -> Value.nanos(duration.toNanos()))
                .orElseGet(() -> Value.absent("not exposed by this platform")));
    }
}
