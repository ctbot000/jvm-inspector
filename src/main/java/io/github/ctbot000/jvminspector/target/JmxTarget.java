package io.github.ctbot000.jvminspector.target;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** A JVM reached over a JMX service URL, which may be on another machine. */
public final class JmxTarget extends AbstractTarget {

    private final String url;
    private final JMXConnector connector;
    private final MBeanServerConnection connection;

    private JmxTarget(String url, JMXConnector connector, MBeanServerConnection connection) {
        this.url = url;
        this.connector = connector;
        this.connection = connection;
    }

    /**
     * Connects to a JMX endpoint. A bare {@code host:port} is expanded to the usual RMI URL.
     * Credentials are optional; pass {@code null} for an unauthenticated endpoint.
     */
    public static JmxTarget connect(String url, String user, char[] password) throws IOException {
        String serviceUrl = normalise(url);
        Map<String, Object> environment = new HashMap<>();
        if (user != null && !user.isBlank()) {
            environment.put(JMXConnector.CREDENTIALS,
                    new String[]{user, password == null ? "" : new String(password)});
        }
        JMXConnector connector = JMXConnectorFactory.connect(new JMXServiceURL(serviceUrl), environment);
        return new JmxTarget(serviceUrl, connector, connector.getMBeanServerConnection());
    }

    static String normalise(String url) {
        if (url.startsWith("service:jmx:")) {
            return url;
        }
        return "service:jmx:rmi:///jndi/rmi://" + url + "/jmxrmi";
    }

    @Override
    public String description() {
        return "remote JVM (" + url + ")";
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
            // Best effort.
        }
    }
}
