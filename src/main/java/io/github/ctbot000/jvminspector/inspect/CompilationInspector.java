package io.github.ctbot000.jvminspector.inspect;

import io.github.ctbot000.jvminspector.model.Section;
import io.github.ctbot000.jvminspector.model.Table;
import io.github.ctbot000.jvminspector.model.Value;

import java.lang.management.CompilationMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.List;

/** The just-in-time compiler: which one is in use, what it has done, and where the code lives. */
public final class CompilationInspector implements Inspector {

    private static final List<String> COMPILER_FLAGS = List.of(
            "TieredCompilation", "TieredStopAtLevel", "CICompilerCount", "CICompilerCountPerCPU",
            "ReservedCodeCacheSize", "InitialCodeCacheSize", "SegmentedCodeCache", "UseJVMCICompiler",
            "EnableJVMCI", "Inline", "MaxInlineSize", "FreqInlineSize", "MaxInlineLevel",
            "CompileThreshold", "BackgroundCompilation", "UseCompiler", "PrintCompilation",
            "DoEscapeAnalysis", "EliminateAllocations", "UseOnStackReplacement", "UseLoopPredicate",
            "UseSuperWord", "UseAOT", "AOTMode");

    @Override
    public String id() {
        return "jit";
    }

    @Override
    public String title() {
        return "Just-In-Time Compilation";
    }

    @Override
    public String description() {
        return "The compiler in use, the time it has spent, the code cache, and the flags that tune it.";
    }

    @Override
    public Section inspect(InspectionContext context) {
        Section section = new Section(title());

        context.bean(CompilationMXBean.class).ifPresentOrElse(compilation -> {
            section.put("Compiler", compilation.getName());
            section.put("Compilation time monitoring",
                    Value.of(compilation.isCompilationTimeMonitoringSupported()));
            if (compilation.isCompilationTimeMonitoringSupported()) {
                section.put("Total time compiling", Value.millis(compilation.getTotalCompilationTime()));
            }
        }, () -> section.note("This VM runs interpreted only: it publishes no compilation bean."));

        codeCache(section, context);
        flags(section, context);

        Section commands = section.sub("Diagnostic command output");
        context.dcmd("Compiler.codecache").ifPresent(output -> commands.code("Compiler.codecache", output));
        context.dcmd("Compiler.directives_print")
                .ifPresent(output -> commands.code("Compiler.directives_print", output));
        if (context.expensive()) {
            context.dcmd("Compiler.CodeHeap_Analytics")
                    .ifPresent(output -> commands.code("Compiler.CodeHeap_Analytics", output));
            context.dcmd("Compiler.codelist").ifPresent(output -> commands.code("Compiler.codelist", output));
        } else {
            commands.note("Run with --expensive to add Compiler.CodeHeap_Analytics and Compiler.codelist.");
        }
        return section;
    }

    private void codeCache(Section section, InspectionContext context) {
        List<MemoryPoolMXBean> pools = context.beans(MemoryPoolMXBean.class).stream()
                .filter(pool -> pool.getName().toLowerCase(java.util.Locale.ROOT).contains("code"))
                .toList();
        if (pools.isEmpty()) {
            return;
        }
        Section cache = section.sub("Code cache");
        cache.description("Where compiled methods, stubs and adapters are kept.");
        Table.Builder table = Table.builder("Code heaps", "Heap", "Used", "Committed", "Max", "Peak used",
                "Used of max");
        long used = 0;
        for (MemoryPoolMXBean pool : pools) {
            MemoryUsage usage = pool.getUsage();
            MemoryUsage peak = pool.getPeakUsage();
            if (usage == null) {
                continue;
            }
            used += usage.getUsed();
            table.row(pool.getName(), Value.bytesShort(usage.getUsed()), Value.bytesShort(usage.getCommitted()),
                    usage.getMax() < 0 ? Value.absent("unbounded") : Value.bytesShort(usage.getMax()),
                    peak == null ? Value.unsupported() : Value.bytesShort(peak.getUsed()),
                    usage.getMax() > 0 ? Value.percent((double) usage.getUsed() / usage.getMax())
                            : Value.absent("unbounded"));
        }
        cache.table(table);
        cache.put("Total compiled code in memory", Value.bytes(used));
    }

    private void flags(Section section, InspectionContext context) {
        Table.Builder table = Table.builder("Compiler flags", "Flag", "Value", "Origin");
        for (String flag : COMPILER_FLAGS) {
            context.vmOption(flag).ifPresent(option ->
                    table.row(option.getName(), option.getValue(), option.getOrigin().toString()));
        }
        if (table.size() > 0) {
            section.sub("Flags").table(table);
        }
    }
}
