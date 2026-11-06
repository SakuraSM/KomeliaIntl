import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Verify that every advertised Android ABI contains the app's native libraries. */
public final class CheckAndroidNativeAbis {
    private record Architecture(int elfClass, int machine) {}
    private static final Map<String, Architecture> ARCHITECTURES = Map.of(
        "armeabi-v7a", new Architecture(1, 40),
        "arm64-v8a", new Architecture(2, 183),
        "x86", new Architecture(1, 3),
        "x86_64", new Architecture(2, 62)
    );

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            throw new IllegalArgumentException("Usage: CheckAndroidNativeAbis <apk-or-aab> <arm64-jni-dir> <arm64-sqlite-dir>");
        }
        Set<String> required = new TreeSet<>();
        for (int index = 1; index < args.length; index++) {
            try (var files = Files.list(Path.of(args[index]))) {
                files.map(path -> path.getFileName().toString()).filter(name -> name.endsWith(".so")).forEach(required::add);
            }
        }
        if (!required.containsAll(Set.of("libkomelia_vips.so", "libkomelia_android_bitmap.so",
                "libkomelia_onnxruntime.so", "libonnxruntime.so", "libsqlitejdbc.so"))) {
            throw new IllegalStateException("The ARM64 native library baseline is incomplete");
        }
        try (var archive = new ZipFile(args[0])) {
            Map<String, Map<String, ZipEntry>> entries = new HashMap<>();
            var zipEntries = archive.entries();
            while (zipEntries.hasMoreElements()) {
                ZipEntry entry = zipEntries.nextElement();
                String name = entry.getName().startsWith("base/") ? entry.getName().substring(5) : entry.getName();
                String[] parts = name.split("/");
                if (parts.length == 3 && parts[0].equals("lib") && parts[2].endsWith(".so")) {
                    if (entries.computeIfAbsent(parts[1], ignored -> new HashMap<>()).put(parts[2], entry) != null) {
                        throw new IllegalStateException("Duplicate native library: " + name);
                    }
                }
            }
            Set<String> missingAbis = new TreeSet<>(ARCHITECTURES.keySet());
            missingAbis.removeAll(entries.keySet());
            if (!missingAbis.isEmpty()) throw new IllegalStateException("Missing Android ABIs: " + missingAbis);
            Set<String> unknownAbis = new TreeSet<>(entries.keySet());
            unknownAbis.removeAll(ARCHITECTURES.keySet());
            if (!unknownAbis.isEmpty()) throw new IllegalStateException("Unknown Android ABIs: " + unknownAbis);
            var errors = new ArrayList<String>();
            for (String abi : new TreeSet<>(ARCHITECTURES.keySet())) {
                var libraries = entries.get(abi);
                Set<String> missing = new TreeSet<>(required);
                missing.removeAll(libraries.keySet());
                if (!missing.isEmpty()) errors.add(abi + " missing " + missing);
                for (String library : required) {
                    ZipEntry entry = libraries.get(library);
                    if (entry == null) continue;
                    byte[] header;
                    try (var input = archive.getInputStream(entry)) { header = input.readNBytes(20); }
                    Architecture expected = ARCHITECTURES.get(abi);
                    if (header.length < 20 || header[0] != 0x7f || header[1] != 'E' || header[2] != 'L' ||
                            header[3] != 'F' || (header[4] & 255) != expected.elfClass() || header[5] != 1 ||
                            header[16] != 3 || header[17] != 0 ||
                            ((header[18] & 255) | ((header[19] & 255) << 8)) != expected.machine()) {
                        errors.add(abi + " has an invalid ELF architecture: " + library);
                    }
                }
            }
            if (!errors.isEmpty()) throw new IllegalStateException(String.join("\n", errors));
            System.out.println("ANDROID_NATIVE_ABIS_PASSED: " + entries.keySet() + "; " + required.size() + " libraries per ABI");
        }
    }
}
