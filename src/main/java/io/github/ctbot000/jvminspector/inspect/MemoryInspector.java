package io.github.ctbot000.jvminspector.inspect;

import io.github.ctbot000.jvminspector.model.Section;
import io.github.ctbot000.jvminspector.model.Table;
import io.github.ctbot000.jvminspector.model.Value;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryManagerMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.List;

/** Heap, non-heap, every memory pool, every manager, and the off-heap buffer pools. */
public final class MemoryInspector implements Inspector {

    @Override
    public String id() {
        return "memory";
    }

    @Override
    public String title() {
        return "Memory";
    }

    @Override
    public String description() {
        return "Heap and non-heap usage, the individual pools behind them, and off-heap buffers.";
    }

    @Override
    public Section inspect(InspectionContext context) {
        Section section = new Section(title());

        context.bean(MemoryMXBean.class).ifPresent(memory -> {
            usage(section.sub("Heap"), memory.getHeapMemoryUsage());
            usage(section.sub("Non-heap"), memory.getNonHeapMemoryUsage());
            Section finalization = section.sub("Finalization");
            finalization.put("Objects pending finalization",
                    Value.of(memory.getObjectPendingFinalizationCount()));
            finalization.put("Verbose GC logging", Value.of(memory.isVerbose()));
        });

        if (context.inProcess()) {
            Runtime runtime = Runtime.getRuntime();
            Section view = section.sub("Runtime view");
            view.description("What java.lang.Runtime reports, which is the heap only.");
            view.put("Runtime.totalMemory()", Value.bytes(runtime.totalMemory()));
            view.put("Runtime.freeMemory()", Value.bytes(runtime.freeMemory()));
            view.put("Runtime.maxMemory()", Value.bytes(runtime.maxMemory()));
            view.put("Runtime.availableProcessors()", Value.of(runtime.availableProcessors()));
        }

        pools(section, context);
        managers(section, context);
        bufferPools(section, context);
        commands(section, context);
        return section;
    }

    private static void usage(Section section, MemoryUsage usage) {
        section.put("Initial", Value.bytes(usage.getInit()));
        section.put("Used", Value.bytes(usage.getUsed()));
        section.put("Committed", Value.bytes(usage.getCommitted()));
        section.put("Maximum", usage.getMax() < 0 ? Value.absent("unbounded") : Value.bytes(usage.getMax()));
        if (usage.getMax() > 0) {
            section.put("Used of maximum", Value.percent((double) usage.getUsed() / usage.getMax()));
        }
        if (usage.getCommitted() > 0) {
            section.put("Used of committed", Value.percent((double) usage.getUsed() / usage.getCommitted()));
        }
    }

    private void pools(Section section, InspectionContext context) {
        List<MemoryPoolMXBean> pools = context.beans(MemoryPoolMXBean.class);
        if (pools.isEmpty()) {
            return;
        }
        Section poolSection = section.sub("Memory pools");
        Table.Builder table = Table.builder("Pools", "Pool", "Type", "Used", "Committed", "Max", "Peak used",
                "Managers");
        for (MemoryPoolMXBean pool : pools) {
            MemoryUsage usage = pool.getUsage();
            MemoryUsage peak = pool.getPeakUsage();
            table.row(pool.getName(),
                    pool.getType() == java.lang.management.MemoryType.HEAP ? "heap" : "non-heap",
                    usage == null ? Value.unsupported() : Value.bytesShort(usage.getUsed()),
                    usage == null ? Value.unsupported() : Value.bytesShort(usage.getCommitted()),
                    usage == null || usage.getMax() < 0 ? Value.absent("unbounded")
                            : Value.bytesShort(usage.getMax()),
                    peak == null ? Value.unsupported() : Value.bytesShort(peak.getUsed()),
                    String.join(", ", pool.getMemoryManagerNames()));
        }
        poolSection.table(table);

        for (MemoryPoolMXBean pool : pools) {
            Section detail = poolSection.sub(pool.getName());
            detail.put("Valid", Value.of(pool.isValid()));
            detail.put("Type", pool.getType().toString());
            detail.put("Managers", Value.list(List.of(pool.getMemoryManagerNames())));
            MemoryUsage usage = pool.getUsage();
            if (usage != null) {
                usage(detail.sub("Current usage"), usage);
            }
            MemoryUsage peak = pool.getPeakUsage();
            if (peak != null) {
                usage(detail.sub("Peak usage"), peak);
            }
            MemoryUsage afterCollection = pool.getCollectionUsage();
            if (afterCollection != null) {
                usage(detail.sub("Usage after the last collection"), afterCollection);
            }
            thresholds(detail, pool);
        }
    }

    private static void thresholds(Section section, MemoryPoolMXBean pool) {
        Section thresholds = section.sub("Thresholds");
        if (pool.isUsageThresholdSupported()) {
            thresholds.put("Usage threshold", Value.bytes(pool.getUsageThreshold()));
            thresholds.put("Usage threshold exceeded", Value.of(pool.isUsageThresholdExceeded()));
            thresholds.put("Usage threshold crossings", Value.of(pool.getUsageThresholdCount()));
        } else {
            thresholds.put("Usage threshold", Value.unsupported());
        }
        if (pool.isCollectionUsageThresholdSupported()) {
            thresholds.put("Collection usage threshold", Value.bytes(pool.getCollectionUsageThreshold()));
            thresholds.put("Collection threshold exceeded", Value.of(pool.isCollectionUsageThresholdExceeded()));
            thresholds.put("Collection threshold crossings", Value.of(pool.getCollectionUsageThresholdCount()));
        } else {
            thresholds.put("Collection usage threshold", Value.unsupported());
        }
    }

    private void managers(Section section, InspectionContext context) {
        List<MemoryManagerMXBean> managers = context.beans(MemoryManagerMXBean.class);
        if (managers.isEmpty()) {
            return;
        }
        Table.Builder table = Table.builder("Memory managers", "Manager", "Valid", "Pools managed");
        for (MemoryManagerMXBean manager : managers) {
            table.row(manager.getName(), manager.isValid(), String.join(", ", manager.getMemoryPoolNames()));
        }
        section.sub("Memory managers").table(table);
    }

    private void bufferPools(Section section, InspectionContext context) {
        List<BufferPoolMXBean> pools = context.beans(BufferPoolMXBean.class);
        if (pools.isEmpty()) {
            return;
        }
        Section buffers = section.sub("Buffer pools");
        buffers.description("Off-heap memory the VM has handed to NIO buffers.");
        Table.Builder table = Table.builder("Buffer pools", "Pool", "Buffers", "Memory used", "Total capacity");
        for (BufferPoolMXBean pool : pools) {
            table.row(pool.getName(), pool.getCount(), Value.bytesShort(pool.getMemoryUsed()),
                    Value.bytesShort(pool.getTotalCapacity()));
        }
        buffers.table(table);
    }

    private void commands(Section section, InspectionContext context) {
        Section commands = section.sub("Diagnostic command output");
        context.dcmd("GC.heap_info").ifPresent(output -> commands.code("GC.heap_info", output));
        context.dcmd("VM.metaspace", "basic").ifPresent(output -> commands.code("VM.metaspace basic", output));
        context.dcmd("VM.native_memory", "summary").ifPresent(output -> commands.code(
                "VM.native_memory summary", output));
        if (commands.isEmpty()) {
            section.note("The target exposes no memory related diagnostic commands.");
        } else {
            commands.note("Native memory tracking needs -XX:NativeMemoryTracking=summary at VM startup.");
        }
    }
}
