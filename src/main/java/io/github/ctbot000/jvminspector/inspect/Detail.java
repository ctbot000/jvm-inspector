package io.github.ctbot000.jvminspector.inspect;

import java.util.Locale;

/** How much of each section to report. */
public enum Detail {
    /** Everything that fits in a report a person will actually read. */
    STANDARD,
    /** Every row the JVM will hand over: all flags, all algorithms, all MBean attributes. */
    FULL;

    public static Detail parse(String text) {
        try {
            return valueOf(text.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("unknown detail level '" + text + "' (expected standard or full)");
        }
    }
}
