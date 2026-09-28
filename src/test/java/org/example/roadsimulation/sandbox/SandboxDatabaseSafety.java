package org.example.roadsimulation.sandbox;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SandboxDatabaseSafety {

    static final String REQUIRED_DATABASE_PREFIX = "road_simulation_sandbox_test";

    private static final Pattern JDBC_DATABASE = Pattern.compile(
            "^jdbc:(?:mariadb|mysql)://[^/]+/([^?;]+).*$",
            Pattern.CASE_INSENSITIVE
    );

    private SandboxDatabaseSafety() {
    }

    static void requireEphemeralSandbox(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalStateException("Sandbox JDBC URL must not be blank");
        }

        Matcher matcher = JDBC_DATABASE.matcher(jdbcUrl.trim());
        if (!matcher.matches()) {
            throw new IllegalStateException("Unsupported sandbox JDBC URL: " + jdbcUrl);
        }

        String database = matcher.group(1).toLowerCase(Locale.ROOT);
        if (!database.startsWith(REQUIRED_DATABASE_PREFIX)) {
            throw new IllegalStateException(
                    "Refusing to run sandbox integration test against database: " + database
            );
        }
    }
}
