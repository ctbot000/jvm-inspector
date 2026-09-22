package io.github.ctbot000.jvminspector.model;

import io.github.ctbot000.jvminspector.util.Format;

import java.util.List;
import java.util.Objects;

/**
 * A single reported value: the raw datum (so JSON stays machine readable) paired with the string a
 * human should see (so {@code 1610612736} can be shown as {@code 1.5 GiB}).
 */
public record Value(Kind kind, Object raw, String display) {

    public enum Kind {
        STRING, NUMBER, BOOLEAN, BYTES, DURATION, TIMESTAMP, LIST, ABSENT
    }

    public Value {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(display, "display");
    }

    public static Value of(String text) {
        return text == null ? absent("null") : new Value(Kind.STRING, text, text);
    }

    public static Value of(long number) {
        return new Value(Kind.NUMBER, number, Format.count(number));
    }

    public static Value of(int number) {
        return of((long) number);
    }

    public static Value of(double number) {
        return new Value(Kind.NUMBER, number, Format.decimal(number));
    }

    public static Value of(boolean flag) {
        return new Value(Kind.BOOLEAN, flag, Boolean.toString(flag));
    }

    /** A byte count, displayed with binary units. Negative means "not available". */
    public static Value bytes(long bytes) {
        return bytes < 0 ? absent("not available") : new Value(Kind.BYTES, bytes, Format.bytes(bytes));
    }

    /** A byte count with a compact display, for dense table cells. */
    public static Value bytesShort(long bytes) {
        return bytes < 0 ? absent("n/a") : new Value(Kind.BYTES, bytes, Format.bytesShort(bytes));
    }

    public static Value millis(long millis) {
        return millis < 0 ? absent("not available")
                : new Value(Kind.DURATION, millis, Format.duration(millis * 1_000_000L));
    }

    public static Value nanos(long nanos) {
        return nanos < 0 ? absent("not available") : new Value(Kind.DURATION, nanos, Format.duration(nanos));
    }

    /** An instant expressed as milliseconds since the epoch. */
    public static Value timestamp(long epochMillis) {
        return epochMillis <= 0 ? absent("not available")
                : new Value(Kind.TIMESTAMP, epochMillis, Format.timestamp(epochMillis));
    }

    public static Value percent(double fraction) {
        return fraction < 0 ? absent("not available")
                : new Value(Kind.NUMBER, fraction, Format.percent(fraction));
    }

    public static Value list(List<String> items) {
        List<String> copy = List.copyOf(items);
        return new Value(Kind.LIST, copy, copy.isEmpty() ? "(none)" : String.join(", ", copy));
    }

    /** A value the JVM declined to provide, carrying the reason instead. */
    public static Value absent(String reason) {
        return new Value(Kind.ABSENT, null, "(" + reason + ")");
    }

    public static Value unsupported() {
        return absent("not supported");
    }

    /** Best-effort conversion for values pulled out of a generic MBean attribute. */
    public static Value ofObject(Object object) {
        if (object == null) {
            return absent("null");
        }
        if (object instanceof Value value) {
            return value;
        }
        if (object instanceof Boolean flag) {
            return of(flag.booleanValue());
        }
        if (object instanceof Double || object instanceof Float) {
            return of(((Number) object).doubleValue());
        }
        if (object instanceof Number number) {
            return of(number.longValue());
        }
        if (object instanceof CharSequence text) {
            return of(text.toString());
        }
        if (object instanceof String[] array) {
            return list(List.of(array));
        }
        if (object instanceof Object[] array) {
            return list(java.util.Arrays.stream(array).map(String::valueOf).toList());
        }
        if (object instanceof java.util.Collection<?> collection) {
            return list(collection.stream().map(String::valueOf).toList());
        }
        return of(String.valueOf(object));
    }

    /** The same value with its rendered form replaced, used by the redactor. */
    public Value withDisplay(String newDisplay) {
        return new Value(kind, raw, newDisplay);
    }

    public boolean isAbsent() {
        return kind == Kind.ABSENT;
    }
}
