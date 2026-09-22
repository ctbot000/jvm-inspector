package io.github.ctbot000.jvminspector.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** A grid of values with a header row: the readable shape for "one row per thread/pool/flag". */
public record Table(String title, List<String> headers, List<List<Value>> rows) implements Element {

    public Table {
        Objects.requireNonNull(title, "title");
        headers = List.copyOf(headers);
        rows = List.copyOf(rows);
    }

    public static Builder builder(String title, String... headers) {
        return new Builder(title, List.of(headers));
    }

    public int columnCount() {
        return headers.size();
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    public static final class Builder {

        private final String title;
        private final List<String> headers;
        private final List<List<Value>> rows = new ArrayList<>();

        private Builder(String title, List<String> headers) {
            this.title = title;
            this.headers = headers;
        }

        /** Adds a row; cells may be {@link Value}s or anything {@link Value#ofObject} understands. */
        public Builder row(Object... cells) {
            List<Value> values = Arrays.stream(cells).map(Value::ofObject).toList();
            if (values.size() != headers.size()) {
                throw new IllegalArgumentException(
                        "row has " + values.size() + " cells but table '" + title + "' has " + headers.size()
                                + " columns");
            }
            rows.add(values);
            return this;
        }

        public int size() {
            return rows.size();
        }

        public Table build() {
            return new Table(title, headers, rows);
        }
    }
}
