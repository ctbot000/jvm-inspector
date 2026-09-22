package io.github.ctbot000.jvminspector.inspect;

import com.sun.management.GcInfo;
import io.github.ctbot000.jvminspector.model.Section;
import io.github.ctbot000.jvminspector.model.Table;
import io.github.ctbot000.jvminspector.model.Value;
import io.github.ctbot000.jvminspector.util.Format;
import io.github.ctbot000.jvminspector.util.Mbeans;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.MemoryUsage;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Which collectors are installed, how much work they have done, and what the last cycle did. */
public final class GarbageCollectorInspector implements Inspector {

    @Override
    public String id() {
        return "gc";
    }

    @Override
    public String title() {
        return "Garbage Collection";
    }

    @Override
    public String description() {
        return "The installed collectors, their totals, and a breakdown of the most recent cycle.";
    }

    @Override
    public Section inspect(InspectionContext context) {
        Section section = new Section(title());
        List<? extends GarbageCollectorMXBean> collectors = collectors(context);
        if (collectors.isEmpty()) {
            return section.warn("The target publishes no garbage collector beans.");
        }

        section.put("Collector names",
                Value.list(collectors.stream().map(GarbageCollectorMXBean::getName).toList()));
        section.put("Collector count", Value.of(collectors.size()));

        Table.Builder totals = Table.builder("Totals since start", "Collector", "Valid", "Collections",
                "Total pause time", "Mean pause", "Pools");
        long allCollections = 0;
        long allMillis = 0;
        for (GarbageCollectorMXBean collector : collectors) {
            long count = collector.getCollectionCount();
            long millis = collector.getCollectionTime();
            allCollections += Math.max(0, count);
            allMillis += Math.max(0, millis);
            totals.row(collector.getName(), collector.isValid(),
                    count < 0 ? Value.unsupported() : Value.of(count),
                    millis < 0 ? Value.unsupported() : Value.millis(millis),
                    count > 0 && millis >= 0 ? Value.of(Format.duration(millis * 1_000_000L / count))
                            : Value.absent("no collections yet"),
                    String.join(", ", collector.getMemoryPoolNames()));
        }
        section.table(totals);
        section.put("Collections across all collectors", Value.of(allCollections));
        section.put("Pause time across all collectors", Value.millis(allMillis));

        lastCycles(section, context, collectors);
        context.dcmd("GC.heap_info").ifPresent(output -> section.code("GC.heap_info", output));
        if (context.expensive()) {
            context.dcmd("GC.class_histogram").ifPresent(output -> section.sub("Class histogram")
                    .description("Live object counts per class. This pauses the target.")
                    .code("GC.class_histogram", output));
        } else {
            section.note("Run with --expensive to include a class histogram, which pauses the target.");
        }
        return section;
    }

    /**
     * Prefers the {@code com.sun.management} view, which carries the last cycle detail, and falls
     * back to the portable one on a VM that does not publish it.
     */
    private static List<? extends GarbageCollectorMXBean> collectors(InspectionContext context) {
        List<com.sun.management.GarbageCollectorMXBean> extended =
                context.beans(com.sun.management.GarbageCollectorMXBean.class);
        return extended.isEmpty() ? context.beans(GarbageCollectorMXBean.class) : extended;
    }

    private void lastCycles(Section section, InspectionContext context,
                            List<? extends GarbageCollectorMXBean> collectors) {
        Section cycles = section.sub("Most recent cycle per collector");
        for (GarbageCollectorMXBean collector : collectors) {
            GcInfo info = lastGcInfo(collector);
            Section detail = cycles.sub(collector.getName());
            if (info == null) {
                detail.note("No collection has run yet, or this collector does not report cycle detail.");
                continue;
            }
            detail.put("Cycle id", Value.of(info.getId()));
            detail.put("Duration", Value.millis(info.getDuration()));
            detail.put("Started at (VM uptime)", Value.millis(info.getStartTime()));
            detail.put("Ended at (VM uptime)", Value.millis(info.getEndTime()));
            Mbeans.read(context.connection(),
                            "java.lang:type=GarbageCollector,name=" + collector.getName(), "LastGcInfo")
                    .filter(javax.management.openmbean.CompositeData.class::isInstance)
                    .map(javax.management.openmbean.CompositeData.class::cast)
                    .ifPresent(composite -> {
                        if (composite.containsKey("GcThreadCount")) {
                            detail.put("GC threads", Value.ofObject(composite.get("GcThreadCount")));
                        }
                    });

            Map<String, MemoryUsage> before = info.getMemoryUsageBeforeGc();
            Map<String, MemoryUsage> after = info.getMemoryUsageAfterGc();
            Table.Builder table = Table.builder("Pool usage across the cycle", "Pool", "Used before",
                    "Used after", "Reclaimed", "Committed after");
            for (String pool : new TreeSet<>(before.keySet())) {
                MemoryUsage start = before.get(pool);
                MemoryUsage end = after.get(pool);
                if (start == null || end == null) {
                    continue;
                }
                long reclaimed = start.getUsed() - end.getUsed();
                table.row(pool, Value.bytesShort(start.getUsed()), Value.bytesShort(end.getUsed()),
                        Value.of((reclaimed >= 0 ? "-" : "+") + Format.bytesShort(Math.abs(reclaimed))),
                        Value.bytesShort(end.getCommitted()));
            }
            detail.table(table);
        }
    }

    private static GcInfo lastGcInfo(GarbageCollectorMXBean collector) {
        try {
            if (collector instanceof com.sun.management.GarbageCollectorMXBean extended) {
                return extended.getLastGcInfo();
            }
        } catch (Exception ignored) {
            // Some collectors decline to report cycle detail; the totals above still stand.
        }
        return null;
    }
}
