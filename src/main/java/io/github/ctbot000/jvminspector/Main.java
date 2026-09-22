package io.github.ctbot000.jvminspector;

import io.github.ctbot000.jvminspector.cli.CommandLine;
import io.github.ctbot000.jvminspector.cli.CommandLineException;
import io.github.ctbot000.jvminspector.cli.Watcher;
import io.github.ctbot000.jvminspector.inspect.Inspector;
import io.github.ctbot000.jvminspector.inspect.Inspectors;
import io.github.ctbot000.jvminspector.model.Report;
import io.github.ctbot000.jvminspector.target.AttachTarget;
import io.github.ctbot000.jvminspector.target.JmxTarget;
import io.github.ctbot000.jvminspector.target.LocalTarget;
import io.github.ctbot000.jvminspector.target.Target;
import io.github.ctbot000.jvminspector.web.WebServer;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

/** The command line entry point. */
public final class Main {

    /** Exit code for a target that could not be inspected. */
    static final int FAILURE = 1;
    /** Exit code for a command line the tool could not make sense of. */
    static final int USAGE = 2;

    private Main() {
    }

    public static void main(String[] arguments) {
        System.exit(run(arguments, System.out, System.err));
    }

    /** Runs one invocation and returns its exit code, so tests can drive the whole tool. */
    static int run(String[] arguments, PrintStream output, PrintStream errors) {
        CommandLine command;
        try {
            command = CommandLine.parse(arguments);
        } catch (CommandLineException | IllegalArgumentException failure) {
            errors.println("jvm-inspector: " + failure.getMessage());
            errors.println("Try 'jvm-inspector --help' for the full list of options.");
            return USAGE;
        }

        if (command.help()) {
            output.print(CommandLine.usage());
            return 0;
        }
        if (command.version()) {
            output.println(JvmInspector.NAME + " " + JvmInspector.version());
            return 0;
        }
        if (command.listSections()) {
            listSections(output);
            return 0;
        }
        if (command.listJvms()) {
            Watcher.listJvms(output);
            return 0;
        }

        try (Target target = open(command)) {
            if (command.loadAgent() && target instanceof AttachTarget attached) {
                loadAgent(attached, errors);
            }
            if (command.serving()) {
                serve(command, target, output, errors);
                return 0;
            }
            if (command.watching()) {
                new Watcher(target, output).watch(command.watchSeconds(), command.samples());
                return 0;
            }
            Report report = JvmInspector.inspect(target, command.inspectionOptions(),
                    new JvmInspector.Selection(command.only(), command.skip()));
            write(command, report, output);
            return 0;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return 0;
        } catch (IOException failure) {
            errors.println("jvm-inspector: " + failure.getMessage());
            return FAILURE;
        } catch (RuntimeException failure) {
            errors.println("jvm-inspector: " + failure);
            return FAILURE;
        }
    }

    /** Serves the browser interface until the process is interrupted. */
    private static void serve(CommandLine command, Target target, PrintStream output, PrintStream errors)
            throws IOException, InterruptedException {
        try (WebServer server = WebServer.start(target, command.inspectionOptions(), command.host(),
                command.port())) {
            output.println("jvm-inspector " + JvmInspector.version() + " is serving "
                    + target.description() + " at " + server.url());
            if (!InetAddress.getByName(command.host()).isLoopbackAddress()) {
                errors.println("warning: this interface is bound to " + command.host()
                        + ", so anything that can reach it can read the target's system properties,"
                        + " command line and stack traces. It has no authentication.");
            }
            output.println("Press Ctrl-C to stop.");
            if (command.openBrowser()) {
                openBrowser(server.url(), errors);
            }
            CountDownLatch stopped = new CountDownLatch(1);
            Runtime.getRuntime().addShutdownHook(new Thread(stopped::countDown, "jvm-inspector-stop"));
            stopped.await();
        }
    }

    private static void openBrowser(String url, PrintStream errors) {
        try {
            java.awt.Desktop desktop = java.awt.Desktop.isDesktopSupported() ? java.awt.Desktop.getDesktop() : null;
            if (desktop != null && desktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
                desktop.browse(java.net.URI.create(url));
                return;
            }
        } catch (Exception ignored) {
            // Fall through: printing the address is all a headless machine can offer.
        }
        errors.println("note: no browser could be opened here; visit " + url + " yourself.");
    }

    private static Target open(CommandLine command) throws IOException {
        return switch (command.targetKind()) {
            case SELF -> new LocalTarget();
            case PID -> AttachTarget.attach(command.pid());
            case JMX -> JmxTarget.connect(command.jmxUrl(), command.jmxUser(), password(command));
        };
    }

    /**
     * Reads the JMX password from a file or the environment, never from the command line, so it
     * does not end up in a shell history or a process listing.
     */
    private static char[] password(CommandLine command) throws IOException {
        if (command.jmxPasswordFile() != null) {
            return Files.readString(command.jmxPasswordFile(), StandardCharsets.UTF_8).strip().toCharArray();
        }
        String fromEnvironment = System.getenv("JVM_INSPECTOR_JMX_PASSWORD");
        if (fromEnvironment != null) {
            return fromEnvironment.toCharArray();
        }
        if (command.jmxUser() != null && System.console() != null) {
            char[] typed = System.console().readPassword("Password for %s: ", command.jmxUser());
            if (typed != null) {
                return typed;
            }
        }
        return null;
    }

    private static void loadAgent(AttachTarget target, PrintStream errors) throws IOException {
        Path jar = ownJar();
        if (jar == null) {
            errors.println("jvm-inspector: --load-agent needs the packaged jar; this build is running from"
                    + " a class directory, so the agent cannot be loaded into the target.");
            return;
        }
        target.loadAgent(jar, "");
    }

    /** The jar this class was loaded from, or null when running from a class directory. */
    static Path ownJar() {
        try {
            java.security.CodeSource source = Main.class.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) {
                return null;
            }
            Path path = Path.of(source.getLocation().toURI());
            return Files.isRegularFile(path) && path.toString().endsWith(".jar") ? path : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void write(CommandLine command, Report report, PrintStream output) throws IOException {
        String rendered = command.format().renderer().renderToString(report);
        if (command.output() == null) {
            output.print(rendered);
            return;
        }
        Files.writeString(command.output(), rendered, StandardCharsets.UTF_8);
        output.println("Wrote " + report.sections().size() + " sections to " + command.output()
                + " (" + rendered.length() + " characters, " + command.format().name().toLowerCase(Locale.ROOT)
                + ").");
    }

    private static void listSections(PrintStream output) {
        output.printf(Locale.ROOT, "%-20s %-32s %s%n", "ID", "TITLE", "DESCRIPTION");
        for (Inspector inspector : Inspectors.all()) {
            output.printf(Locale.ROOT, "%-20s %-32s %s%n", inspector.id(), inspector.title(),
                    inspector.description() == null ? "" : inspector.description());
        }
    }
}
