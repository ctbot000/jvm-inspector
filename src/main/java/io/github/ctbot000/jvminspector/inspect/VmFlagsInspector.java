package io.github.ctbot000.jvminspector.inspect;

import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.VMOption;
import io.github.ctbot000.jvminspector.model.Section;
import io.github.ctbot000.jvminspector.model.Table;
import io.github.ctbot000.jvminspector.model.Value;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Every {@code -XX} flag the VM holds, with the value it ended up with and where that value came
 * from - a default, the command line, ergonomics, or a management call.
 */
public final class VmFlagsInspector implements Inspector {

    /** {@code     bool UseCompressedOops   = true   {lp64_product} {ergonomic}} */
    private static final Pattern FLAG = Pattern.compile(
            "^\\s*(\\S+)\\s+(\\S+)\\s*(:?=)\\s*(.*?)\\s*((?:\\{[^}]*}\\s*)+)$");

    /** One parsed line of {@code VM.flags -all}. */
    record Flag(String type, String name, String value, String category, String origin, boolean changed) {
    }

    @Override
    public String id() {
        return "flags";
    }

    @Override
    public String title() {
        return "VM Flags";
    }

    @Override
    public String description() {
        return "The complete -XX flag set, grouped by where each value came from.";
    }

    @Override
    public Section inspect(InspectionContext context) {
        Section section = new Section(title());

        context.dcmd("VM.flags").ifPresent(output ->
                section.code("VM.flags (as given on the command line)", output));

        List<Flag> flags = context.dcmd("VM.flags", "-all").map(VmFlagsInspector::parse).orElseGet(List::of);
        if (flags.isEmpty()) {
            section.note("This VM does not expose its full flag set; only the diagnostic options below"
                    + " are readable.");
        } else {
            summarise(section, flags);
            List<Flag> nonDefault = flags.stream()
                    .filter(flag -> flag.changed() || !"default".equals(flag.origin()))
                    .sorted(Comparator.comparing(Flag::name))
                    .toList();
            section.table(toTable("Flags that are not at their default", nonDefault));
            if (context.full()) {
                section.table(toTable("All flags",
                        flags.stream().sorted(Comparator.comparing(Flag::name)).toList()));
            } else {
                section.note("Run with --detail full to list all " + flags.size() + " flags.");
            }
        }

        diagnosticOptions(section, context);
        return section;
    }

    private static void summarise(Section section, List<Flag> flags) {
        section.put("Flags reported", Value.of(flags.size()));
        Map<String, Integer> byOrigin = new TreeMap<>();
        Map<String, Integer> byCategory = new TreeMap<>();
        for (Flag flag : flags) {
            byOrigin.merge(flag.origin(), 1, Integer::sum);
            byCategory.merge(flag.category(), 1, Integer::sum);
        }
        Table.Builder origins = Table.builder("Flags by origin", "Origin", "Count");
        byOrigin.forEach(origins::row);
        section.table(origins);
        Table.Builder categories = Table.builder("Flags by category", "Category", "Count");
        byCategory.forEach(categories::row);
        section.table(categories);
    }

    private static Table toTable(String title, List<Flag> flags) {
        Table.Builder table = Table.builder(title, "Flag", "Value", "Type", "Category", "Origin");
        for (Flag flag : flags) {
            table.row(flag.name(), flag.value().isEmpty() ? "(empty)" : flag.value(), flag.type(),
                    flag.category(), flag.origin());
        }
        return table.build();
    }

    private void diagnosticOptions(Section section, InspectionContext context) {
        context.bean(HotSpotDiagnosticMXBean.class).ifPresentOrElse(diagnostic -> {
            List<VMOption> options;
            try {
                options = diagnostic.getDiagnosticOptions();
            } catch (RuntimeException failure) {
                section.warn("The diagnostic options could not be read: " + failure);
                return;
            }
            Section writeable = section.sub("Writeable diagnostic options");
            writeable.description("Flags that can be changed on a running VM through the management interface.");
            Table.Builder table = Table.builder("Diagnostic options", "Flag", "Value", "Writeable", "Origin");
            options.stream()
                    .sorted(Comparator.comparing(VMOption::getName))
                    .forEach(option -> table.row(option.getName(), option.getValue(), option.isWriteable(),
                            option.getOrigin().toString()));
            writeable.table(table);
        }, () -> section.note("The target does not publish the HotSpot diagnostic bean."));
    }

    /** Parses the {@code VM.flags -all} table. Lines that do not match are skipped, not guessed at. */
    static List<Flag> parse(String output) {
        List<Flag> flags = new ArrayList<>();
        for (String line : output.split("\\R")) {
            Matcher matcher = FLAG.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            List<String> tags = tags(matcher.group(5));
            String origin = tags.isEmpty() ? "unknown" : tags.get(tags.size() - 1);
            String category = tags.size() > 1 ? String.join(" ", tags.subList(0, tags.size() - 1)) : "product";
            flags.add(new Flag(matcher.group(1), matcher.group(2), matcher.group(4), category, origin,
                    ":=".equals(matcher.group(3))));
        }
        return flags;
    }

    private static List<String> tags(String text) {
        List<String> tags = new ArrayList<>();
        Matcher matcher = Pattern.compile("\\{([^}]*)}").matcher(text);
        while (matcher.find()) {
            tags.add(matcher.group(1).trim());
        }
        return tags;
    }
}
