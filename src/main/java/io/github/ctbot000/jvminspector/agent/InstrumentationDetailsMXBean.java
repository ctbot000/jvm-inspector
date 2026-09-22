package io.github.ctbot000.jvminspector.agent;

/**
 * The extra facts that only a JVM agent can see, published as a management bean so they are
 * readable from another process once the agent has been loaded into the target.
 */
public interface InstrumentationDetailsMXBean {

    /** The canonical name this bean registers under. */
    String OBJECT_NAME = "io.github.ctbot000.jvminspector:type=Instrumentation";

    /** {@code premain} when started with -javaagent, {@code agentmain} when attached later. */
    String getAgentMode();

    /** When the agent was installed, in milliseconds since the epoch. */
    long getAgentLoadedAt();

    boolean isRedefineClassesSupported();

    boolean isRetransformClassesSupported();

    boolean isNativeMethodPrefixSupported();

    /** Every class the VM has loaded, including the ones no class loading bean will name. */
    int getLoadedClassCount();

    /** Distinct loaders that show up as the defining loader of a loaded class. */
    int getClassLoaderCount();

    /** The shallow size of a bare {@code java.lang.Object}, which reveals header and alignment. */
    long getObjectHeaderSize();

    /** The shallow size of a zero length {@code byte[]}, which reveals the array header. */
    long getArrayHeaderSize();

    /** Loaded class counts per defining loader, as {@code count<tab>loader} lines. */
    String[] getClassCountByLoader();

    /** Loaded class counts per package, most populated first, as {@code count<tab>package} lines. */
    String[] getClassCountByPackage();

    /** Recomputes the package histogram, keeping the {@code limit} most populated packages. */
    String[] topPackages(int limit);
}
