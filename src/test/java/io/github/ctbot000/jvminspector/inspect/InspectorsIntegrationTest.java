package io.github.ctbot000.jvminspector.inspect;

import io.github.ctbot000.jvminspector.model.Code;
import io.github.ctbot000.jvminspector.model.Element;
import io.github.ctbot000.jvminspector.model.Note;
import io.github.ctbot000.jvminspector.model.Property;
import io.github.ctbot000.jvminspector.model.Section;
import io.github.ctbot000.jvminspector.model.Table;
import io.github.ctbot000.jvminspector.target.LocalTarget;
import io.github.ctbot000.jvminspector.target.Target;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs every inspector against the test JVM itself, which is the only target always available. */
class InspectorsIntegrationTest {

    private static Target target;

    @BeforeAll
    static void openTarget() {
        target = new LocalTarget();
    }

    @AfterAll
    static void closeTarget() {
        target.close();
    }

    static List<Inspector> inspectors() {
        return Inspectors.all();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("inspectors")
    void eachInspectorProducesSomething(Inspector inspector) throws Exception {
        Section section = inspector.inspect(new InspectionContext(target, InspectionOptions.defaults()));
        assertNotNull(section, inspector.id() + " returned no section");
        assertFalse(section.title().isBlank());
        assertFalse(section.isEmpty(), inspector.id() + " produced an empty section");
        if ("instrumentation".equals(inspector.id()) && !target.instrumentation().isPresent()) {
            // Without an agent this section can only explain how to load one, which is a note.
            assertFalse(notes(section).isEmpty(), "the empty instrumentation section must say why");
            return;
        }
        assertTrue(countLeaves(section) > 0, inspector.id() + " produced no leaf content");
    }

    @Test
    void sectionIdsAreUniqueAndStable() {
        Set<String> ids = new HashSet<>();
        for (Inspector inspector : Inspectors.all()) {
            assertTrue(ids.add(inspector.id()), "duplicate section id " + inspector.id());
            assertTrue(inspector.id().matches("[a-z-]+"), "unexpected id shape " + inspector.id());
            assertNotNull(inspector.description(), inspector.id() + " has no description");
        }
        assertEquals(Inspectors.all().size(), Inspectors.byId().size());
        assertTrue(Inspectors.ids().contains("overview"));
    }

    @Test
    void theOverviewNamesThisVirtualMachine() throws Exception {
        Section section = new OverviewInspector()
                .inspect(new InspectionContext(target, InspectionOptions.defaults()));
        List<String> names = properties(section).stream().map(Property::name).toList();
        assertTrue(names.contains("Java version"));
        assertTrue(names.contains("Heap used"));
        assertTrue(names.contains("Live threads"));
    }

    @Test
    void fullDetailAddsRowsRatherThanFailing() throws Exception {
        InspectionOptions full = new InspectionOptions(Detail.FULL, false, 4,
                new io.github.ctbot000.jvminspector.util.Redactor(
                        io.github.ctbot000.jvminspector.util.Redactor.Mode.ALL));
        for (String id : List.of("flags", "modules", "locale", "security")) {
            Inspector inspector = Inspectors.byId().get(id);
            Section standard = inspector.inspect(new InspectionContext(target, InspectionOptions.defaults()));
            Section detailed = inspector.inspect(new InspectionContext(target, full));
            assertTrue(countLeaves(detailed) >= countLeaves(standard),
                    id + " lost content at full detail");
        }
    }

    @Test
    void noSectionReportsAnError() throws Exception {
        for (Inspector inspector : Inspectors.all()) {
            Section section = inspector.inspect(new InspectionContext(target, InspectionOptions.defaults()));
            notes(section).forEach(note -> assertFalse(note.level() == Note.Level.ERROR,
                    inspector.id() + " reported an error: " + note.text()));
        }
    }

    private static List<Property> properties(Section section) {
        List<Property> found = new ArrayList<>();
        walk(section, element -> {
            if (element instanceof Property property) {
                found.add(property);
            }
        });
        return found;
    }

    private static List<Note> notes(Section section) {
        List<Note> found = new ArrayList<>();
        walk(section, element -> {
            if (element instanceof Note note) {
                found.add(note);
            }
        });
        return found;
    }

    private static int countLeaves(Section section) {
        int[] count = {0};
        walk(section, element -> {
            if (element instanceof Property || element instanceof Table || element instanceof Code) {
                count[0]++;
            }
        });
        return count[0];
    }

    private static void walk(Section section, java.util.function.Consumer<Element> visitor) {
        for (Element child : section.children()) {
            visitor.accept(child);
            if (child instanceof Section nested) {
                walk(nested, visitor);
            }
        }
    }
}
