package io.github.ctbot000.jvminspector.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Rendering helpers. Every method is locale independent so two machines produce comparable text. */
public final class Format {

    private static final String[] BINARY_UNITS = {"KiB", "MiB", "GiB", "TiB", "PiB", "EiB"};
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS XXX", Locale.ROOT);

    private Format() {
    }

    /** {@code 1610612736} becomes {@code 1.50 GiB (1,610,612,736 bytes)}. */
    public static String bytes(long bytes) {
        if (bytes < 1024) {
            return bytes + (Math.abs(bytes) == 1 ? " byte" : " bytes");
        }
        double value = bytes;
        int unit = -1;
        while (value >= 1024 && unit < BINARY_UNITS.length - 1) {
            value /= 1024;
            unit++;
        }
        return String.format(Locale.ROOT, "%.2f %s (%s bytes)", value, BINARY_UNITS[unit], count(bytes));
    }

    /** A plain byte figure with no parenthesised exact value, for dense table cells. */
    public static String bytesShort(long bytes) {
        if (bytes < 0) {
            return "n/a";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        double value = bytes;
        int unit = -1;
        while (value >= 1024 && unit < BINARY_UNITS.length - 1) {
            value /= 1024;
            unit++;
        }
        return String.format(Locale.ROOT, "%.1f %s", value, BINARY_UNITS[unit]);
    }

    public static String count(long number) {
        return String.format(Locale.ROOT, "%,d", number);
    }

    public static String decimal(double number) {
        if (Double.isNaN(number) || Double.isInfinite(number)) {
            return String.valueOf(number);
        }
        if (number == Math.rint(number) && Math.abs(number) < 1e15) {
            return count((long) number);
        }
        return String.format(Locale.ROOT, "%.4f", number);
    }

    public static String percent(double fraction) {
        return String.format(Locale.ROOT, "%.1f%%", fraction * 100);
    }

    /** Formats a nanosecond duration at a precision a reader can actually use. */
    public static String duration(long nanos) {
        if (nanos < 0) {
            return "n/a";
        }
        if (nanos < 1_000L) {
            return nanos + " ns";
        }
        if (nanos < 1_000_000L) {
            return String.format(Locale.ROOT, "%.1f us", nanos / 1_000d);
        }
        if (nanos < 1_000_000_000L) {
            return String.format(Locale.ROOT, "%.1f ms", nanos / 1_000_000d);
        }
        long totalSeconds = nanos / 1_000_000_000L;
        if (totalSeconds < 60) {
            return String.format(Locale.ROOT, "%.3f s", nanos / 1_000_000_000d);
        }
        long days = totalSeconds / 86_400;
        long hours = (totalSeconds % 86_400) / 3_600;
        long minutes = (totalSeconds % 3_600) / 60;
        long seconds = totalSeconds % 60;
        StringBuilder text = new StringBuilder();
        if (days > 0) {
            text.append(days).append("d ");
        }
        if (days > 0 || hours > 0) {
            text.append(hours).append("h ");
        }
        text.append(minutes).append("m ").append(seconds).append('s');
        return text.toString();
    }

    public static String timestamp(long epochMillis) {
        return TIMESTAMP.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()));
    }

    public static String timestamp(Instant instant) {
        return TIMESTAMP.format(instant.atZone(ZoneId.systemDefault()));
    }

    /** Makes control characters visible, so a value such as {@code line.separator} can be read. */
    public static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /** Shortens text for a table cell, keeping the head and marking the cut. */
    public static String ellipsis(String text, int max) {
        if (text == null) {
            return "";
        }
        String flattened = text.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
        return flattened.length() <= max ? flattened : flattened.substring(0, Math.max(1, max - 1)) + "…";
    }

    /** Turns {@code a:b:c} into its entries, which is how every path-shaped property is best read. */
    public static java.util.List<String> splitPath(String path) {
        if (path == null || path.isBlank()) {
            return java.util.List.of();
        }
        return java.util.Arrays.stream(path.split(java.io.File.pathSeparator))
                .filter(entry -> !entry.isBlank())
                .toList();
    }
}
