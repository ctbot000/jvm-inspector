package io.github.ctbot000.jvminspector.inspect;

import io.github.ctbot000.jvminspector.model.Section;
import io.github.ctbot000.jvminspector.model.Table;
import io.github.ctbot000.jvminspector.model.Value;

import java.lang.management.LockInfo;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Every platform thread: what it is doing, what it is waiting for, and what it has consumed. */
public final class ThreadInspector implements Inspector {

    private static final int FULL_STACK_DEPTH = 256;

    @Override
    public String id() {
        return "threads";
    }

    @Override
    public String title() {
        return "Threads";
    }

    @Override
    public String description() {
        return "Counts, per-thread state and cost, deadlock detection and stack traces.";
    }

    @Override
    public Section inspect(InspectionContext context) {
        Section section = new Section(title());
        ThreadMXBean threads = context.bean(ThreadMXBean.class).orElse(null);
        if (threads == null) {
            return section.warn("The target publishes no thread management bean.");
        }

        Section counts = section.sub("Counts");
        counts.put("Live threads", Value.of(threads.getThreadCount()));
        counts.put("Daemon threads", Value.of(threads.getDaemonThreadCount()));
        counts.put("Non-daemon threads", Value.of(threads.getThreadCount() - threads.getDaemonThreadCount()));
        counts.put("Peak live threads", Value.of(threads.getPeakThreadCount()));
        counts.put("Threads started since VM start", Value.of(threads.getTotalStartedThreadCount()));

        Section capabilities = section.sub("Measurement support");
        capabilities.put("CPU time supported", Value.of(threads.isThreadCpuTimeSupported()));
        capabilities.put("CPU time enabled", Value.of(threads.isThreadCpuTimeEnabled()));
        capabilities.put("Current thread CPU time supported",
                Value.of(threads.isCurrentThreadCpuTimeSupported()));
        capabilities.put("Contention monitoring supported",
                Value.of(threads.isThreadContentionMonitoringSupported()));
        capabilities.put("Contention monitoring enabled",
                Value.of(threads.isThreadContentionMonitoringEnabled()));
        capabilities.put("Object monitor usage supported", Value.of(threads.isObjectMonitorUsageSupported()));
        capabilities.put("Synchronizer usage supported", Value.of(threads.isSynchronizerUsageSupported()));

        Optional<com.sun.management.ThreadMXBean> extended = extended(context);
        extended.ifPresent(bean -> {
            try {
                capabilities.put("Allocation measurement supported",
                        Value.of(bean.isThreadAllocatedMemorySupported()));
                capabilities.put("Allocation measurement enabled",
                        Value.of(bean.isThreadAllocatedMemoryEnabled()));
                io.github.ctbot000.jvminspector.util.Mbeans
                        .read(context.connection(), "java.lang:type=Threading", "TotalThreadAllocatedBytes")
                        .filter(Number.class::isInstance)
                        .ifPresent(total -> capabilities.put("Bytes allocated by all threads",
                                Value.bytes(((Number) total).longValue())));
            } catch (RuntimeException ignored) {
                capabilities.put("Allocation measurement supported", Value.unsupported());
            }
        });

        deadlocks(section, threads, context);
        threadTable(section, context, threads, extended);
        stacks(section, context, threads);

        if (majorVersion(context) >= 21) {
            section.note("Virtual threads are not reported here: the thread management bean covers"
                    + " platform threads only. Use a JFR recording or Thread.dump_to_file for those.");
        }
        if (context.full()) {
            context.dcmd("Thread.print", "-l").ifPresent(output -> section.sub("Raw thread dump")
                    .code("Thread.print -l", output));
        }
        return section;
    }

    private static Optional<com.sun.management.ThreadMXBean> extended(InspectionContext context) {
        return context.bean(com.sun.management.ThreadMXBean.class);
    }

    private void deadlocks(Section section, ThreadMXBean threads, InspectionContext context) {
        Section deadlocks = section.sub("Deadlocks");
        long[] monitorDeadlocked = safely(threads::findMonitorDeadlockedThreads);
        long[] allDeadlocked = safely(threads::findDeadlockedThreads);
        if (allDeadlocked.length == 0 && monitorDeadlocked.length == 0) {
            deadlocks.note("No deadlocked threads were found.");
            return;
        }
        deadlocks.warn("The VM reports " + allDeadlocked.length + " deadlocked thread(s), "
                + monitorDeadlocked.length + " of them on object monitors.");
        ThreadInfo[] infos = threads.getThreadInfo(allDeadlocked.length > 0 ? allDeadlocked : monitorDeadlocked,
                true, true);
        for (ThreadInfo info : infos) {
            if (info != null) {
                deadlocks.code(info.getThreadName() + " (id " + info.getThreadId() + ")",
                        render(info, context.full() ? FULL_STACK_DEPTH : context.stackDepth()));
            }
        }
    }

    private void threadTable(Section section, InspectionContext context, ThreadMXBean threads,
                             Optional<com.sun.management.ThreadMXBean> extended) {
        long[] ids = threads.getAllThreadIds();
        Arrays.sort(ids);
        ThreadInfo[] infos = threads.getThreadInfo(ids, 0);
        Map<Long, Long> allocated = allocatedBytes(extended, ids);

        List<ThreadInfo> live = Arrays.stream(infos).filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(ThreadInfo::getThreadName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        Table.Builder table = Table.builder("Live threads", "Id", "Name", "State",
                "Daemon", "Priority", "CPU time", "User time", "Allocated", "Blocked", "Waited", "Waiting on",
                "Owned by");
        for (ThreadInfo info : live) {
            long id = info.getThreadId();
            table.row(id,
                    info.getThreadName(),
                    info.getThreadState().toString(),
                    info.isDaemon(),
                    info.getPriority(),
                    cpu(threads, id, false),
                    cpu(threads, id, true),
                    allocated.containsKey(id) ? Value.bytesShort(allocated.get(id)) : Value.unsupported(),
                    info.getBlockedCount() + (info.getBlockedTime() >= 0 ? " / " + info.getBlockedTime() + "ms" : ""),
                    info.getWaitedCount() + (info.getWaitedTime() >= 0 ? " / " + info.getWaitedTime() + "ms" : ""),
                    info.getLockName() == null ? "-" : info.getLockName(),
                    info.getLockOwnerName() == null ? "-" : info.getLockOwnerName() + " (" + info.getLockOwnerId() + ")");
        }
        section.table(table);

        Map<Thread.State, Integer> byState = new HashMap<>();
        live.forEach(info -> byState.merge(info.getThreadState(), 1, Integer::sum));
        Table.Builder states = Table.builder("Threads by state", "State", "Count");
        Arrays.stream(Thread.State.values())
                .filter(byState::containsKey)
                .forEach(state -> states.row(state.toString(), byState.get(state)));
        section.table(states);
    }

    private void stacks(Section section, InspectionContext context, ThreadMXBean threads) {
        int depth = context.full() ? FULL_STACK_DEPTH : context.stackDepth();
        if (depth <= 0) {
            section.note("Stack traces were suppressed by --stack-depth 0.");
            return;
        }
        ThreadInfo[] dump;
        try {
            dump = threads.dumpAllThreads(true, true, depth);
        } catch (RuntimeException failure) {
            dump = threads.dumpAllThreads(false, false);
        }
        Section stacks = section.sub("Stack traces");
        stacks.description("Top " + (depth >= FULL_STACK_DEPTH ? "" : depth + " ") + "frames of every"
                + " platform thread, with the locks each one holds.");
        Arrays.stream(dump)
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(ThreadInfo::getThreadName, String.CASE_INSENSITIVE_ORDER))
                .forEach(info -> stacks.code(
                        "\"" + info.getThreadName() + "\" id=" + info.getThreadId() + " " + info.getThreadState(),
                        render(info, depth)));
    }

    /** Renders one thread the way a thread dump does, so the output is familiar to read. */
    private static String render(ThreadInfo info, int depth) {
        StringBuilder text = new StringBuilder();
        text.append("\"").append(info.getThreadName()).append("\"")
                .append(info.isDaemon() ? " daemon" : "")
                .append(" prio=").append(info.getPriority())
                .append(" tid=").append(info.getThreadId())
                .append(" ").append(info.getThreadState());
        if (info.getLockName() != null) {
            text.append(" on ").append(info.getLockName());
        }
        if (info.getLockOwnerName() != null) {
            text.append(" owned by \"").append(info.getLockOwnerName())
                    .append("\" (").append(info.getLockOwnerId()).append(")");
        }
        if (info.isSuspended()) {
            text.append(" (suspended)");
        }
        if (info.isInNative()) {
            text.append(" (in native)");
        }
        text.append('\n');

        StackTraceElement[] frames = info.getStackTrace();
        int shown = Math.min(frames.length, depth);
        MonitorInfo[] monitors = info.getLockedMonitors();
        for (int index = 0; index < shown; index++) {
            text.append("    at ").append(frames[index]).append('\n');
            for (MonitorInfo monitor : monitors) {
                if (monitor.getLockedStackDepth() == index) {
                    text.append("    - locked ").append(monitor).append('\n');
                }
            }
        }
        if (frames.length > shown) {
            text.append("    ... ").append(frames.length - shown).append(" more frame(s)\n");
        }
        LockInfo[] synchronizers = info.getLockedSynchronizers();
        if (synchronizers.length > 0) {
            text.append("    Locked ownable synchronizers:\n");
            for (LockInfo lock : synchronizers) {
                text.append("    - ").append(lock).append('\n');
            }
        }
        return text.toString();
    }

    private static Map<Long, Long> allocatedBytes(Optional<com.sun.management.ThreadMXBean> extended, long[] ids) {
        Map<Long, Long> allocated = new HashMap<>();
        extended.ifPresent(bean -> {
            try {
                if (!bean.isThreadAllocatedMemorySupported() || !bean.isThreadAllocatedMemoryEnabled()) {
                    return;
                }
                long[] bytes = bean.getThreadAllocatedBytes(ids);
                for (int index = 0; index < ids.length; index++) {
                    if (bytes[index] >= 0) {
                        allocated.put(ids[index], bytes[index]);
                    }
                }
            } catch (RuntimeException ignored) {
                // Allocation accounting is optional; the rest of the table is unaffected.
            }
        });
        return allocated;
    }

    private static Value cpu(ThreadMXBean threads, long id, boolean userTime) {
        if (!threads.isThreadCpuTimeSupported() || !threads.isThreadCpuTimeEnabled()) {
            return Value.unsupported();
        }
        try {
            long nanos = userTime ? threads.getThreadUserTime(id) : threads.getThreadCpuTime(id);
            return Value.nanos(nanos);
        } catch (RuntimeException ignored) {
            return Value.unsupported();
        }
    }

    private static long[] safely(java.util.function.Supplier<long[]> finder) {
        try {
            long[] found = finder.get();
            return found == null ? new long[0] : found;
        } catch (RuntimeException ignored) {
            return new long[0];
        }
    }

    private static int majorVersion(InspectionContext context) {
        try {
            String specification = context.property("java.specification.version", "0");
            return Integer.parseInt(specification.split("\\.")[0]);
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

}
