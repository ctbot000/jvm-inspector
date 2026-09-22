package io.github.ctbot000.jvminspector.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal JSON reader used only by the tests.
 *
 * <p>The tool ships with no dependencies, so proving that its JSON output is well formed needs a
 * parser of its own rather than a library.
 */
final class TestJson {

    private final String text;
    private int position;

    private TestJson(String text) {
        this.text = text;
    }

    /** Parses a document, throwing {@link IllegalArgumentException} on anything malformed. */
    static Object parse(String text) {
        TestJson parser = new TestJson(text);
        parser.skipWhitespace();
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (parser.position != text.length()) {
            throw new IllegalArgumentException("trailing content at offset " + parser.position);
        }
        return value;
    }

    private Object readValue() {
        skipWhitespace();
        char character = peek();
        return switch (character) {
            case '{' -> readObject();
            case '[' -> readArray();
            case '"' -> readString();
            case 't', 'f' -> readBoolean();
            case 'n' -> readNull();
            default -> readNumber();
        };
    }

    private Map<String, Object> readObject() {
        expect('{');
        Map<String, Object> members = new LinkedHashMap<>();
        skipWhitespace();
        if (peek() == '}') {
            position++;
            return members;
        }
        while (true) {
            skipWhitespace();
            String name = readString();
            skipWhitespace();
            expect(':');
            members.put(name, readValue());
            skipWhitespace();
            char next = next();
            if (next == '}') {
                return members;
            }
            if (next != ',') {
                throw new IllegalArgumentException("expected , or } at offset " + position);
            }
        }
    }

    private List<Object> readArray() {
        expect('[');
        List<Object> items = new ArrayList<>();
        skipWhitespace();
        if (peek() == ']') {
            position++;
            return items;
        }
        while (true) {
            items.add(readValue());
            skipWhitespace();
            char next = next();
            if (next == ']') {
                return items;
            }
            if (next != ',') {
                throw new IllegalArgumentException("expected , or ] at offset " + position);
            }
        }
    }

    private String readString() {
        expect('"');
        StringBuilder value = new StringBuilder();
        while (true) {
            char character = next();
            if (character == '"') {
                return value.toString();
            }
            if (character == '\\') {
                char escape = next();
                switch (escape) {
                    case '"', '\\', '/' -> value.append(escape);
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case 'b' -> value.append('\b');
                    case 'f' -> value.append('\f');
                    case 'u' -> {
                        value.append((char) Integer.parseInt(text.substring(position, position + 4), 16));
                        position += 4;
                    }
                    default -> throw new IllegalArgumentException("bad escape \\" + escape);
                }
            } else if (character < 0x20) {
                throw new IllegalArgumentException("unescaped control character at offset " + position);
            } else {
                value.append(character);
            }
        }
    }

    private Boolean readBoolean() {
        if (text.startsWith("true", position)) {
            position += 4;
            return Boolean.TRUE;
        }
        if (text.startsWith("false", position)) {
            position += 5;
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException("bad literal at offset " + position);
    }

    private Object readNull() {
        if (!text.startsWith("null", position)) {
            throw new IllegalArgumentException("bad literal at offset " + position);
        }
        position += 4;
        return null;
    }

    private Double readNumber() {
        int start = position;
        while (position < text.length() && "+-.eE0123456789".indexOf(text.charAt(position)) >= 0) {
            position++;
        }
        if (start == position) {
            throw new IllegalArgumentException("unexpected character '" + peek() + "' at offset " + position);
        }
        return Double.valueOf(text.substring(start, position));
    }

    private void skipWhitespace() {
        while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
            position++;
        }
    }

    private char peek() {
        if (position >= text.length()) {
            throw new IllegalArgumentException("unexpected end of input");
        }
        return text.charAt(position);
    }

    private char next() {
        char character = peek();
        position++;
        return character;
    }

    private void expect(char character) {
        if (next() != character) {
            throw new IllegalArgumentException("expected '" + character + "' at offset " + (position - 1));
        }
    }
}
