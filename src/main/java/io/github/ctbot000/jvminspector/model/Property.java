package io.github.ctbot000.jvminspector.model;

import java.util.Objects;

/** One named value, the workhorse of the report. */
public record Property(String name, Value value) implements Element {

    public Property {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
    }
}
