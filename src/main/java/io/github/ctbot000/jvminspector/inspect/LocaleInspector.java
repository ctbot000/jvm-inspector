package io.github.ctbot000.jvminspector.inspect;

import io.github.ctbot000.jvminspector.model.Section;
import io.github.ctbot000.jvminspector.model.Table;
import io.github.ctbot000.jvminspector.model.Value;
import io.github.ctbot000.jvminspector.util.Format;

import java.nio.charset.Charset;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Character encodings, locale and time zone: the settings that quietly change program output. */
public final class LocaleInspector implements Inspector {

    private static final List<String> ENCODING_PROPERTIES = List.of(
            "file.encoding", "native.encoding", "sun.jnu.encoding", "stdout.encoding", "stderr.encoding",
            "sun.stdout.encoding", "sun.stderr.encoding", "user.language", "user.country", "user.script",
            "user.variant", "user.timezone", "user.language.format", "user.country.format", "line.separator");

    @Override
    public String id() {
        return "locale";
    }

    @Override
    public String title() {
        return "Encoding, Locale and Time";
    }

    @Override
    public String description() {
        return "The default charset, locale and time zone, and the properties that set them.";
    }

    @Override
    public Section inspect(InspectionContext context) {
        Section section = new Section(title());

        Table.Builder table = Table.builder("Related system properties", "Property", "Value");
        for (String property : ENCODING_PROPERTIES) {
            String value = context.property(property);
            table.row(property, value == null ? "(not set)" : Format.escape(value));
        }
        section.table(table);

        if (!context.inProcess()) {
            section.note("The live charset, locale and time zone objects can only be read in process;"
                    + " the properties above are the target's own view.");
            return section;
        }

        Section charsets = section.sub("Character encoding");
        charsets.put("Default charset", Charset.defaultCharset().name());
        charsets.put("Default charset aliases", Value.list(Charset.defaultCharset().aliases().stream().sorted().toList()));
        charsets.put("Charsets available", Value.of(Charset.availableCharsets().size()));
        if (context.full()) {
            Table.Builder charsetTable = Table.builder("Available charsets", "Charset", "Can encode", "Aliases");
            Charset.availableCharsets().values().forEach(charset ->
                    charsetTable.row(charset.name(), charset.canEncode(),
                            String.join(", ", charset.aliases())));
            charsets.table(charsetTable);
        } else {
            charsets.note("Run with --detail full to list every available charset.");
        }

        Locale locale = Locale.getDefault();
        Section locales = section.sub("Locale");
        locales.put("Default locale", locale.toString() + " (" + locale.getDisplayName(Locale.ENGLISH) + ")");
        locales.put("Language tag", locale.toLanguageTag());
        locales.put("Language", locale.getLanguage() + " / " + locale.getDisplayLanguage(Locale.ENGLISH));
        locales.put("Country", locale.getCountry().isEmpty() ? "(none)"
                : locale.getCountry() + " / " + locale.getDisplayCountry(Locale.ENGLISH));
        locales.put("Script", locale.getScript().isEmpty() ? "(none)" : locale.getScript());
        locales.put("Variant", locale.getVariant().isEmpty() ? "(none)" : locale.getVariant());
        locales.put("Display category locale", Locale.getDefault(Locale.Category.DISPLAY).toString());
        locales.put("Format category locale", Locale.getDefault(Locale.Category.FORMAT).toString());
        locales.put("Locales available", Value.of(Locale.getAvailableLocales().length));
        try {
            Currency currency = Currency.getInstance(locale);
            locales.put("Currency", currency.getCurrencyCode() + " " + currency.getSymbol(locale)
                    + " (" + currency.getDisplayName(Locale.ENGLISH) + ")");
        } catch (Exception ignored) {
            locales.put("Currency", Value.absent("no currency for this locale"));
        }

        TimeZone zone = TimeZone.getDefault();
        ZoneId zoneId = ZoneId.systemDefault();
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        Section time = section.sub("Time");
        time.put("Time zone id", zone.getID());
        time.put("Time zone name", zone.getDisplayName(false, TimeZone.LONG, Locale.ENGLISH));
        time.put("Raw offset", Value.millis(zone.getRawOffset()));
        time.put("Uses daylight time", Value.of(zone.useDaylightTime()));
        time.put("In daylight time now", Value.of(zone.inDaylightTime(new java.util.Date())));
        time.put("DST saving", Value.millis(zone.getDSTSavings()));
        time.put("Current offset", now.getOffset().getId());
        time.put("Current local time", now.toString());
        time.put("Current UTC time", Instant.now().toString());
        time.put("Available zone ids", Value.of(ZoneId.getAvailableZoneIds().size()));
        time.put("System clock", java.time.Clock.systemDefaultZone().toString());
        return section;
    }
}
