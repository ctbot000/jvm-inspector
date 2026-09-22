package io.github.ctbot000.jvminspector.inspect;

import io.github.ctbot000.jvminspector.model.Section;
import io.github.ctbot000.jvminspector.model.Value;
import io.github.ctbot000.jvminspector.util.Mbeans;

import javax.management.ObjectName;
import java.util.Map;
import java.util.Optional;

/**
 * The host as the VM sees it.
 *
 * <p>The operating system bean grows platform specific attributes - file descriptor limits on Unix,
 * different memory attributes on different JDK releases - so it is read generically and every
 * attribute the target publishes is reported, whether or not this build knows about it.
 */
public final class OperatingSystemInspector implements Inspector {

    private static final String OS_BEAN = "java.lang:type=OperatingSystem";

    @Override
    public String id() {
        return "os";
    }

    @Override
    public String title() {
        return "Operating System";
    }

    @Override
    public String description() {
        return "Every attribute the operating system bean publishes, including the platform specific ones.";
    }

    @Override
    public Section inspect(InspectionContext context) {
        Section section = new Section(title());
        Optional<ObjectName> name = Mbeans.name(OS_BEAN);
        if (name.isEmpty()) {
            return section.warn("The operating system bean is not available.");
        }
        Map<String, Object> attributes = Mbeans.readAll(context.connection(), name.get());
        if (attributes.isEmpty()) {
            return section.warn("The operating system bean published no readable attributes.");
        }

        for (Map.Entry<String, Object> attribute : attributes.entrySet()) {
            if ("ObjectName".equals(attribute.getKey())) {
                continue;
            }
            section.put(attribute.getKey(), interpret(attribute.getKey(), attribute.getValue()));
        }

        derived(section, attributes);
        return section;
    }

    /** Applies units the bean itself does not carry: sizes are bytes, loads are fractions. */
    private static Value interpret(String name, Object raw) {
        if (raw instanceof Number number) {
            if (name.endsWith("Size")) {
                return Value.bytes(number.longValue());
            }
            if (name.endsWith("CpuTime")) {
                return Value.nanos(number.longValue());
            }
            if (name.endsWith("Load") || name.endsWith("LoadAverage")) {
                double value = number.doubleValue();
                return value < 0 ? Value.unsupported()
                        : name.endsWith("LoadAverage") ? Value.of(value) : Value.percent(value);
            }
        }
        return Value.ofObject(raw);
    }

    /** Adds the figures a reader would otherwise work out with a calculator. */
    private static void derived(Section section, Map<String, Object> attributes) {
        Section computed = section.sub("Derived");
        long totalMemory = longOf(attributes, "TotalMemorySize", "TotalPhysicalMemorySize");
        long freeMemory = longOf(attributes, "FreeMemorySize", "FreePhysicalMemorySize");
        if (totalMemory > 0 && freeMemory >= 0) {
            computed.put("Physical memory in use", Value.bytes(totalMemory - freeMemory));
            computed.put("Physical memory utilisation",
                    Value.percent((double) (totalMemory - freeMemory) / totalMemory));
        }
        long totalSwap = longOf(attributes, "TotalSwapSpaceSize");
        long freeSwap = longOf(attributes, "FreeSwapSpaceSize");
        if (totalSwap > 0 && freeSwap >= 0) {
            computed.put("Swap in use", Value.bytes(totalSwap - freeSwap));
            computed.put("Swap utilisation", Value.percent((double) (totalSwap - freeSwap) / totalSwap));
        }
        long openFiles = longOf(attributes, "OpenFileDescriptorCount");
        long maxFiles = longOf(attributes, "MaxFileDescriptorCount");
        if (openFiles >= 0 && maxFiles > 0) {
            computed.put("File descriptors in use", Value.of(openFiles + " of " + maxFiles));
            computed.put("File descriptor utilisation", Value.percent((double) openFiles / maxFiles));
        }
        if (computed.isEmpty()) {
            computed.note("This target publishes none of the attributes these figures are derived from.");
        }
    }

    private static long longOf(Map<String, Object> attributes, String... names) {
        for (String name : names) {
            if (attributes.get(name) instanceof Number number) {
                return number.longValue();
            }
        }
        return -1;
    }
}
