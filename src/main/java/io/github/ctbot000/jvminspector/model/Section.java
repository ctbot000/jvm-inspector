package io.github.ctbot000.jvminspector.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A titled group of elements, possibly containing further sections. */
public final class Section implements Element {

    private final String title;
    private final List<Element> children = new ArrayList<>();
    private String description;

    public Section(String title) {
        this.title = Objects.requireNonNull(title, "title");
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public List<Element> children() {
        return List.copyOf(children);
    }

    public Section description(String text) {
        this.description = text;
        return this;
    }

    public Section add(Element element) {
        children.add(Objects.requireNonNull(element, "element"));
        return this;
    }

    public Section put(String name, Value value) {
        return add(new Property(name, value));
    }

    public Section put(String name, String value) {
        return put(name, Value.of(value));
    }

    public Section put(String name, long value) {
        return put(name, Value.of(value));
    }

    public Section put(String name, boolean value) {
        return put(name, Value.of(value));
    }

    public Section note(String text) {
        return add(Note.info(text));
    }

    public Section warn(String text) {
        return add(Note.warn(text));
    }

    public Section code(String title, String body) {
        return add(new Code(title, body));
    }

    /** Adds a table unless it has no rows, in which case the section is left untouched. */
    public Section table(Table table) {
        return table.isEmpty() ? this : add(table);
    }

    public Section table(Table.Builder builder) {
        return table(builder.build());
    }

    /** Creates a child section, adds it, and returns the child for further filling. */
    public Section sub(String childTitle) {
        Section child = new Section(childTitle);
        add(child);
        return child;
    }

    public boolean isEmpty() {
        return children.isEmpty();
    }

    @Override
    public String toString() {
        return "Section[" + title + ", " + children.size() + " children]";
    }
}
