package io.github.ctbot000.jvminspector.cli;

import io.github.ctbot000.jvminspector.inspect.Detail;
import io.github.ctbot000.jvminspector.inspect.InspectionOptions;
import io.github.ctbot000.jvminspector.inspect.Inspectors;
import io.github.ctbot000.jvminspector.render.OutputFormat;
import io.github.ctbot000.jvminspector.util.Redactor;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** The parsed command line. */
public final class CommandLine {

    /** Which JVM to inspect. */
    public enum TargetKind {
        SELF, PID, JMX
    }

    private TargetKind targetKind = TargetKind.SELF;
    private long pid;
    private String jmxUrl;
    private String jmxUser;
    private Path jmxPasswordFile;
    private boolean loadAgent;

    private OutputFormat format = OutputFormat.TEXT;
    private Path output;
    private final Set<String> only = new LinkedHashSet<>();
    private final Set<String> skip = new LinkedHashSet<>();

    private Detail detail = Detail.STANDARD;
    private boolean expensive;
    private int stackDepth = 12;
    private Redactor.Mode redaction = Redactor.Mode.SECRETS;

    private int watchSeconds;
    private int samples;

    private boolean serve;
    private int port = io.github.ctbot000.jvminspector.web.WebServer.DEFAULT_PORT;
    private String host = io.github.ctbot000.jvminspector.web.WebServer.DEFAULT_HOST;
    private boolean openBrowser;

    private boolean help;
    private boolean version;
    private boolean listJvms;
    private boolean listSections;

    private CommandLine() {
    }

    public static CommandLine parse(String[] arguments) {
        CommandLine line = new CommandLine();
        for (int index = 0; index < arguments.length; index++) {
            String argument = arguments[index];
            switch (argument) {
                case "-h", "--help" -> line.help = true;
                case "-V", "--version" -> line.version = true;
                case "--list-jvms", "--list-vms" -> line.listJvms = true;
                case "--list-sections" -> line.listSections = true;
                case "--expensive" -> line.expensive = true;
                case "--open" -> line.openBrowser = true;
                case "--serve" -> {
                    line.serve = true;
                    // "--serve 8080" reads naturally, so a bare number after the flag is its port.
                    if (index + 1 < arguments.length && arguments[index + 1].matches("\\d{1,5}")) {
                        line.port = (int) parseLong(argument, arguments[++index]);
                    }
                }
                case "--port" -> line.port = (int) parseLong(argument, next(arguments, ++index));
                case "--host" -> line.host = next(arguments, ++index);
                case "--load-agent" -> line.loadAgent = true;
                case "--pid", "-p" -> {
                    line.targetKind = TargetKind.PID;
                    line.pid = parseLong(argument, next(arguments, ++index));
                }
                case "--jmx" -> {
                    line.targetKind = TargetKind.JMX;
                    line.jmxUrl = next(arguments, ++index);
                }
                case "--jmx-user" -> line.jmxUser = next(arguments, ++index);
                case "--jmx-password-file" -> line.jmxPasswordFile = Path.of(next(arguments, ++index));
                case "-f", "--format" -> line.format = OutputFormat.parse(next(arguments, ++index));
                case "-o", "--output" -> line.output = Path.of(next(arguments, ++index));
                case "--only", "--sections" -> line.only.addAll(split(next(arguments, ++index)));
                case "--skip", "--exclude" -> line.skip.addAll(split(next(arguments, ++index)));
                case "-d", "--detail" -> line.detail = Detail.parse(next(arguments, ++index));
                case "--stack-depth" -> line.stackDepth = (int) parseLong(argument, next(arguments, ++index));
                case "--redact" -> line.redaction = Redactor.parseMode(next(arguments, ++index));
                case "--watch" -> line.watchSeconds = (int) parseLong(argument, next(arguments, ++index));
                case "--samples" -> line.samples = (int) parseLong(argument, next(arguments, ++index));
                case "--full" -> line.detail = Detail.FULL;
                default -> throw new CommandLineException("unknown option '" + argument + "'");
            }
        }
        line.validate();
        return line;
    }

    private void validate() {
        Set<String> known = Set.copyOf(Inspectors.ids());
        for (String id : only) {
            if (!known.contains(id)) {
                throw new CommandLineException("unknown section '" + id + "'; --list-sections shows them all");
            }
        }
        for (String id : skip) {
            if (!known.contains(id)) {
                throw new CommandLineException("unknown section '" + id + "'; --list-sections shows them all");
            }
        }
        if (stackDepth < 0) {
            throw new CommandLineException("--stack-depth cannot be negative");
        }
        if (watchSeconds < 0 || samples < 0) {
            throw new CommandLineException("--watch and --samples cannot be negative");
        }
        if (loadAgent && targetKind != TargetKind.PID) {
            throw new CommandLineException("--load-agent only applies to --pid; this JVM can load the agent"
                    + " with -javaagent at startup");
        }
        if (jmxUser != null && targetKind != TargetKind.JMX) {
            throw new CommandLineException("--jmx-user only applies to --jmx");
        }
        if (serve && watchSeconds > 0) {
            throw new CommandLineException("--serve and --watch are two different ways to watch one JVM;"
                    + " pick one");
        }
        if (port < 0 || port > 65535) {
            throw new CommandLineException("--port must be between 0 and 65535");
        }
        if (openBrowser && !serve) {
            throw new CommandLineException("--open only applies to --serve");
        }
    }

    private static String next(String[] arguments, int index) {
        if (index >= arguments.length) {
            throw new CommandLineException("option '" + arguments[index - 1] + "' needs a value");
        }
        return arguments[index];
    }

    private static long parseLong(String option, String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            throw new CommandLineException("option '" + option + "' needs a number, not '" + value + "'");
        }
    }

    private static List<String> split(String value) {
        return List.of(value.split("\\s*,\\s*"));
    }

    public InspectionOptions inspectionOptions() {
        return new InspectionOptions(detail, expensive, stackDepth, new Redactor(redaction));
    }

    public TargetKind targetKind() {
        return targetKind;
    }

    public long pid() {
        return pid;
    }

    public String jmxUrl() {
        return jmxUrl;
    }

    public String jmxUser() {
        return jmxUser;
    }

    public Path jmxPasswordFile() {
        return jmxPasswordFile;
    }

    public boolean loadAgent() {
        return loadAgent;
    }

    public OutputFormat format() {
        return format;
    }

    public Path output() {
        return output;
    }

    public Set<String> only() {
        return Set.copyOf(only);
    }

    public Set<String> skip() {
        return Set.copyOf(skip);
    }

    public boolean serving() {
        return serve;
    }

    public int port() {
        return port;
    }

    public String host() {
        return host;
    }

    public boolean openBrowser() {
        return openBrowser;
    }

    public boolean watching() {
        return watchSeconds > 0;
    }

    public int watchSeconds() {
        return watchSeconds;
    }

    public int samples() {
        return samples;
    }

    public boolean help() {
        return help;
    }

    public boolean version() {
        return version;
    }

    public boolean listJvms() {
        return listJvms;
    }

    public boolean listSections() {
        return listSections;
    }

    public static String usage() {
        return """
                jvm-inspector - report every detail a JVM will tell you about itself

                Usage: jvm-inspector [options]

                Target (the default is the JVM the tool itself runs in)
                  -p, --pid <pid>              attach to another JVM on this machine
                      --jmx <url|host:port>    connect to a JMX endpoint
                      --list-jvms              list the JVMs on this machine, then exit
                      --load-agent             with --pid, load this jar into the target as an agent
                                               first, which unlocks the instrumentation section

                Output
                  -f, --format <fmt>           text (default), json or markdown
                  -o, --output <file>          write to a file instead of standard output
                      --only <ids>             comma separated section ids to include
                      --skip <ids>             comma separated section ids to leave out
                      --list-sections          list the section ids, then exit

                Depth
                  -d, --detail <level>         standard (default) or full
                      --full                   the same as --detail full
                      --expensive              also run operations that pause the target,
                                               such as a class histogram
                      --stack-depth <n>        stack frames per thread (default 12, 0 for none)
                      --redact <mode>          none, secrets (default) or all; "all" also masks
                                               user name, host names, IP and hardware addresses

                JMX authentication
                      --jmx-user <user>        user name for an authenticated endpoint
                      --jmx-password-file <f>  file holding the password; the environment variable
                                               JVM_INSPECTOR_JMX_PASSWORD is read when it is absent

                Live view
                      --serve [port]           serve a browser interface (default port 7777) with a
                                               live dashboard, every section, and downloads
                      --port <n>               port for --serve, when not given after it
                      --host <address>         interface for --serve (default 127.0.0.1; any other
                                               value exposes the target's internals to the network)
                      --open                   open the interface in a browser once it is serving
                      --watch <seconds>        print a compact sample line on an interval instead
                                               of a full report
                      --samples <n>            stop after n samples (default: until interrupted)

                Other
                  -h, --help                   this text
                  -V, --version                the tool version

                As an agent
                  -javaagent:jvm-inspector.jar[=dump=start|exit,output=<file>,format=<fmt>]
                                               publishes the instrumentation bean, and optionally
                                               writes a report at startup or at shutdown
                """;
    }
}
