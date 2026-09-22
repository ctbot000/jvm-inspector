package io.github.ctbot000.jvminspector;

import io.github.ctbot000.jvminspector.inspect.Detail;
import io.github.ctbot000.jvminspector.inspect.InspectionContext;
import io.github.ctbot000.jvminspector.inspect.InspectionOptions;
import io.github.ctbot000.jvminspector.inspect.Inspector;
import io.github.ctbot000.jvminspector.inspect.Inspectors;
import io.github.ctbot000.jvminspector.model.Report;
import io.github.ctbot000.jvminspector.model.Section;
import io.github.ctbot000.jvminspector.model.Table;
import io.github.ctbot000.jvminspector.model.Value;
import io.github.ctbot000.jvminspector.render.OutputFormat;
import io.github.ctbot000.jvminspector.target.LocalTarget;
import io.github.ctbot000.jvminspector.target.Target;
import io.github.ctbot000.jvminspector.util.Redactor;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Runs the inspectors against a target and assembles the report. */
public final class JvmInspector {

    public static final String NAME = "jvm-inspector";

    private JvmInspector() {
    }

    /** The version recorded in the jar manifest, or a development placeholder. */
    public static String version() {
        String version = JvmInspector.class.getPackage().getImplementationVersion();
        return version == null ? "1.0.0-dev" : version;
    }

    /** Which sections to run: an empty {@code only} means all of them. */
    public record Selection(Set<String> only, Set<String> skip) {

        public static Selection all() {
            return new Selection(Set.of(), Set.of());
        }

        boolean includes(String id) {
            return (only.isEmpty() || only.contains(id)) && !skip.contains(id);
        }
    }

    public static Report inspect(Target target, InspectionOptions options, Selection selection) {
        List<Section> sections = new ArrayList<>();
        Table.Builder timing = Table.builder("Sections", "Section", "Status", "Elapsed");

        for (Inspector inspector : Inspectors.all()) {
            if (!selection.includes(inspector.id())) {
                timing.row(inspector.id(), "not selected", "-");
                continue;
            }
            if (inspector.requiresInProcess() && !target.inProcess()) {
                Section skipped = new Section(inspector.title());
                skipped.description(inspector.description());
                skipped.note("This section reads the JDK API directly and can only be produced from inside"
                        + " the target, so it is empty for " + target.description() + ".");
                sections.add(skipped);
                timing.row(inspector.id(), "skipped (needs in-process access)", "-");
                continue;
            }

            long started = System.nanoTime();
            try {
                Section section = inspector.inspect(new InspectionContext(target, options));
                if (section.description() == null) {
                    section.description(inspector.description());
                }
                sections.add(section);
                timing.row(inspector.id(), "ok", Value.nanos(System.nanoTime() - started));
            } catch (Throwable failure) {
                Section broken = new Section(inspector.title());
                broken.description(inspector.description());
                broken.add(io.github.ctbot000.jvminspector.model.Note.error(
                        "This section failed: " + failure));
                broken.code("Stack trace", stackTrace(failure));
                sections.add(broken);
                timing.row(inspector.id(), "failed", Value.nanos(System.nanoTime() - started));
            }
        }

        Section run = new Section("Inspection Run");
        run.description("What this report collected, and how long each section took.");
        run.put("Tool", NAME + " " + version());
        run.put("Detail level", options.detail().name().toLowerCase(java.util.Locale.ROOT));
        run.put("Expensive operations", Value.of(options.expensive()));
        run.put("Stack depth", Value.of(options.stackDepth()));
        run.put("Redaction", options.redactor().mode().name().toLowerCase(java.util.Locale.ROOT));
        run.put("Sections reported", Value.of(sections.size()));
        run.table(timing);
        sections.add(run);

        return new Report(NAME, version(), target.description(), Instant.now(), sections);
    }

    /** Inspects this JVM and renders it, which is what the agent's dump option calls. */
    public static String renderSelf(String format, String detail, String redaction) {
        InspectionOptions options = new InspectionOptions(Detail.parse(detail), false, 12,
                new Redactor(Redactor.parseMode(redaction)));
        try (Target target = new LocalTarget()) {
            Report report = inspect(target, options, Selection.all());
            return OutputFormat.parse(format).renderer().renderToString(report);
        }
    }

    private static String stackTrace(Throwable failure) {
        StringWriter text = new StringWriter();
        failure.printStackTrace(new PrintWriter(text));
        return text.toString();
    }
}
