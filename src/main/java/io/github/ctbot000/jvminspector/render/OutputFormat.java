package io.github.ctbot000.jvminspector.render;

import java.util.Locale;

/** The output formats, named as the {@code --format} option spells them. */
public enum OutputFormat {

    TEXT, JSON, MARKDOWN;

    public static OutputFormat parse(String text) {
        String normalised = text.toUpperCase(Locale.ROOT);
        if ("MD".equals(normalised)) {
            return MARKDOWN;
        }
        try {
            return valueOf(normalised);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("unknown format '" + text + "' (expected text, json or markdown)");
        }
    }

    public Renderer renderer() {
        return switch (this) {
            case TEXT -> new TextRenderer();
            case JSON -> new JsonRenderer();
            case MARKDOWN -> new MarkdownRenderer();
        };
    }
}
