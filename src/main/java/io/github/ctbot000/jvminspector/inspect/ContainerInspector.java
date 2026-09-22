package io.github.ctbot000.jvminspector.inspect;

import io.github.ctbot000.jvminspector.model.Section;
import io.github.ctbot000.jvminspector.model.Table;
import io.github.ctbot000.jvminspector.model.Value;

import java.lang.management.OperatingSystemMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Container awareness: the limits the VM sized itself against.
 *
 * <p>A JVM in a container sizes its heap and its thread pools from cgroup limits rather than from
 * the host, and a mismatch between the two is a common source of surprise, so both are reported.
 */
public final class ContainerInspector implements Inspector {

    private static final List<String> SIZING_FLAGS = List.of(
            "UseContainerSupport", "ActiveProcessorCount", "MaxRAM", "MaxRAMPercentage",
            "MinRAMPercentage", "InitialRAMPercentage", "MaxHeapSize", "InitialHeapSize", "MinHeapSize",
            "MaxMetaspaceSize", "MaxDirectMemorySize", "SoftMaxHeapSize", "CompressedClassSpaceSize");

    private static final List<String> CGROUP_V2_FILES = List.of(
            "/sys/fs/cgroup/memory.max", "/sys/fs/cgroup/memory.high", "/sys/fs/cgroup/memory.current",
            "/sys/fs/cgroup/cpu.max", "/sys/fs/cgroup/cpuset.cpus.effective", "/sys/fs/cgroup/pids.max");

    private static final List<String> CGROUP_V1_FILES = List.of(
            "/sys/fs/cgroup/memory/memory.limit_in_bytes",
            "/sys/fs/cgroup/memory/memory.usage_in_bytes",
            "/sys/fs/cgroup/cpu/cpu.cfs_quota_us",
            "/sys/fs/cgroup/cpu/cpu.cfs_period_us",
            "/sys/fs/cgroup/cpu/cpu.shares",
            "/sys/fs/cgroup/cpuset/cpuset.cpus");

    @Override
    public String id() {
        return "container";
    }

    @Override
    public String title() {
        return "Container and Resource Limits";
    }

    @Override
    public String description() {
        return "The CPU and memory limits the VM sized itself against, and the cgroup behind them.";
    }

    @Override
    public Section inspect(InspectionContext context) {
        Section section = new Section(title());

        Section view = section.sub("What the VM decided");
        context.bean(OperatingSystemMXBean.class).ifPresent(os ->
                view.put("Processors the VM will use", Value.of(os.getAvailableProcessors())));
        Table.Builder flags = Table.builder("Sizing flags", "Flag", "Value", "Origin");
        for (String flag : SIZING_FLAGS) {
            context.vmOption(flag).ifPresent(option -> flags.row(option.getName(),
                    interpret(option.getName(), option.getValue()), option.getOrigin().toString()));
        }
        view.table(flags);

        if (!context.inProcess()) {
            section.note("The cgroup files behind these numbers can only be read inside the target.");
            return section;
        }

        String osName = context.property("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (!osName.contains("linux")) {
            section.note("This host runs " + context.property("os.name", "an unknown system")
                    + ", which has no cgroups; the flags above are the whole story.");
            return section;
        }

        Section cgroup = section.sub("cgroup");
        cgroup.put("Docker marker file (/.dockerenv)", Value.of(Files.exists(Path.of("/.dockerenv"))));
        cgroup.put("Podman marker file (/run/.containerenv)",
                Value.of(Files.exists(Path.of("/run/.containerenv"))));
        read(Path.of("/proc/1/cgroup")).ifPresent(content -> cgroup.code("/proc/1/cgroup", content));
        read(Path.of("/proc/self/cgroup")).ifPresent(content -> cgroup.code("/proc/self/cgroup", content));

        Table.Builder limits = Table.builder("cgroup limit files", "File", "Value");
        java.util.stream.Stream.concat(CGROUP_V2_FILES.stream(), CGROUP_V1_FILES.stream())
                .forEach(file -> read(Path.of(file))
                        .ifPresent(value -> limits.row(file, value.replace('\n', ' ').trim())));
        if (limits.size() == 0) {
            cgroup.note("No cgroup limit files are readable, so this process is most likely not"
                    + " constrained by one.");
        } else {
            cgroup.table(limits);
        }
        read(Path.of("/proc/meminfo")).ifPresent(content -> cgroup.code("/proc/meminfo (first lines)",
                content.lines().limit(8).reduce("", (left, right) -> left.isEmpty() ? right : left + "\n" + right)));
        return section;
    }

    /** Turns raw flag values into something a reader can act on. */
    private static String interpret(String name, String value) {
        if (name.endsWith("Size") || "MaxRAM".equals(name)) {
            try {
                long bytes = Long.parseLong(value);
                return bytes <= 0 ? value : io.github.ctbot000.jvminspector.util.Format.bytes(bytes);
            } catch (NumberFormatException ignored) {
                return value;
            }
        }
        return value;
    }

    private static Optional<String> read(Path path) {
        try {
            return Files.isReadable(path) ? Optional.of(Files.readString(path).stripTrailing()) : Optional.empty();
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}
