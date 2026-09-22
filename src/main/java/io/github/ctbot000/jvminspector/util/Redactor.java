package io.github.ctbot000.jvminspector.util;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Keeps a report safe to paste into an issue tracker.
 *
 * <p>A JVM will happily tell you its environment variables and its full command line, and those
 * routinely carry credentials. {@link Mode#SECRETS} (the default) masks anything whose name looks
 * like a secret plus a handful of well known token shapes; {@link Mode#ALL} additionally masks the
 * things that identify the machine and its user.
 */
public final class Redactor {

    public enum Mode {
        /** Report everything verbatim. */
        NONE,
        /** Mask secret-looking names and token shapes. The default. */
        SECRETS,
        /** Also mask user name, home directory, host names, IP and hardware addresses. */
        ALL
    }

    public static final String MASK = "<redacted>";

    private static final Pattern SECRET_NAME = Pattern.compile(
            "(?i).*(pass|pwd|secret|token|credential|api[-_.]?key|access[-_.]?key|private[-_.]?key"
                    + "|auth|session|cookie|signature|passphrase|salt|licen[cs]e).*");

    private static final List<Pattern> SECRET_VALUES = List.of(
            Pattern.compile("AKIA[0-9A-Z]{16}"),
            Pattern.compile("gh[pousr]_[A-Za-z0-9]{20,}"),
            Pattern.compile("xox[baprs]-[A-Za-z0-9-]{10,}"),
            Pattern.compile("sk-[A-Za-z0-9]{20,}"),
            Pattern.compile("eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}"),
            Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----"));

    private static final Pattern IPV4 = Pattern.compile(
            "\\b(?!127\\.0\\.0\\.1\\b)(?:\\d{1,3}\\.){3}\\d{1,3}\\b");
    private static final Pattern MAC = Pattern.compile("\\b(?:[0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}\\b");

    private final Mode mode;
    private final String userName;
    private final String homeDirectory;
    private final String hostName;

    public Redactor(Mode mode) {
        this.mode = mode;
        this.userName = System.getProperty("user.name", "");
        this.homeDirectory = System.getProperty("user.home", "");
        this.hostName = System.getenv().getOrDefault("HOSTNAME", "");
    }

    public Mode mode() {
        return mode;
    }

    /** Masks a value whose name suggests it holds a secret, and otherwise scrubs its contents. */
    public String named(String name, String value) {
        if (mode == Mode.NONE || value == null) {
            return value;
        }
        if (name != null && SECRET_NAME.matcher(name).matches() && !value.isBlank()) {
            return MASK;
        }
        return text(value);
    }

    /** Scrubs token shapes out of free text, plus identity when running in {@link Mode#ALL}. */
    public String text(String value) {
        if (mode == Mode.NONE || value == null || value.isEmpty()) {
            return value;
        }
        String result = value;
        for (Pattern pattern : SECRET_VALUES) {
            result = pattern.matcher(result).replaceAll(MASK);
        }
        if (mode == Mode.ALL) {
            result = maskIdentity(result);
        }
        return result;
    }

    /** Scrubs a host name, IP or hardware address. Only {@link Mode#ALL} touches these. */
    public String host(String value) {
        if (mode != Mode.ALL || value == null) {
            return value;
        }
        String result = MAC.matcher(value).replaceAll(MASK);
        result = IPV4.matcher(result).replaceAll(MASK);
        return maskIdentity(result);
    }

    private String maskIdentity(String value) {
        String result = value;
        if (!homeDirectory.isBlank()) {
            result = result.replace(homeDirectory, "~");
        }
        if (userName.length() > 2) {
            result = result.replace(userName, "<user>");
        }
        if (hostName.length() > 2) {
            result = result.replace(hostName, "<host>");
        }
        return result;
    }

    public static Mode parseMode(String text) {
        try {
            return Mode.valueOf(text.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException(
                    "unknown redaction mode '" + text + "' (expected none, secrets or all)");
        }
    }
}
