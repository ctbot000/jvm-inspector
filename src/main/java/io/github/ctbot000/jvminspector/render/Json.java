package io.github.ctbot000.jvminspector.render;

/** The small amount of JSON writing this tool needs, so that it can ship with no dependencies. */
final class Json {

    private Json() {
    }

    static String quote(String text) {
        StringBuilder json = new StringBuilder(text.length() + 2);
        json.append('"');
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                default -> {
                    if (character < 0x20 || character == 0x7f) {
                        json.append(String.format("\\u%04x", (int) character));
                    } else {
                        json.append(character);
                    }
                }
            }
        }
        return json.append('"').toString();
    }

    /** Renders a raw value as a JSON scalar, array, or quoted string. */
    static String scalar(Object raw) {
        if (raw == null) {
            return "null";
        }
        if (raw instanceof Boolean flag) {
            return flag.toString();
        }
        if (raw instanceof Double || raw instanceof Float) {
            double value = ((Number) raw).doubleValue();
            return Double.isFinite(value) ? Double.toString(value) : quote(String.valueOf(value));
        }
        if (raw instanceof Number number) {
            return number.toString();
        }
        if (raw instanceof Iterable<?> items) {
            StringBuilder array = new StringBuilder("[");
            boolean first = true;
            for (Object item : items) {
                if (!first) {
                    array.append(',');
                }
                array.append(scalar(item));
                first = false;
            }
            return array.append(']').toString();
        }
        return quote(raw.toString());
    }
}
