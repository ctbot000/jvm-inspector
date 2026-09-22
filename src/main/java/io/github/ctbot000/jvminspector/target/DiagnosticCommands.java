package io.github.ctbot000.jvminspector.target;

import javax.management.Descriptor;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Access to the HotSpot diagnostic commands - the same set {@code jcmd} exposes - through the
 * {@code com.sun.management:type=DiagnosticCommand} MBean.
 *
 * <p>Going through the MBean rather than through {@code jcmd} means the commands work identically
 * for this JVM, for an attached JVM and for one reached over JMX.
 */
public final class DiagnosticCommands {

    /** One command as the VM describes itself: {@code VM.flags}, its help text and its impact. */
    public record Command(String name, String operation, String description, String impact,
                          boolean takesArguments) implements Comparable<Command> {

        @Override
        public int compareTo(Command other) {
            return name.compareTo(other.name);
        }
    }

    private static final String OBJECT_NAME = "com.sun.management:type=DiagnosticCommand";
    private static final String STRING_ARRAY = String[].class.getName();

    private final MBeanServerConnection connection;
    private final ObjectName objectName;
    private final Map<String, Command> commands;

    private DiagnosticCommands(MBeanServerConnection connection, ObjectName objectName,
                               Map<String, Command> commands) {
        this.connection = connection;
        this.objectName = objectName;
        this.commands = commands;
    }

    /** Returns the commands the target supports, or an empty set if it exposes no diagnostic MBean. */
    public static DiagnosticCommands discover(MBeanServerConnection connection) {
        try {
            ObjectName name = ObjectName.getInstance(OBJECT_NAME);
            if (!connection.isRegistered(name)) {
                return empty(connection);
            }
            Map<String, Command> discovered = new TreeMap<>();
            for (MBeanOperationInfo operation : connection.getMBeanInfo(name).getOperations()) {
                Descriptor descriptor = operation.getDescriptor();
                Object commandName = descriptor.getFieldValue("dcmd.name");
                if (commandName == null) {
                    continue;
                }
                discovered.put(commandName.toString(), new Command(
                        commandName.toString(),
                        operation.getName(),
                        String.valueOf(descriptor.getFieldValue("dcmd.description")),
                        String.valueOf(descriptor.getFieldValue("dcmd.vmImpact")),
                        operation.getSignature().length > 0));
            }
            return new DiagnosticCommands(connection, name, discovered);
        } catch (Exception failure) {
            return empty(connection);
        }
    }

    private static DiagnosticCommands empty(MBeanServerConnection connection) {
        return new DiagnosticCommands(connection, null, Map.of());
    }

    public boolean isAvailable() {
        return objectName != null && !commands.isEmpty();
    }

    /** Every command the target offers, keyed and ordered by its {@code Category.name} form. */
    public Map<String, Command> commands() {
        return new LinkedHashMap<>(commands);
    }

    public boolean supports(String name) {
        return commands.containsKey(name);
    }

    /**
     * Runs a diagnostic command and returns its raw output, or an empty optional when the target
     * does not support the command or refuses to run it.
     */
    public Optional<String> execute(String name, String... arguments) {
        Command command = commands.get(name);
        if (command == null || objectName == null) {
            return Optional.empty();
        }
        try {
            Object result;
            if (command.takesArguments()) {
                result = connection.invoke(objectName, command.operation(),
                        new Object[]{arguments}, new String[]{STRING_ARRAY});
            } else {
                result = connection.invoke(objectName, command.operation(), new Object[0], new String[0]);
            }
            String output = result == null ? "" : result.toString();
            return output.isBlank() ? Optional.empty() : Optional.of(output.stripTrailing());
        } catch (Exception failure) {
            Throwable cause = failure.getCause() == null ? failure : failure.getCause();
            return Optional.of("<command failed: " + cause + ">");
        }
    }

    /** Runs the first of {@code names} the target supports. */
    public Optional<String> executeFirst(List<String> names, String... arguments) {
        for (String name : names) {
            if (supports(name)) {
                return execute(name, arguments);
            }
        }
        return Optional.empty();
    }
}
