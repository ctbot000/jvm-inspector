package io.github.ctbot000.jvminspector.inspect;

import io.github.ctbot000.jvminspector.model.Section;

/** One area of the JVM, collected into one section of the report. */
public interface Inspector {

    /** The stable identifier used by {@code --only} and {@code --skip}. */
    String id();

    /** The section heading. */
    String title();

    /** One line describing what the section covers, printed under the heading. */
    default String description() {
        return null;
    }

    /**
     * Whether this inspector needs to run inside the target. Sections that read the JDK API
     * directly - modules, security providers, network interfaces - cannot serve a remote target and
     * are reported as skipped instead.
     */
    default boolean requiresInProcess() {
        return false;
    }

    Section inspect(InspectionContext context) throws Exception;
}
