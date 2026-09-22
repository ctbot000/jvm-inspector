package io.github.ctbot000.jvminspector.inspect;

import io.github.ctbot000.jvminspector.model.Section;
import io.github.ctbot000.jvminspector.model.Value;

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.CompilationMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.util.List;

/** The one screen worth reading first: what this JVM is, and how it is doing right now. */
public final class OverviewInspector implements Inspector {

    @Override
    public String id() {
        return "overview";
    }

    @Override
    public String title() {
        return "Overview";
    }

    @Override
    public String description() {
        return "The headline facts, pulled from the sections that follow.";
    }

    @Override
    public Section inspect(InspectionContext context) {
        Section section = new Section(title());

        context.bean(RuntimeMXBean.class).ifPresent(runtime -> {
            section.put("Java version", context.property("java.version", "unknown"));
            section.put("Java runtime", context.property("java.runtime.version", "unknown"));
            section.put("Java vendor", context.property("java.vendor", "unknown"));
            section.put("VM", runtime.getVmName() + " " + runtime.getVmVersion());
            section.put("VM mode", context.property("java.vm.info", "unknown"));
            section.put("VM vendor", runtime.getVmVendor());
            section.put("Process id", Value.of(runtime.getPid()));
            section.put("Started", Value.timestamp(runtime.getStartTime()));
            section.put("Uptime", Value.millis(runtime.getUptime()));
        });

        context.bean(OperatingSystemMXBean.class).ifPresent(os -> {
            section.put("Operating system", os.getName() + " " + os.getVersion() + " (" + os.getArch() + ")");
            section.put("Available processors", Value.of(os.getAvailableProcessors()));
            double load = os.getSystemLoadAverage();
            section.put("System load average", load < 0 ? Value.unsupported() : Value.of(load));
        });

        context.bean(MemoryMXBean.class).ifPresent(memory -> {
            MemoryUsage heap = memory.getHeapMemoryUsage();
            section.put("Heap used", Value.bytes(heap.getUsed()));
            section.put("Heap committed", Value.bytes(heap.getCommitted()));
            section.put("Heap max", heap.getMax() < 0 ? Value.absent("unbounded") : Value.bytes(heap.getMax()));
            if (heap.getMax() > 0) {
                section.put("Heap utilisation", Value.percent((double) heap.getUsed() / heap.getMax()));
            }
            section.put("Non-heap used", Value.bytes(memory.getNonHeapMemoryUsage().getUsed()));
        });

        context.bean(ThreadMXBean.class).ifPresent(threads -> section
                .put("Live threads", Value.of(threads.getThreadCount()))
                .put("Daemon threads", Value.of(threads.getDaemonThreadCount()))
                .put("Peak threads", Value.of(threads.getPeakThreadCount())));

        context.bean(ClassLoadingMXBean.class).ifPresent(classes -> section
                .put("Loaded classes", Value.of(classes.getLoadedClassCount()))
                .put("Total classes loaded", Value.of(classes.getTotalLoadedClassCount()))
                .put("Unloaded classes", Value.of(classes.getUnloadedClassCount())));

        List<GarbageCollectorMXBean> collectors = context.beans(GarbageCollectorMXBean.class);
        if (!collectors.isEmpty()) {
            long count = 0;
            long millis = 0;
            for (GarbageCollectorMXBean collector : collectors) {
                count += Math.max(0, collector.getCollectionCount());
                millis += Math.max(0, collector.getCollectionTime());
            }
            section.put("Garbage collectors",
                    Value.list(collectors.stream().map(GarbageCollectorMXBean::getName).toList()));
            section.put("Collections so far", Value.of(count));
            section.put("Time in collections", Value.millis(millis));
        }

        context.bean(CompilationMXBean.class).ifPresent(compilation -> {
            section.put("JIT compiler", compilation.getName());
            if (compilation.isCompilationTimeMonitoringSupported()) {
                section.put("Time compiling", Value.millis(compilation.getTotalCompilationTime()));
            }
        });

        section.put("Inspected from", ManagementFactory.getRuntimeMXBean().getName()
                + (context.inProcess() ? " (in process)" : " (remote)"));
        return section;
    }
}
