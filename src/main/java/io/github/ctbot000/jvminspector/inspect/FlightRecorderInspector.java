package io.github.ctbot000.jvminspector.inspect;

import io.github.ctbot000.jvminspector.model.Section;
import io.github.ctbot000.jvminspector.model.Table;
import io.github.ctbot000.jvminspector.model.Value;

import jdk.jfr.Configuration;
import jdk.jfr.EventType;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;

import java.util.Comparator;
import java.util.List;

/** Flight Recorder: whether it is available, what is recording, and what it can record. */
public final class FlightRecorderInspector implements Inspector {

    @Override
    public String id() {
        return "jfr";
    }

    @Override
    public String title() {
        return "Flight Recorder";
    }

    @Override
    public String description() {
        return "JFR availability, active recordings, bundled configurations and event types.";
    }

    @Override
    public Section inspect(InspectionContext context) {
        Section section = new Section(title());

        context.dcmd("JFR.check").ifPresent(output -> section.code("JFR.check", output));

        if (!context.inProcess()) {
            section.note("Recording and event detail is read through the JFR API, which needs to run inside"
                    + " the target. The JFR.check output above is what a remote target can report.");
            return section;
        }

        boolean available;
        try {
            available = FlightRecorder.isAvailable();
        } catch (Throwable failure) {
            section.warn("Flight Recorder is not present in this runtime: " + failure);
            return section;
        }
        section.put("Available", Value.of(available));
        if (!available) {
            section.note("This build of the JVM has no Flight Recorder.");
            return section;
        }

        configurations(section);

        if (!context.full()) {
            section.note("Run with --detail full to list active recordings and every event type."
                    + " Reading them initialises the Flight Recorder subsystem in the target.");
            return section;
        }

        try {
            FlightRecorder recorder = FlightRecorder.getFlightRecorder();
            recordings(section, recorder.getRecordings());
            eventTypes(section, recorder.getEventTypes());
        } catch (Throwable failure) {
            section.warn("The Flight Recorder could not be read: " + failure);
        }
        return section;
    }

    private static void configurations(Section section) {
        List<Configuration> configurations;
        try {
            configurations = Configuration.getConfigurations();
        } catch (Throwable failure) {
            section.note("Bundled configurations could not be read: " + failure);
            return;
        }
        Table.Builder table = Table.builder("Bundled configurations", "Name", "Label", "Provider",
                "Settings", "Description");
        configurations.forEach(configuration -> table.row(configuration.getName(), configuration.getLabel(),
                configuration.getProvider(), configuration.getSettings().size(),
                configuration.getDescription()));
        section.table(table);
    }

    private static void recordings(Section section, List<Recording> recordings) {
        Section active = section.sub("Recordings");
        active.put("Recording count", Value.of(recordings.size()));
        if (recordings.isEmpty()) {
            active.note("Nothing is recording.");
            return;
        }
        Table.Builder table = Table.builder("Recordings", "Id", "Name", "State", "Started", "Duration",
                "Max size", "Max age", "Size on disk", "To disk");
        recordings.forEach(recording -> table.row(recording.getId(), recording.getName(),
                recording.getState().toString(),
                recording.getStartTime() == null ? "-" : recording.getStartTime().toString(),
                recording.getDuration() == null ? "unbounded" : recording.getDuration().toString(),
                recording.getMaxSize() == 0 ? "unbounded" : Value.bytesShort(recording.getMaxSize()),
                recording.getMaxAge() == null ? "unbounded" : recording.getMaxAge().toString(),
                Value.bytesShort(recording.getSize()), recording.isToDisk()));
        active.table(table);
    }

    private static void eventTypes(Section section, List<EventType> types) {
        Section events = section.sub("Event types");
        events.put("Event type count", Value.of(types.size()));
        Table.Builder table = Table.builder("Event types", "Name", "Label", "Categories", "Enabled",
                "Fields", "Description");
        types.stream()
                .sorted(Comparator.comparing(EventType::getName))
                .forEach(type -> table.row(type.getName(), type.getLabel() == null ? "-" : type.getLabel(),
                        String.join(" / ", type.getCategoryNames()),
                        type.isEnabled(), type.getFields().size(),
                        type.getDescription() == null ? "-" : type.getDescription()));
        events.table(table);
    }
}
