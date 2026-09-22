package io.github.ctbot000.jvminspector.model;

import java.util.Objects;

/** A line of prose: an explanation, or a warning about something the JVM reported. */
public record Note(Level level, String text) implements Element {

    public enum Level {
        INFO, WARN, ERROR
    }

    public Note {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(text, "text");
    }

    public static Note info(String text) {
        return new Note(Level.INFO, text);
    }

    public static Note warn(String text) {
        return new Note(Level.WARN, text);
    }

    public static Note error(String text) {
        return new Note(Level.ERROR, text);
    }
}
