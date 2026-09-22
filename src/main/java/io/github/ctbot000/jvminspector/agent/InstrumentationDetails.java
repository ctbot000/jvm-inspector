package io.github.ctbot000.jvminspector.agent;

import java.lang.instrument.Instrumentation;
import java.util.HashMap;
import java.util.Map;

/** The {@link InstrumentationDetailsMXBean} implementation, backed by the live agent handle. */
final class InstrumentationDetails implements InstrumentationDetailsMXBean {

    private static final int DEFAULT_PACKAGE_LIMIT = 40;

    private final Instrumentation instrumentation;

    InstrumentationDetails(Instrumentation instrumentation) {
        this.instrumentation = instrumentation;
    }

    @Override
    public String getAgentMode() {
        return AgentSupport.mode();
    }

    @Override
    public long getAgentLoadedAt() {
        return AgentSupport.loadedAt();
    }

    @Override
    public boolean isRedefineClassesSupported() {
        return instrumentation.isRedefineClassesSupported();
    }

    @Override
    public boolean isRetransformClassesSupported() {
        return instrumentation.isRetransformClassesSupported();
    }

    @Override
    public boolean isNativeMethodPrefixSupported() {
        return instrumentation.isNativeMethodPrefixSupported();
    }

    @Override
    public int getLoadedClassCount() {
        return instrumentation.getAllLoadedClasses().length;
    }

    @Override
    public int getClassLoaderCount() {
        Map<String, Integer> byLoader = countBy(type -> loaderName(type.getClassLoader()));
        return byLoader.size();
    }

    @Override
    public long getObjectHeaderSize() {
        return instrumentation.getObjectSize(new Object());
    }

    @Override
    public long getArrayHeaderSize() {
        return instrumentation.getObjectSize(new byte[0]);
    }

    @Override
    public String[] getClassCountByLoader() {
        return render(countBy(type -> loaderName(type.getClassLoader())), Integer.MAX_VALUE);
    }

    @Override
    public String[] getClassCountByPackage() {
        return topPackages(DEFAULT_PACKAGE_LIMIT);
    }

    @Override
    public String[] topPackages(int limit) {
        return render(countBy(type -> {
            Package definition = type.getPackage();
            String name = definition == null ? "" : definition.getName();
            return name.isEmpty() ? "<default>" : name;
        }), Math.max(1, limit));
    }

    private Map<String, Integer> countBy(java.util.function.Function<Class<?>, String> key) {
        Map<String, Integer> counts = new HashMap<>();
        for (Class<?> type : instrumentation.getAllLoadedClasses()) {
            try {
                counts.merge(key.apply(type), 1, Integer::sum);
            } catch (Throwable ignored) {
                counts.merge("<unreadable>", 1, Integer::sum);
            }
        }
        return counts;
    }

    private static String loaderName(ClassLoader loader) {
        if (loader == null) {
            return "<bootstrap>";
        }
        String name = loader.getName();
        return loader.getClass().getName() + (name == null ? "" : " (" + name + ")");
    }

    private static String[] render(Map<String, Integer> counts, int limit) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .map(entry -> entry.getValue() + "\t" + entry.getKey())
                .toArray(String[]::new);
    }
}
