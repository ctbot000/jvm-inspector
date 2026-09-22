package io.github.ctbot000.jvminspector.agent;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InspectorAgentTest {

    @Test
    void agentOptionsAreKeyValuePairs() {
        Map<String, String> options = InspectorAgent.parse("dump=exit,output=/tmp/x.json,format=json");
        assertEquals("exit", options.get("dump"));
        assertEquals("/tmp/x.json", options.get("output"));
        assertEquals("json", options.get("format"));
    }

    @Test
    void aBareFlagBecomesTrueAndBlankInputIsEmpty() {
        assertEquals("true", InspectorAgent.parse("verbose").get("verbose"));
        assertTrue(InspectorAgent.parse("").isEmpty());
        assertTrue(InspectorAgent.parse(null).isEmpty());
        assertTrue(InspectorAgent.parse(",,").isEmpty());
    }

    @Test
    void theHolderReportsWhetherAnAgentIsLoaded() {
        assertEquals(AgentSupport.isLoaded(), AgentSupport.instrumentation().isPresent());
        if (!AgentSupport.isLoaded()) {
            assertEquals("not loaded", AgentSupport.mode());
        }
    }

    @Test
    void theBeanNameIsStable() {
        assertEquals("io.github.ctbot000.jvminspector:type=Instrumentation",
                InstrumentationDetailsMXBean.OBJECT_NAME);
    }
}
