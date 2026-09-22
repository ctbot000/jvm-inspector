package io.github.ctbot000.jvminspector.render;

import io.github.ctbot000.jvminspector.model.Code;
import io.github.ctbot000.jvminspector.model.Element;
import io.github.ctbot000.jvminspector.model.Note;
import io.github.ctbot000.jvminspector.model.Property;
import io.github.ctbot000.jvminspector.model.Report;
import io.github.ctbot000.jvminspector.model.Section;
import io.github.ctbot000.jvminspector.model.Table;
import io.github.ctbot000.jvminspector.model.Value;
import io.github.ctbot000.jvminspector.util.Format;

import java.io.IOException;
import java.util.List;

/** Markdown, for pasting a report into an issue or a pull request. */
public final class MarkdownRenderer implements Renderer {

    private static final int MAX_HEADING_LEVEL = 6;

    @Override
    public void render(Report report, Appendable output) throws IOException {
        output.append("# ").append(report.tool()).append(' ').append(report.toolVersion()).append('\n');
        output.append("\n- **Target**: ").append(escape(report.target()));
        output.append("\n- **Generated**: ").append(Format.timestamp(report.generatedAt())).append("\n");

        output.append("\n## Contents\n\n");
        for (Section section : report.sections()) {
            output.append("- [").append(escape(section.title())).append("](#")
                    .append(anchor(section.title())).append(")\n");
        }
        for (Section section : report.sections()) {
            output.append('\n');
            renderElement(section, output, 2);
        }
    }

    private void renderElement(Element element, Appendable output, int level) throws IOException {
        if (element instanceof Section section) {
            output.append("#".repeat(Math.min(level, MAX_HEADING_LEVEL))).append(' ')
                    .append(escape(section.title())).append("\n\n");
            if (section.description() != null) {
                output.append('_').append(escape(section.description())).append("_\n\n");
            }
            for (Element child : section.children()) {
                renderElement(child, output, level + 1);
            }
        } else if (element instanceof Property property) {
            output.append("- **").append(escape(property.name())).append("**: ")
                    .append(escape(property.value().display())).append('\n');
        } else if (element instanceof Note note) {
            String prefix = switch (note.level()) {
                case INFO -> "> ";
                case WARN -> "> **Warning:** ";
                case ERROR -> "> **Error:** ";
            };
            output.append('\n').append(prefix).append(escape(note.text())).append("\n\n");
        } else if (element instanceof Code code) {
            output.append("\n**").append(escape(code.title())).append("**\n\n```text\n")
                    .append(code.body()).append("\n```\n\n");
        } else if (element instanceof Table table) {
            renderTable(table, output);
        }
    }

    private void renderTable(Table table, Appendable output) throws IOException {
        output.append("\n**").append(escape(table.title())).append("** (")
                .append(String.valueOf(table.rows().size())).append(" rows)\n\n");
        output.append("| ").append(String.join(" | ", table.headers().stream()
                .map(MarkdownRenderer::escape).toList())).append(" |\n");
        output.append("|").append(" --- |".repeat(table.columnCount())).append('\n');
        for (List<Value> row : table.rows()) {
            output.append("| ");
            for (int index = 0; index < row.size(); index++) {
                output.append(escape(row.get(index).display()));
                output.append(index < row.size() - 1 ? " | " : " |\n");
            }
        }
        output.append('\n');
    }

    private static String escape(String text) {
        return text.replace("|", "\\|").replace("\n", " ").replace("\r", "");
    }

    private static String anchor(String title) {
        return title.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9 -]", "").replace(' ', '-');
    }
}
