package io.github.ctbot000.jvminspector.target;

import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Another JVM on this machine, reached with the attach API.
 *
 * <p>Attaching starts the target's local management agent, which publishes a connector address on
 * a loopback-only endpoint; everything else then flows through ordinary JMX.
 */
public final class AttachTarget extends AbstractTarget {

    private static final String LOCAL_CONNECTOR_ADDRESS =
            "com.sun.management.jmxremote.localConnectorAddress";

    private final long pid;
    private final String displayName;
    private final VirtualMachine machine;
    private final JMXConnector connector;
    private final MBeanServerConnection connection;

    private AttachTarget(long pid, String displayName, VirtualMachine machine, JMXConnector connector,
                         MBeanServerConnection connection) {
        this.pid = pid;
        this.displayName = displayName;
        this.machine = machine;
        this.connector = connector;
        this.connection = connection;
    }

    public static AttachTarget attach(long pid) throws IOException {
        if (pid == ProcessHandle.current().pid()) {
            throw new IOException("cannot attach to the inspector's own JVM; run without --pid to inspect it");
        }
        VirtualMachine machine;
        try {
            machine = VirtualMachine.attach(Long.toString(pid));
        } catch (AttachNotSupportedException failure) {
            throw new IOException("cannot attach to pid " + pid + ": " + failure.getMessage(), failure);
        }
        try {
            String address = startManagementAgent(machine);
            JMXConnector connector = JMXConnectorFactory.connect(new JMXServiceURL(address));
            String name = describe(pid, machine);
            return new AttachTarget(pid, name, machine, connector, connector.getMBeanServerConnection());
        } catch (Exception failure) {
            detach(machine);
            if (failure instanceof IOException io) {
                throw io;
            }
            throw new IOException("attached to pid " + pid + " but could not start its management agent: "
                    + failure.getMessage(), failure);
        }
    }

    /** Loads this tool's own jar into the target as an agent, unlocking instrumentation detail. */
    public void loadAgent(Path agentJar, String options) throws IOException {
        try {
            machine.loadAgent(agentJar.toAbsolutePath().toString(), options);
        } catch (Exception failure) {
            throw new IOException("could not load the agent into pid " + pid + ": " + failure.getMessage(), failure);
        }
    }

    private static String startManagementAgent(VirtualMachine machine) throws Exception {
        Properties agentProperties = machine.getAgentProperties();
        String existing = agentProperties.getProperty(LOCAL_CONNECTOR_ADDRESS);
        if (existing != null) {
            return existing;
        }
        return machine.startLocalManagementAgent();
    }

    private static String describe(long pid, VirtualMachine machine) {
        String name = "";
        try {
            name = machine.getSystemProperties().getProperty("sun.java.command", "");
        } catch (Exception ignored) {
            // The display name is cosmetic; a target that will not answer still inspects fine.
        }
        String head = name.isBlank() ? "" : " - " + name.split("\\s+")[0];
        return "attached JVM (pid " + pid + head + ")";
    }

    /** The JVMs on this machine that this user may attach to. */
    public static List<VirtualMachineDescriptor> list() {
        try {
            return new ArrayList<>(VirtualMachine.list());
        } catch (Throwable failure) {
            return List.of();
        }
    }

    @Override
    public String description() {
        return displayName;
    }

    @Override
    public boolean inProcess() {
        return false;
    }

    @Override
    public MBeanServerConnection connection() {
        return connection;
    }

    @Override
    public void close() {
        try {
            connector.close();
        } catch (IOException ignored) {
            // Closing is best effort; the target keeps running either way.
        }
        detach(machine);
    }

    private static void detach(VirtualMachine machine) {
        try {
            machine.detach();
        } catch (IOException ignored) {
            // Same: nothing useful to do if the target has already gone away.
        }
    }
}
