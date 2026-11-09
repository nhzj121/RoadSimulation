package org.example.roadsimulation.sandbox.optimization;

import org.springframework.boot.system.ApplicationHome;

import java.io.IOException;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.regex.Pattern;

/** Identifies the actual launch artifact or ordered development classpath, including resources. */
public record EvaluationBuildIdentity(String classpathFingerprint, String jdkFingerprint) {
    public static String classpath() {
        String raw = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        // Surefire can append a separator. Do not let Java interpret empty entries as the working directory.
        return String.join(java.io.File.pathSeparator, Arrays.stream(raw.split(Pattern.quote(java.io.File.pathSeparator), -1))
                .filter(entry -> !entry.isEmpty()).toList());
    }

    public static Path packagedSource(Class<?> main) {
        var source = new ApplicationHome(main).getSource();
        return source != null && source.toString().endsWith(".jar") ? source.toPath() : null;
    }

    public static EvaluationBuildIdentity capture(Class<?> main) throws IOException {
        Path jar = packagedSource(main);
        String classpath = classpath();
        if (jar == null && classpath.isBlank()) throw new IOException("Evaluation requires an explicit worker classpath");
        List<Path> paths = jar != null ? List.of(jar) : Arrays.stream(classpath.split(
                Pattern.quote(java.io.File.pathSeparator), -1)).map(Path::of).toList();
        var entries = new ArrayList<Object>();
        for (Path path : paths) {
            if (Files.isRegularFile(path)) entries.add(fileSha256(path));
            else if (Files.isDirectory(path)) {
                try (var files = Files.walk(path)) {
                    var resources = new TreeMap<String, String>();
                    for (Path file : files.filter(Files::isRegularFile).toList())
                        resources.put(path.relativize(file).toString().replace('\\', '/'), fileSha256(file));
                    entries.add(resources);
                }
            } else {
                throw new IOException("Evaluation classpath contains an unavailable entry");
            }
        }
        var jdk = List.of(System.getProperty("java.runtime.version"), System.getProperty("java.vendor"),
                System.getProperty("java.vm.name"), System.getProperty("java.vm.version"),
                System.getProperty("os.name"), System.getProperty("os.arch"));
        return new EvaluationBuildIdentity(OptimizationJson.fingerprint(entries), OptimizationJson.fingerprint(jdk));
    }

    public static String fileSha256(Path file) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var stream = new DigestInputStream(Files.newInputStream(file), digest)) {
                stream.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
