package io.github.ctbot000.jvminspector.inspect;

import io.github.ctbot000.jvminspector.model.Section;
import io.github.ctbot000.jvminspector.model.Table;
import io.github.ctbot000.jvminspector.model.Value;
import io.github.ctbot000.jvminspector.target.DiagnosticCommands;

import java.util.List;
import java.util.Map;

/**
 * The VM's own diagnostic commands - the set {@code jcmd} drives - listed in full, with the safe
 * read-only ones executed.
 */
public final class DiagnosticsInspector implements Inspector {

    /** Commands that only read state, run on every inspection. */
    private static final List<Command> CHEAP = List.of(
            new Command("VM.version", new String[0]),
            new Command("VM.uptime", new String[0]),
            new Command("VM.metaspace", new String[]{"basic"}),
            new Command("VM.stringtable", new String[0]),
            new Command("VM.symboltable", new String[0]));

    /** Commands that are read-only but produce a great deal of output. */
    private static final List<Command> VERBOSE = List.of(
            new Command("VM.dynlibs", new String[0]),
            new Command("VM.events", new String[0]),
            new Command("VM.info", new String[0]),
            new Command("System.map", new String[0]));

    private record Command(String name, String[] arguments) {
    }

    @Override
    public String id() {
        return "diagnostics";
    }

    @Override
    public String title() {
        return "Diagnostic Commands";
    }

    @Override
    public String description() {
        return "Every jcmd command the target supports, with the read-only ones executed.";
    }

    @Override
    public Section inspect(InspectionContext context) {
        Section section = new Section(title());
        DiagnosticCommands diagnostics = context.diagnostics();
        if (!diagnostics.isAvailable()) {
            return section.warn("This target publishes no diagnostic command bean, so jcmd style commands"
                    + " are not reachable. A VM started with -XX:+DisableAttachMechanism behaves this way.");
        }

        Map<String, DiagnosticCommands.Command> commands = diagnostics.commands();
        section.put("Commands available", Value.of(commands.size()));
        Table.Builder table = Table.builder("Supported commands", "Command", "Impact", "Takes arguments",
                "Description");
        commands.values().forEach(command -> table.row(command.name(), command.impact(),
                command.takesArguments(), command.description()));
        section.table(table);

        Section output = section.sub("Command output");
        run(output, context, CHEAP);
        if (context.full() || context.expensive()) {
            run(output, context, VERBOSE);
        } else {
            output.note("Run with --detail full to add " + VERBOSE.stream().map(Command::name).toList()
                    + ", which are read-only but long.");
        }
        return section;
    }

    private void run(Section section, InspectionContext context, List<Command> commands) {
        for (Command command : commands) {
            context.dcmd(command.name(), command.arguments()).ifPresent(result -> {
                String title = command.arguments().length == 0 ? command.name()
                        : command.name() + " " + String.join(" ", command.arguments());
                section.code(title, context.redactor().host(context.redactor().text(result)));
            });
        }
    }
}
