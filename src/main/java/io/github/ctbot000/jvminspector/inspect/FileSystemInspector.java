package io.github.ctbot000.jvminspector.inspect;

import io.github.ctbot000.jvminspector.model.Section;
import io.github.ctbot000.jvminspector.model.Table;
import io.github.ctbot000.jvminspector.model.Value;

import java.io.File;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeSet;

/** The file systems the VM can see, and the directories it was pointed at. */
public final class FileSystemInspector implements Inspector {

    @Override
    public String id() {
        return "filesystem";
    }

    @Override
    public String title() {
        return "File System";
    }

    @Override
    public String description() {
        return "The default file system, its stores and their free space, and the VM's working directories.";
    }

    @Override
    public boolean requiresInProcess() {
        return true;
    }

    @Override
    public Section inspect(InspectionContext context) {
        Section section = new Section(title());
        FileSystem fileSystem = FileSystems.getDefault();

        section.put("Provider", fileSystem.provider().getClass().getName());
        section.put("Scheme", fileSystem.provider().getScheme());
        section.put("Open", Value.of(fileSystem.isOpen()));
        section.put("Read only", Value.of(fileSystem.isReadOnly()));
        section.put("Name separator", Value.of(fileSystem.getSeparator()));
        section.put("Path separator", Value.of(File.pathSeparator));
        section.put("Supported attribute views",
                Value.list(new TreeSet<>(fileSystem.supportedFileAttributeViews()).stream().toList()));
        section.put("Root directories", Value.list(rootNames(fileSystem)));

        directories(section, context);
        stores(section, context, fileSystem);
        return section;
    }

    private static List<String> rootNames(FileSystem fileSystem) {
        java.util.List<String> roots = new java.util.ArrayList<>();
        fileSystem.getRootDirectories().forEach(root -> roots.add(root.toString()));
        return roots;
    }

    private void directories(Section section, InspectionContext context) {
        Section directories = section.sub("Directories");
        Table.Builder table = Table.builder("Directories the VM uses", "Purpose", "Path", "Exists", "Writable",
                "Usable space");
        record Entry(String purpose, String property) {
        }
        List<Entry> entries = List.of(
                new Entry("Working directory", "user.dir"),
                new Entry("Home directory", "user.home"),
                new Entry("Temporary directory", "java.io.tmpdir"),
                new Entry("Java installation", "java.home"));
        for (Entry entry : entries) {
            String value = context.property(entry.property());
            if (value == null) {
                continue;
            }
            Path path = Path.of(value);
            boolean exists = Files.exists(path);
            table.row(entry.purpose(), context.redactor().host(value), exists,
                    exists && Files.isWritable(path),
                    exists ? Value.bytesShort(usableSpace(path)) : Value.of("-"));
        }
        directories.table(table);
    }

    private void stores(Section section, InspectionContext context, FileSystem fileSystem) {
        Section stores = section.sub("File stores");
        Table.Builder table = Table.builder("Mounted stores", "Store", "Type", "Total", "Usable",
                "Unallocated", "Used", "Read only", "Block size");
        for (FileStore store : fileSystem.getFileStores()) {
            try {
                long total = store.getTotalSpace();
                long usable = store.getUsableSpace();
                long unallocated = store.getUnallocatedSpace();
                table.row(context.redactor().host(store.name()), store.type(),
                        Value.bytesShort(total), Value.bytesShort(usable), Value.bytesShort(unallocated),
                        total > 0 ? Value.percent((double) (total - unallocated) / total) : Value.of("-"),
                        store.isReadOnly(), Value.bytesShort(blockSize(store)));
            } catch (Exception ignored) {
                table.row(context.redactor().host(store.name()), store.type(), "-", "-", "-", "-", "-", "-");
            }
        }
        stores.table(table);
        if (table.size() == 0) {
            stores.note("No file stores could be enumerated.");
        }
    }

    private static long blockSize(FileStore store) {
        try {
            return store.getBlockSize();
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static long usableSpace(Path path) {
        try {
            return Files.getFileStore(path).getUsableSpace();
        } catch (Exception ignored) {
            return -1;
        }
    }
}
