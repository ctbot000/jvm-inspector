package io.github.ctbot000.jvminspector.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** A complete inspection: what was inspected, when, and by which build of the tool. */
public record Report(String tool, String toolVersion, String target, Instant generatedAt, List<Section> sections) {

    public Report {
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(toolVersion, "toolVersion");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(generatedAt, "generatedAt");
        sections = List.copyOf(sections);
    }
}
