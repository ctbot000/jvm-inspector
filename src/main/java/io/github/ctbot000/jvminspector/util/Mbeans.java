package io.github.ctbot000.jvminspector.util;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Generic MBean reads, for beans whose interface the tool does not want to depend on. */
public final class Mbeans {

    private Mbeans() {
    }

    public static Optional<ObjectName> name(String canonical) {
        try {
            return Optional.of(ObjectName.getInstance(canonical));
        } catch (Exception failure) {
            return Optional.empty();
        }
    }

    /**
     * Reads every readable attribute of a bean, sorted by name. Attributes that throw - and some
     * always do, such as a thread dump on a bean that has been unregistered - are reported with the
     * failure in place of a value rather than aborting the read.
     */
    public static Map<String, Object> readAll(MBeanServerConnection connection, ObjectName objectName) {
        Map<String, Object> values = new TreeMap<>();
        try {
            for (MBeanAttributeInfo attribute : connection.getMBeanInfo(objectName).getAttributes()) {
                if (!attribute.isReadable()) {
                    continue;
                }
                try {
                    values.put(attribute.getName(), connection.getAttribute(objectName, attribute.getName()));
                } catch (Exception failure) {
                    Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                    values.put(attribute.getName(), "<unreadable: " + cause.getClass().getSimpleName() + ">");
                }
            }
        } catch (Exception failure) {
            return Map.of();
        }
        return values;
    }

    /** Reads one attribute, absent when the bean or the attribute is missing. */
    public static Optional<Object> read(MBeanServerConnection connection, String objectName, String attribute) {
        try {
            return Optional.ofNullable(connection.getAttribute(ObjectName.getInstance(objectName), attribute));
        } catch (Exception failure) {
            return Optional.empty();
        }
    }

    /** Groups the registered beans by JMX domain. */
    public static Map<String, java.util.List<ObjectName>> byDomain(MBeanServerConnection connection) {
        Map<String, java.util.List<ObjectName>> domains = new LinkedHashMap<>();
        try {
            connection.queryNames(null, null).stream()
                    .sorted(java.util.Comparator.comparing(ObjectName::getCanonicalName))
                    .forEach(name -> domains.computeIfAbsent(name.getDomain(), key -> new java.util.ArrayList<>())
                            .add(name));
        } catch (Exception ignored) {
            return Map.of();
        }
        return new TreeMap<>(domains);
    }
}
