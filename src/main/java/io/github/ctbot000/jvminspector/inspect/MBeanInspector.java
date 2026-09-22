package io.github.ctbot000.jvminspector.inspect;

import io.github.ctbot000.jvminspector.model.Section;
import io.github.ctbot000.jvminspector.model.Table;
import io.github.ctbot000.jvminspector.model.Value;
import io.github.ctbot000.jvminspector.util.Format;
import io.github.ctbot000.jvminspector.util.Mbeans;

import javax.management.MBeanInfo;
import javax.management.ObjectName;
import java.util.List;
import java.util.Map;

/**
 * The management bean registry itself.
 *
 * <p>The typed sections above cover the platform beans; this one is the catch-all that also reports
 * whatever the application, its libraries and its frameworks have registered.
 */
public final class MBeanInspector implements Inspector {

    @Override
    public String id() {
        return "mbeans";
    }

    @Override
    public String title() {
        return "Management Beans";
    }

    @Override
    public String description() {
        return "Every registered MBean, including the ones the application itself publishes.";
    }

    @Override
    public Section inspect(InspectionContext context) {
        Section section = new Section(title());
        Map<String, List<ObjectName>> byDomain = Mbeans.byDomain(context.connection());
        if (byDomain.isEmpty()) {
            return section.warn("The MBean registry could not be read.");
        }

        long total = byDomain.values().stream().mapToLong(List::size).sum();
        section.put("Registered beans", Value.of(total));
        section.put("Domains", Value.of(byDomain.size()));
        try {
            section.put("Default domain", context.connection().getDefaultDomain());
        } catch (Exception ignored) {
            // The default domain is cosmetic here.
        }

        Table.Builder domains = Table.builder("Beans per domain", "Domain", "Beans");
        byDomain.forEach((domain, names) -> domains.row(domain, names.size()));
        section.table(domains);

        Table.Builder inventory = Table.builder("Registered beans", "Object name", "Class", "Attributes",
                "Operations", "Notifications");
        for (List<ObjectName> names : byDomain.values()) {
            for (ObjectName name : names) {
                MBeanInfo info = infoOf(context, name);
                inventory.row(name.getCanonicalName(),
                        info == null ? "(unreadable)" : info.getClassName(),
                        info == null ? Value.unsupported() : Value.of(info.getAttributes().length),
                        info == null ? Value.unsupported() : Value.of(info.getOperations().length),
                        info == null ? Value.unsupported() : Value.of(info.getNotifications().length));
            }
        }
        section.table(inventory);

        if (context.full()) {
            values(section, context, byDomain);
        } else {
            section.note("Run with --detail full to dump every attribute value of every bean.");
        }
        return section;
    }

    private void values(Section section, InspectionContext context, Map<String, List<ObjectName>> byDomain) {
        Section values = section.sub("Attribute values");
        values.description("Every readable attribute of every bean, as the registry reports it.");
        for (Map.Entry<String, List<ObjectName>> domain : byDomain.entrySet()) {
            Section domainSection = values.sub(domain.getKey());
            for (ObjectName name : domain.getValue()) {
                Map<String, Object> attributes = Mbeans.readAll(context.connection(), name);
                if (attributes.isEmpty()) {
                    continue;
                }
                Table.Builder table = Table.builder(name.getCanonicalName(), "Attribute", "Value");
                attributes.forEach((attribute, value) -> table.row(attribute,
                        Format.ellipsis(context.redactor().host(String.valueOf(value)), 2000)));
                domainSection.table(table);
            }
            if (domainSection.isEmpty()) {
                domainSection.note("No readable attributes.");
            }
        }
    }

    private static MBeanInfo infoOf(InspectionContext context, ObjectName name) {
        try {
            return context.connection().getMBeanInfo(name);
        } catch (Exception ignored) {
            return null;
        }
    }
}
