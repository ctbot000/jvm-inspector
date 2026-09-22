package io.github.ctbot000.jvminspector.inspect;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VmFlagsInspectorTest {

    private static final String SAMPLE = """
            [Global flags]
                 bool UseCompressedOops                        = true                                   {lp64_product} {ergonomic}
                intx CICompilerCount                          := 4                                      {product} {ergonomic}
               size_t MaxHeapSize                              = 4294967296                             {product} {command line}
               ccstr  SharedArchiveFile                        =                                        {product} {default}
               double G1ConcMarkStepDurationMillis             = 10.000000                              {product} {default}
            this line is not a flag
            """;

    @Test
    void parsesNameValueTypeCategoryAndOrigin() {
        Map<String, VmFlagsInspector.Flag> flags = byName(VmFlagsInspector.parse(SAMPLE));
        assertEquals(5, flags.size(), "the prose line must not be read as a flag");

        VmFlagsInspector.Flag compressedOops = flags.get("UseCompressedOops");
        assertEquals("bool", compressedOops.type());
        assertEquals("true", compressedOops.value());
        assertEquals("lp64_product", compressedOops.category());
        assertEquals("ergonomic", compressedOops.origin());
        assertFalse(compressedOops.changed());
    }

    @Test
    void marksFlagsThatWereExplicitlySet() {
        Map<String, VmFlagsInspector.Flag> flags = byName(VmFlagsInspector.parse(SAMPLE));
        assertTrue(flags.get("CICompilerCount").changed(), ":= means the value was set");
        assertEquals("4", flags.get("CICompilerCount").value());
        assertEquals("command line", flags.get("MaxHeapSize").origin());
    }

    @Test
    void handlesEmptyStringFlagsAndDecimals() {
        Map<String, VmFlagsInspector.Flag> flags = byName(VmFlagsInspector.parse(SAMPLE));
        assertEquals("", flags.get("SharedArchiveFile").value());
        assertEquals("default", flags.get("SharedArchiveFile").origin());
        assertEquals("10.000000", flags.get("G1ConcMarkStepDurationMillis").value());
    }

    @Test
    void emptyOutputYieldsNoFlags() {
        assertEquals(List.of(), VmFlagsInspector.parse(""));
        assertEquals(List.of(), VmFlagsInspector.parse("nothing to see here"));
    }

    private static Map<String, VmFlagsInspector.Flag> byName(List<VmFlagsInspector.Flag> flags) {
        return flags.stream().collect(java.util.stream.Collectors.toMap(VmFlagsInspector.Flag::name,
                Function.identity()));
    }
}
