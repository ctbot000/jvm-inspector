package io.github.ctbot000.jvminspector.render;

import io.github.ctbot000.jvminspector.model.Code;
import io.github.ctbot000.jvminspector.model.Element;
import io.github.ctbot000.jvminspector.model.Note;
import io.github.ctbot000.jvminspector.model.Property;
import io.github.ctbot000.jvminspector.model.Report;
import io.github.ctbot000.jvminspector.model.Section;
import io.github.ctbot000.jvminspector.model.Table;
import io.github.ctbot000.jvminspector.model.Value;

import java.io.IOException;
import java.util.List;

/**
 * JSON, for feeding a report to something other than a person.
 *
 * <p>Values keep their raw type - a byte count stays a number - while the string a human would read
 * travels alongside it, so neither consumer has to reverse the other's formatting.
 */
public final class JsonRenderer implements Renderer {

    private static final String INDENT = "  ";

    @Override
    public void render(Report report, Appendable output) throws IOException {
        output.append("{\n");
        output.append(INDENT).append("\"tool\": ").append(Json.quote(report.tool())).append(",\n");
        output.append(INDENT).append("\"toolVersion\": ").append(Json.quote(report.toolVersion())).append(",\n");
        output.append(INDENT).append("\"target\": ").append(Json.quote(report.target())).append(",\n");
        output.append(INDENT).append("\"generatedAt\": ")
                .append(Json.quote(report.generatedAt().toString())).append(",\n");
        output.append(INDENT).append("\"sections\": ");
        renderList(report.sections(), output, 1);
        output.append("\n}\n");
    }

    private void renderList(List<? extends Element> elements, Appendable output, int depth) throws IOException {
        if (elements.isEmpty()) {
            output.append("[]");
            return;
        }
        String indent = INDENT.repeat(depth + 1);
        output.append("[\n");
        for (int index = 0; index < elements.size(); index++) {
            output.append(indent);
            renderElement(elements.get(index), output, depth + 1);
            output.append(index < elements.size() - 1 ? ",\n" : "\n");
        }
        output.append(INDENT.repeat(depth)).append(']');
    }

    private void renderElement(Element element, Appendable output, int depth) throws IOException {
        String indent = INDENT.repeat(depth + 1);
        if (element instanceof Section section) {
            output.append("{\n");
            output.append(indent).append("\"type\": \"section\",\n");
            output.append(indent).append("\"title\": ").append(Json.quote(section.title())).append(",\n");
            output.append(indent).append("\"description\": ")
                    .append(section.description() == null ? "null" : Json.quote(section.description()))
                    .append(",\n");
            output.append(indent).append("\"children\": ");
            renderList(section.children(), output, depth + 1);
            output.append('\n').append(INDENT.repeat(depth)).append('}');
        } else if (element instanceof Property property) {
            Value value = property.value();
            output.append("{\"type\": \"property\", \"name\": ").append(Json.quote(property.name()))
                    .append(", \"kind\": ").append(Json.quote(value.kind().name().toLowerCase(java.util.Locale.ROOT)))
                    .append(", \"value\": ").append(Json.scalar(value.raw()))
                    .append(", \"display\": ").append(Json.quote(value.display())).append('}');
        } else if (element instanceof Note note) {
            output.append("{\"type\": \"note\", \"level\": ")
                    .append(Json.quote(note.level().name().toLowerCase(java.util.Locale.ROOT)))
                    .append(", \"text\": ").append(Json.quote(note.text())).append('}');
        } else if (element instanceof Code code) {
            output.append("{\"type\": \"code\", \"title\": ").append(Json.quote(code.title()))
                    .append(", \"body\": ").append(Json.quote(code.body())).append('}');
        } else if (element instanceof Table table) {
            output.append("{\n");
            output.append(indent).append("\"type\": \"table\",\n");
            output.append(indent).append("\"title\": ").append(Json.quote(table.title())).append(",\n");
            output.append(indent).append("\"headers\": ").append(Json.scalar(table.headers())).append(",\n");
            output.append(indent).append("\"rows\": [");
            for (int index = 0; index < table.rows().size(); index++) {
                output.append(index == 0 ? "\n" : ",\n").append(indent).append(INDENT);
                renderRow(table.rows().get(index), output);
            }
            output.append(table.rows().isEmpty() ? "]\n" : "\n" + indent + "]\n");
            output.append(INDENT.repeat(depth)).append('}');
        }
    }

    private void renderRow(List<Value> row, Appendable output) throws IOException {
        output.append('[');
        for (int index = 0; index < row.size(); index++) {
            if (index > 0) {
                output.append(", ");
            }
            Value value = row.get(index);
            output.append(value.kind() == Value.Kind.BYTES || value.kind() == Value.Kind.DURATION
                    || value.kind() == Value.Kind.TIMESTAMP
                    ? "{\"value\": " + Json.scalar(value.raw()) + ", \"display\": "
                            + Json.quote(value.display()) + "}"
                    : Json.scalar(value.raw()));
        }
        output.append(']');
    }
}
