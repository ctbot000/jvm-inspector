package io.github.ctbot000.jvminspector.web;

import io.github.ctbot000.jvminspector.target.Target;
import io.github.ctbot000.jvminspector.util.Json;
import io.github.ctbot000.jvminspector.util.Mbeans;

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.CompilationMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.util.StringJoiner;

/**
 * The handful of numbers worth re-reading every couple of seconds, as compact JSON.
 *
 * <p>A full report is far too heavy to poll, so the live view has its own endpoint that touches
 * only the beans whose values actually move.
 */
final class LiveMetrics {

    private LiveMetrics() {
    }

    static String snapshot(Target target) {
        StringBuilder json = new StringBuilder("{");
        json.append("\"timestamp\":").append(System.currentTimeMillis());

        target.bean(RuntimeMXBean.class).ifPresent(runtime ->
                json.append(",\"uptimeMillis\":").append(runtime.getUptime())
                        .append(",\"startTime\":").append(runtime.getStartTime()));

        target.bean(MemoryMXBean.class).ifPresent(memory -> {
            json.append(",\"heap\":").append(usage(memory.getHeapMemoryUsage()));
            json.append(",\"nonHeap\":").append(usage(memory.getNonHeapMemoryUsage()));
            json.append(",\"pendingFinalization\":").append(memory.getObjectPendingFinalizationCount());
        });

        target.bean(ThreadMXBean.class).ifPresent(threads ->
                json.append(",\"threads\":{\"live\":").append(threads.getThreadCount())
                        .append(",\"daemon\":").append(threads.getDaemonThreadCount())
                        .append(",\"peak\":").append(threads.getPeakThreadCount())
                        .append(",\"started\":").append(threads.getTotalStartedThreadCount())
                        .append('}'));

        target.bean(ClassLoadingMXBean.class).ifPresent(classes ->
                json.append(",\"classes\":{\"loaded\":").append(classes.getLoadedClassCount())
                        .append(",\"total\":").append(classes.getTotalLoadedClassCount())
                        .append(",\"unloaded\":").append(classes.getUnloadedClassCount())
                        .append('}'));

        target.bean(CompilationMXBean.class).ifPresent(compilation -> {
            if (compilation.isCompilationTimeMonitoringSupported()) {
                json.append(",\"compilationMillis\":").append(compilation.getTotalCompilationTime());
            }
        });

        StringJoiner collectors = new StringJoiner(",", "[", "]");
        long collections = 0;
        long pauseMillis = 0;
        for (GarbageCollectorMXBean collector : target.beans(GarbageCollectorMXBean.class)) {
            long count = Math.max(0, collector.getCollectionCount());
            long millis = Math.max(0, collector.getCollectionTime());
            collections += count;
            pauseMillis += millis;
            collectors.add("{\"name\":" + Json.quote(collector.getName())
                    + ",\"count\":" + count + ",\"timeMillis\":" + millis + "}");
        }
        json.append(",\"gc\":{\"collections\":").append(collections)
                .append(",\"pauseMillis\":").append(pauseMillis)
                .append(",\"collectors\":").append(collectors).append('}');

        StringJoiner pools = new StringJoiner(",", "[", "]");
        for (MemoryPoolMXBean pool : target.beans(MemoryPoolMXBean.class)) {
            MemoryUsage usage = pool.getUsage();
            if (usage == null) {
                continue;
            }
            pools.add("{\"name\":" + Json.quote(pool.getName())
                    + ",\"type\":" + Json.quote(pool.getType() == MemoryType.HEAP ? "heap" : "non-heap")
                    + ",\"used\":" + usage.getUsed()
                    + ",\"committed\":" + usage.getCommitted()
                    + ",\"max\":" + usage.getMax() + "}");
        }
        json.append(",\"pools\":").append(pools);

        target.bean(OperatingSystemMXBean.class).ifPresent(os ->
                json.append(",\"cpu\":{\"processors\":").append(os.getAvailableProcessors())
                        .append(",\"loadAverage\":").append(finite(os.getSystemLoadAverage()))
                        .append(",\"processLoad\":").append(fraction(target, "ProcessCpuLoad"))
                        .append(",\"systemLoad\":").append(fraction(target, "CpuLoad"))
                        .append('}'));

        return json.append('}').toString();
    }

    private static String usage(MemoryUsage usage) {
        return "{\"init\":" + usage.getInit() + ",\"used\":" + usage.getUsed()
                + ",\"committed\":" + usage.getCommitted() + ",\"max\":" + usage.getMax() + "}";
    }

    /** Reads an operating system load attribute generically, since its name moved between releases. */
    private static String fraction(Target target, String attribute) {
        Object value = Mbeans.read(target.connection(), "java.lang:type=OperatingSystem", attribute)
                .orElseGet(() -> Mbeans.read(target.connection(), "java.lang:type=OperatingSystem",
                        "SystemCpuLoad").orElse(null));
        return value instanceof Number number ? finite(number.doubleValue()) : "null";
    }

    private static String finite(double value) {
        return Double.isFinite(value) && value >= 0 ? Double.toString(value) : "null";
    }
}
