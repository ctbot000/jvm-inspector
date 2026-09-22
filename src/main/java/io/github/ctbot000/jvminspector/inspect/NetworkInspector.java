package io.github.ctbot000.jvminspector.inspect;

import io.github.ctbot000.jvminspector.model.Section;
import io.github.ctbot000.jvminspector.model.Table;
import io.github.ctbot000.jvminspector.model.Value;

import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** The network stack as the VM sees it: interfaces, addresses, and the proxy configuration. */
public final class NetworkInspector implements Inspector {

    private static final List<String> NETWORK_PROPERTIES = List.of(
            "java.net.preferIPv4Stack", "java.net.preferIPv6Addresses", "java.net.useSystemProxies",
            "http.proxyHost", "http.proxyPort", "https.proxyHost", "https.proxyPort",
            "ftp.proxyHost", "ftp.proxyPort", "socksProxyHost", "socksProxyPort", "socksProxyVersion",
            "http.nonProxyHosts", "http.agent", "http.keepAlive", "http.maxConnections",
            "networkaddress.cache.ttl", "sun.net.client.defaultConnectTimeout",
            "sun.net.client.defaultReadTimeout", "jdk.httpclient.keepalive.timeout");

    @Override
    public String id() {
        return "network";
    }

    @Override
    public String title() {
        return "Network";
    }

    @Override
    public String description() {
        return "Network interfaces and addresses, plus the proxy and protocol settings in force.";
    }

    @Override
    public boolean requiresInProcess() {
        return true;
    }

    @Override
    public Section inspect(InspectionContext context) {
        Section section = new Section(title());

        Section host = section.sub("Local host");
        try {
            InetAddress local = InetAddress.getLocalHost();
            host.put("Host name", context.redactor().host(local.getHostName()));
            host.put("Address", context.redactor().host(local.getHostAddress()));
        } catch (Exception failure) {
            host.note("The local host name could not be resolved: " + failure.getMessage());
        }
        try {
            host.put("Loopback address",
                    context.redactor().host(InetAddress.getLoopbackAddress().getHostAddress()));
        } catch (Exception ignored) {
            // A VM without a loopback address is not worth a warning here.
        }

        interfaces(section, context);

        Section settings = section.sub("Settings");
        Table.Builder table = Table.builder("Network system properties", "Property", "Value");
        for (String property : NETWORK_PROPERTIES) {
            String value = context.property(property);
            table.row(property, value == null ? "(not set)" : context.redactor().host(value));
        }
        settings.table(table);
        return section;
    }

    private void interfaces(Section section, InspectionContext context) {
        Section interfaces = section.sub("Interfaces");
        List<NetworkInterface> all;
        try {
            all = Collections.list(NetworkInterface.getNetworkInterfaces());
        } catch (Exception failure) {
            interfaces.note("Network interfaces could not be enumerated: " + failure.getMessage());
            return;
        }
        interfaces.put("Interface count", Value.of(all.size()));

        Table.Builder table = Table.builder("Network interfaces", "Name", "Display name", "Index", "MTU",
                "Up", "Loopback", "Point to point", "Multicast", "Virtual", "Hardware address", "Addresses");
        for (NetworkInterface candidate : all) {
            try {
                table.row(candidate.getName(),
                        context.redactor().host(candidate.getDisplayName()),
                        candidate.getIndex(),
                        candidate.getMTU(),
                        candidate.isUp(),
                        candidate.isLoopback(),
                        candidate.isPointToPoint(),
                        candidate.supportsMulticast(),
                        candidate.isVirtual(),
                        context.redactor().host(hardwareAddress(candidate)),
                        context.redactor().host(addresses(candidate)));
            } catch (Exception ignored) {
                // An interface can disappear between enumeration and interrogation.
            }
        }
        interfaces.table(table);
    }

    private static String hardwareAddress(NetworkInterface candidate) throws java.net.SocketException {
        byte[] address = candidate.getHardwareAddress();
        if (address == null || address.length == 0) {
            return "-";
        }
        StringBuilder text = new StringBuilder();
        for (byte octet : address) {
            if (text.length() > 0) {
                text.append(':');
            }
            text.append(String.format(java.util.Locale.ROOT, "%02x", octet));
        }
        return text.toString();
    }

    private static String addresses(NetworkInterface candidate) {
        String addresses = candidate.getInterfaceAddresses().stream()
                .map(NetworkInspector::describe)
                .collect(Collectors.joining(", "));
        return addresses.isEmpty() ? "-" : addresses;
    }

    private static String describe(InterfaceAddress address) {
        String text = address.getAddress().getHostAddress();
        if (address.getNetworkPrefixLength() > 0) {
            text += "/" + address.getNetworkPrefixLength();
        }
        return text;
    }
}
