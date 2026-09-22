package io.github.ctbot000.jvminspector;

import io.github.ctbot000.jvminspector.inspect.InspectionOptions;
import io.github.ctbot000.jvminspector.inspect.Inspectors;
import io.github.ctbot000.jvminspector.model.Report;
import io.github.ctbot000.jvminspector.target.LocalTarget;
import io.github.ctbot000.jvminspector.target.Target;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JvmInspectorTest {

    @Test
    void afullReportCoversEverySectionPlusTheRunSummary() {
        try (Target target = new LocalTarget()) {
            Report report = JvmInspector.inspect(target, InspectionOptions.defaults(),
                    JvmInspector.Selection.all());
            assertEquals(Inspectors.all().size() + 1, report.sections().size());
            assertEquals("jvm-inspector", report.tool());
            assertTrue(report.target().contains("this JVM"));
            assertEquals("Inspection Run", report.sections().get(report.sections().size() - 1).title());
        }
    }

    @Test
    void onlyAndSkipNarrowTheReport() {
        try (Target target = new LocalTarget()) {
            Report report = JvmInspector.inspect(target, InspectionOptions.defaults(),
                    new JvmInspector.Selection(Set.of("overview", "memory"), Set.of("memory")));
            List<String> titles = report.sections().stream().map(section -> section.title()).toList();
            assertEquals(List.of("Overview", "Inspection Run"), titles);
        }
    }

    @Test
    void renderSelfProducesEachFormat() {
        assertTrue(JvmInspector.renderSelf("text", "standard", "secrets").contains("OVERVIEW"));
        assertTrue(JvmInspector.renderSelf("json", "standard", "none").startsWith("{"));
        assertTrue(JvmInspector.renderSelf("markdown", "standard", "all").startsWith("# jvm-inspector"));
    }

    @Test
    void theVersionIsNeverBlank() {
        assertFalse(JvmInspector.version().isBlank());
    }
}
