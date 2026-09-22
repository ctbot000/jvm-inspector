package io.github.ctbot000.jvminspector.cli;

import io.github.ctbot000.jvminspector.target.Target;
import io.github.ctbot000.jvminspector.util.Format;

import java.io.PrintStream;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * A repeating one-line sample of the target, for watching a JVM move rather than photographing it.
 *
 * <p>Counters that only grow - collections, collection time, classes loaded - are printed as the
 * change since the previous sample, because that is the number worth watching.
 */
public final class Watcher {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);
    private static final String HEADER = String.format(Locale.ROOT,
            "%-8s %10s %10s %6s %10s %7s %7s %9s %7s %9s %7s",
            "time", "heap used", "heap max", "use%", "non-heap", "threads", "daemon", "classes",
            "+loaded", "collections", "+pause");

    private final Target target;
    private final PrintStream output;

    public Watcher(Target target, PrintStream output) {
        this.target = target;
        this.output = output;
    }

    /**
     * Prints samples until the sample budget runs out, or forever when {@code samples} is zero.
     *
     * @return the number of samples printed
     */
    public int watch(int intervalSeconds, int samples) throws InterruptedException {
        output.println("Watching " + target.description() + " every " + intervalSeconds
                + "s. Press Ctrl-C to stop.");
        output.println(HEADER);

        long previousLoaded = -1;
        long previousPause = -1;
        int printed = 0;

        while (samples == 0 || printed < samples) {
            if (printed > 0) {
                Thread.sleep(intervalSeconds * 1000L);
            }
            MemoryUsage heap = target.bean(MemoryMXBean.class)
                    .map(MemoryMXBean::getHeapMemoryUsage).orElse(null);
            MemoryUsage nonHeap = target.bean(MemoryMXBean.class)
                    .map(MemoryMXBean::getNonHeapMemoryUsage).orElse(null);
            ThreadMXBean threads = target.bean(ThreadMXBean.class).orElse(null);
            ClassLoadingMXBean classes = target.bean(ClassLoadingMXBean.class).orElse(null);

            long collections = 0;
            long pause = 0;
            for (GarbageCollectorMXBean collector : target.beans(GarbageCollectorMXBean.class)) {
                collections += Math.max(0, collector.getCollectionCount());
                pause += Math.max(0, collector.getCollectionTime());
            }
            long loaded = classes == null ? -1 : classes.getTotalLoadedClassCount();

            output.println(String.format(Locale.ROOT,
                    "%-8s %10s %10s %6s %10s %7s %7s %9s %7s %9s %7s",
                    CLOCK.format(LocalTime.now()),
                    heap == null ? "-" : Format.bytesShort(heap.getUsed()),
                    heap == null || heap.getMax() < 0 ? "-" : Format.bytesShort(heap.getMax()),
                    heap == null || heap.getMax() <= 0 ? "-"
                            : Format.percent((double) heap.getUsed() / heap.getMax()),
                    nonHeap == null ? "-" : Format.bytesShort(nonHeap.getUsed()),
                    threads == null ? "-" : threads.getThreadCount(),
                    threads == null ? "-" : threads.getDaemonThreadCount(),
                    classes == null ? "-" : classes.getLoadedClassCount(),
                    delta(previousLoaded, loaded),
                    collections,
                    previousPause < 0 ? "-" : (pause - previousPause) + "ms"));

            previousLoaded = loaded;
            previousPause = pause;
            printed++;
        }
        return printed;
    }

    /** Lists the JVMs on this machine, the way {@code jps} does. */
    public static void listJvms(PrintStream output) {
        List<com.sun.tools.attach.VirtualMachineDescriptor> machines =
                io.github.ctbot000.jvminspector.target.AttachTarget.list();
        if (machines.isEmpty()) {
            output.println("No attachable JVMs were found. A JVM started by another user, or one started"
                    + " with -XX:+DisableAttachMechanism, will not appear here.");
            return;
        }
        output.printf(Locale.ROOT, "%-10s %s%n", "PID", "Display name");
        machines.stream()
                .sorted(java.util.Comparator.comparing(com.sun.tools.attach.VirtualMachineDescriptor::id))
                .forEach(machine -> output.printf(Locale.ROOT, "%-10s %s%n", machine.id(),
                        machine.displayName().isBlank() ? "(unknown)" : machine.displayName()));
    }

    private static String delta(long previous, long current) {
        if (previous < 0 || current < 0) {
            return "-";
        }
        return "+" + (current - previous);
    }

    /** Exposed so a test can assert the header shape without running a sample loop. */
    public static String header() {
        return HEADER;
    }

}
