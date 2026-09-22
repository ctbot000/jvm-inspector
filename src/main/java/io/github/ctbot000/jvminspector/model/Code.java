package io.github.ctbot000.jvminspector.model;

import java.util.Objects;

/** Verbatim text: a stack trace, or the raw output of a diagnostic command. */
public record Code(String title, String body) implements Element {

    public Code {
        Objects.requireNonNull(title, "title");
        body = body == null ? "" : body.stripTrailing();
    }
}
