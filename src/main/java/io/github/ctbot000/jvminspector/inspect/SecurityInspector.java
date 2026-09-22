package io.github.ctbot000.jvminspector.inspect;

import io.github.ctbot000.jvminspector.model.Section;
import io.github.ctbot000.jvminspector.model.Table;
import io.github.ctbot000.jvminspector.model.Value;

import javax.crypto.Cipher;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.security.KeyStore;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Cryptographic providers, the TLS defaults, and the security properties that constrain them. */
public final class SecurityInspector implements Inspector {

    private static final List<String> SECURITY_PROPERTIES = List.of(
            "jdk.tls.disabledAlgorithms", "jdk.certpath.disabledAlgorithms", "jdk.jar.disabledAlgorithms",
            "jdk.tls.legacyAlgorithms", "jdk.security.legacyAlgorithms", "crypto.policy",
            "securerandom.source", "securerandom.strongAlgorithms", "keystore.type", "policy.provider",
            "networkaddress.cache.ttl", "networkaddress.cache.negative.ttl");

    @Override
    public String id() {
        return "security";
    }

    @Override
    public String title() {
        return "Security";
    }

    @Override
    public String description() {
        return "Installed providers and their algorithms, the TLS defaults, and the policy in force.";
    }

    @Override
    public boolean requiresInProcess() {
        return true;
    }

    @Override
    public Section inspect(InspectionContext context) {
        Section section = new Section(title());
        providers(section, context);
        defaults(section, context);
        tls(section, context);
        properties(section);
        return section;
    }

    private void providers(Section section, InspectionContext context) {
        Provider[] providers = Security.getProviders();
        Section installed = section.sub("Providers");
        installed.put("Provider count", Value.of(providers.length));

        Table.Builder table = Table.builder("Installed providers, in preference order", "#", "Name", "Version",
                "Services", "Description");
        for (int index = 0; index < providers.length; index++) {
            Provider provider = providers[index];
            table.row(index + 1, provider.getName(), provider.getVersionStr(),
                    provider.getServices().size(), provider.getInfo());
        }
        installed.table(table);

        Map<String, Integer> byType = new TreeMap<>();
        for (Provider provider : providers) {
            provider.getServices().forEach(service -> byType.merge(service.getType(), 1, Integer::sum));
        }
        Table.Builder types = Table.builder("Algorithms by service type", "Service type", "Implementations");
        byType.forEach(types::row);
        installed.table(types);

        if (context.full()) {
            Section algorithms = section.sub("Every algorithm, by provider");
            for (Provider provider : providers) {
                Table.Builder algorithmTable = Table.builder(provider.getName(), "Type", "Algorithm",
                        "Implementation");
                provider.getServices().stream()
                        .sorted(Comparator.comparing(Provider.Service::getType)
                                .thenComparing(Provider.Service::getAlgorithm))
                        .forEach(service -> algorithmTable.row(service.getType(), service.getAlgorithm(),
                                service.getClassName()));
                algorithms.table(algorithmTable);
            }
        } else {
            installed.note("Run with --detail full to list every algorithm each provider implements.");
        }
    }

    private void defaults(Section section, InspectionContext context) {
        Section defaults = section.sub("Defaults");
        defaults.put("Default key store type", KeyStore.getDefaultType());
        defaults.put("Security manager property", context.property("java.security.manager", "(not set)"));
        defaults.put("Policy file property", context.property("java.security.policy", "(not set)"));
        try {
            SecureRandom random = new SecureRandom();
            defaults.put("SecureRandom algorithm", random.getAlgorithm());
            defaults.put("SecureRandom provider", random.getProvider().getName());
        } catch (Exception failure) {
            defaults.note("A SecureRandom could not be created: " + failure.getMessage());
        }
        try {
            int maxKeyLength = Cipher.getMaxAllowedKeyLength("AES");
            defaults.put("Maximum AES key length",
                    maxKeyLength == Integer.MAX_VALUE ? Value.of("unlimited") : Value.of(maxKeyLength + " bits"));
        } catch (Exception failure) {
            defaults.put("Maximum AES key length", Value.absent("not readable: " + failure.getMessage()));
        }
    }

    private void tls(Section section, InspectionContext context) {
        Section tls = section.sub("TLS");
        try {
            SSLContext ssl = SSLContext.getDefault();
            tls.put("Default context protocol", ssl.getProtocol());
            tls.put("Provider", ssl.getProvider().getName());
            SSLParameters supported = ssl.getSupportedSSLParameters();
            SSLParameters enabled = ssl.getDefaultSSLParameters();
            tls.put("Supported protocols", Value.list(List.of(supported.getProtocols())));
            tls.put("Enabled protocols", Value.list(List.of(enabled.getProtocols())));
            tls.put("Supported cipher suites", Value.of(supported.getCipherSuites().length));
            tls.put("Enabled cipher suites", Value.of(enabled.getCipherSuites().length));
            if (context.full()) {
                Table.Builder table = Table.builder("Cipher suites", "Suite", "Enabled by default");
                List<String> enabledSuites = List.of(enabled.getCipherSuites());
                java.util.Arrays.stream(supported.getCipherSuites()).sorted()
                        .forEach(suite -> table.row(suite, enabledSuites.contains(suite)));
                tls.table(table);
            } else {
                tls.note("Run with --detail full to list every cipher suite.");
            }
        } catch (Exception failure) {
            tls.note("The default SSL context could not be created: " + failure);
        }
    }

    private void properties(Section section) {
        Section properties = section.sub("Security properties");
        properties.description("Values from java.security, which constrain what the providers above will do.");
        Table.Builder table = Table.builder("java.security", "Property", "Value");
        for (String property : SECURITY_PROPERTIES) {
            String value = Security.getProperty(property);
            table.row(property, value == null || value.isBlank() ? "(not set)" : value);
        }
        properties.table(table);
    }
}
