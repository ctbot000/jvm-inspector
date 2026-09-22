package io.github.ctbot000.jvminspector.inspect;

import io.github.ctbot000.jvminspector.util.Redactor;

/**
 * What to collect.
 *
 * @param detail        how much of each section to report
 * @param expensive     whether to run operations that pause the target, such as a class histogram
 * @param stackDepth    how many frames of each thread stack to keep
 * @param redactor      how aggressively to mask secrets and identity
 */
public record InspectionOptions(Detail detail, boolean expensive, int stackDepth, Redactor redactor) {

    public static InspectionOptions defaults() {
        return new InspectionOptions(Detail.STANDARD, false, 12, new Redactor(Redactor.Mode.SECRETS));
    }

    public boolean full() {
        return detail == Detail.FULL;
    }
}
