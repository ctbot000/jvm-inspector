package io.github.ctbot000.jvminspector.render;

import io.github.ctbot000.jvminspector.model.Report;

import java.io.IOException;

/** Turns a report into one of the supported output formats. */
public interface Renderer {

    void render(Report report, Appendable output) throws IOException;

    default String renderToString(Report report) {
        StringBuilder text = new StringBuilder();
        try {
            render(report, text);
        } catch (IOException impossible) {
            throw new IllegalStateException("a StringBuilder cannot fail to accept text", impossible);
        }
        return text.toString();
    }
}
