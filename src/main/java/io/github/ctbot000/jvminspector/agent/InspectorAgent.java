package io.github.ctbot000.jvminspector.agent;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The agent entry points.
 *
 * <p>Loading the tool's jar as an agent does two things: it makes instrumentation-only facts - the
 * true loaded class count, the object header size, the class histogram by loader - available to an
 * inspection, and it publishes them as a management bean so another process can read them too.
 *
 * <p>Options are comma separated {@code key=value} pairs:
 * <ul>
 *   <li>{@code dump=start|exit} - write a report when the JVM starts or when it shuts down</li>
 *   <li>{@code output=<path>} - where to write it (default: standard error)</li>
 *   <li>{@code format=text|json|markdown} - how to write it (default: text)</li>
 *   <li>{@code detail=standard|full} and {@code redact=none|secrets|all} - as on the command line</li>
 * </ul>
 */
public final class InspectorAgent {

    private InspectorAgent() {
    }

    /** Entry point for {@code -javaagent:jvm-inspector.jar}. */
    public static void premain(String arguments, Instrumentation instrumentation) {
        install(arguments, instrumentation, "premain");
    }

    /** Entry point for an agent attached to a running JVM, and for {@code java -jar}. */
    public static void agentmain(String arguments, Instrumentation instrumentation) {
        install(arguments, instrumentation, "agentmain");
    }

    private static void install(String arguments, Instrumentation instrumentation, String mode) {
        AgentSupport.install(instrumentation, mode);
        registerMBean(instrumentation);
        Map<String, String> options = parse(arguments);
        String dump = options.get("dump");
        if ("start".equalsIgnoreCase(dump)) {
            writeReport(options);
        } else if ("exit".equalsIgnoreCase(dump)) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> writeReport(options), "jvm-inspector-dump"));
        }
    }

    private static void registerMBean(Instrumentation instrumentation) {
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            ObjectName name = ObjectName.getInstance(InstrumentationDetailsMXBean.OBJECT_NAME);
            if (!server.isRegistered(name)) {
                server.registerMBean(new InstrumentationDetails(instrumentation), name);
            }
        } catch (InstanceAlreadyExistsException ignored) {
            // A second agent load is harmless: the first registration still answers.
        } catch (Exception failure) {
            System.err.println("[jvm-inspector] could not publish the instrumentation bean: " + failure);
        }
    }

    private static void writeReport(Map<String, String> options) {
        try {
            String rendered = io.github.ctbot000.jvminspector.JvmInspector.renderSelf(
                    options.getOrDefault("format", "text"),
                    options.getOrDefault("detail", "standard"),
                    options.getOrDefault("redact", "secrets"));
            String output = options.get("output");
            if (output == null || output.isBlank()) {
                PrintStream err = System.err;
                err.println(rendered);
            } else {
                Files.writeString(Path.of(output), rendered, StandardCharsets.UTF_8);
            }
        } catch (IOException | RuntimeException failure) {
            System.err.println("[jvm-inspector] report failed: " + failure);
        }
    }

    static Map<String, String> parse(String arguments) {
        Map<String, String> options = new LinkedHashMap<>();
        if (arguments == null || arguments.isBlank()) {
            return options;
        }
        for (String pair : arguments.split(",")) {
            if (pair.isBlank()) {
                continue;
            }
            int equals = pair.indexOf('=');
            if (equals < 0) {
                options.put(pair.trim(), "true");
            } else {
                options.put(pair.substring(0, equals).trim(), pair.substring(equals + 1).trim());
            }
        }
        return options;
    }
}
