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
import java.util.ArrayList;
import java.util.List;

/** Plain text, laid out for reading in a terminal or pasting into a ticket. */
public final class TextRenderer implements Renderer {

    private static final int NAME_WIDTH = 38;
    private static final int MAX_CELL_WIDTH = 72;
    private static final String INDENT = "  ";

    @Override
    public void render(Report report, Appendable output) throws IOException {
        String title = report.tool() + " " + report.toolVersion();
        output.append(rule('=', 100)).append('\n');
        output.append(title).append('\n');
        output.append("Target:    ").append(report.target()).append('\n');
        output.append("Generated: ").append(Format.timestamp(report.generatedAt())).append('\n');
        output.append(rule('=', 100)).append('\n');

        output.append("\nContents\n");
        List<Section> sections = report.sections();
        for (int index = 0; index < sections.size(); index++) {
            output.append(String.format("  %2d. %s%n", index + 1, sections.get(index).title()));
        }

        for (int index = 0; index < sections.size(); index++) {
            Section section = sections.get(index);
            output.append('\n').append(rule('=', 100)).append('\n');
            output.append(String.format("%d. %s%n", index + 1, section.title().toUpperCase(java.util.Locale.ROOT)));
            if (section.description() != null) {
                output.append(section.description()).append('\n');
            }
            output.append(rule('=', 100)).append('\n');
            renderChildren(section, output, 0);
        }
    }

    private void renderChildren(Section section, Appendable output, int depth) throws IOException {
        for (Element child : section.children()) {
            render(child, output, depth);
        }
    }

    private void render(Element element, Appendable output, int depth) throws IOException {
        String indent = INDENT.repeat(depth);
        if (element instanceof Section section) {
            output.append('\n').append(indent).append("-- ").append(section.title()).append(' ')
                    .append(rule('-', Math.max(4, 70 - indent.length() - section.title().length())))
                    .append('\n');
            if (section.description() != null) {
                output.append(indent).append(INDENT).append(section.description()).append('\n');
            }
            renderChildren(section, output, depth + 1);
        } else if (element instanceof Property property) {
            output.append(indent).append(pad(property.name(), NAME_WIDTH)).append(' ')
                    .append(property.value().display()).append('\n');
        } else if (element instanceof Note note) {
            String prefix = switch (note.level()) {
                case INFO -> "note:  ";
                case WARN -> "WARN:  ";
                case ERROR -> "ERROR: ";
            };
            output.append(indent).append(prefix).append(note.text()).append('\n');
        } else if (element instanceof Code code) {
            output.append('\n').append(indent).append("[").append(code.title()).append("]\n");
            for (String line : code.body().split("\\R", -1)) {
                output.append(indent).append(INDENT).append(line).append('\n');
            }
        } else if (element instanceof Table table) {
            renderTable(table, output, indent);
        }
    }

    private void renderTable(Table table, Appendable output, String indent) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        rows.add(new ArrayList<>(table.headers()));
        for (List<Value> row : table.rows()) {
            List<String> cells = new ArrayList<>(row.size());
            for (Value value : row) {
                cells.add(Format.ellipsis(value.display(), MAX_CELL_WIDTH));
            }
            rows.add(cells);
        }
        int[] widths = new int[table.columnCount()];
        for (List<String> row : rows) {
            for (int column = 0; column < widths.length && column < row.size(); column++) {
                widths[column] = Math.max(widths[column], row.get(column).length());
            }
        }

        output.append('\n').append(indent).append(table.title())
                .append(" (").append(String.valueOf(table.rows().size())).append(" rows)\n");
        appendRow(output, indent, rows.get(0), widths);
        StringBuilder separator = new StringBuilder();
        for (int column = 0; column < widths.length; column++) {
            separator.append(rule('-', widths[column]));
            if (column < widths.length - 1) {
                separator.append("  ");
            }
        }
        output.append(indent).append(separator).append('\n');
        for (int index = 1; index < rows.size(); index++) {
            appendRow(output, indent, rows.get(index), widths);
        }
    }

    private static void appendRow(Appendable output, String indent, List<String> cells, int[] widths)
            throws IOException {
        StringBuilder line = new StringBuilder();
        for (int column = 0; column < widths.length; column++) {
            String cell = column < cells.size() ? cells.get(column) : "";
            line.append(column == widths.length - 1 ? cell : pad(cell, widths[column]));
            if (column < widths.length - 1) {
                line.append("  ");
            }
        }
        output.append(indent).append(line.toString().stripTrailing()).append('\n');
    }

    private static String pad(String text, int width) {
        return text.length() >= width ? text : text + " ".repeat(width - text.length());
    }

    private static String rule(char character, int length) {
        return String.valueOf(character).repeat(Math.max(0, length));
    }
}
