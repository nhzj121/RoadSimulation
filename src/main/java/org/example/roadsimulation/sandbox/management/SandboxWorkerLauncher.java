package org.example.roadsimulation.sandbox.management;

import org.springframework.boot.system.ApplicationHome;
import java.nio.file.*;
import java.util.*;

/** No shell, user paths or client commands. Supports IDE classpaths and packaged Boot jars. */
public class SandboxWorkerLauncher {
    public Process launch(String jobId, SandboxManagementSettings settings) throws Exception {
        var home = new ApplicationHome(SandboxManagementWorker.class).getSource();
        List<String> command = command(jobId, Path.of(System.getProperty("java.home")),
                home == null ? null : home.toPath(),
                System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")), SandboxManagementWorker.class.getName());
        Path directory = Path.of("target", "sandbox-jobs").toAbsolutePath().normalize();
        Files.createDirectories(directory);
        var builder = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(directory.resolve(jobId + ".log").toFile());
        environment(builder.environment(), settings);
        Process child = builder.start(); child.getOutputStream().close(); return child;
    }

    public static List<String> command(String jobId, Path javaHome, Path source, String classpath, String main) {
        SandboxManagementJobStore.uuid(jobId);
        List<String> result = new ArrayList<>();
        String executable = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java";
        result.add(javaHome.resolve("bin").resolve(executable).toString());
        result.add("-Dspring.devtools.restart.enabled=false");
        if (source != null && source.toString().endsWith(".jar")) {
            result.add("-Dloader.main=" + main); result.add("-cp"); result.add(source.toString());
            result.add("org.springframework.boot.loader.launch.PropertiesLauncher");
        } else {
            if (classpath == null || classpath.isBlank()) throw new IllegalArgumentException("Missing worker classpath");
            result.add("-cp"); result.add(classpath); result.add(main);
        }
        result.add(jobId); return List.copyOf(result);
    }

    public static void environment(Map<String, String> environment, SandboxManagementSettings settings) {
        for (String name : List.of("DB_PASSWORD", "MYSQL_PWD", "MYSQL_ADMIN_PASSWORD", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS")) environment.remove(name);
        environment.put("SANDBOX_DB_URL", settings.url()); environment.put("SANDBOX_DB_USER", settings.user());
        environment.put("SANDBOX_DB_PASSWORD", settings.password());
    }
}
