package org.example.roadsimulation.sandbox;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@ActiveProfiles("sandbox-test")
@Testcontainers(disabledWithoutDocker = true)
abstract class SandboxMariaDbIntegrationTestSupport {

    static final String IMAGE = "mariadb:10.4.32";
    static final String DATABASE = "road_simulation_sandbox_test";
    static final String SOURCE_SQL_MODE =
            "NO_ZERO_IN_DATE,NO_ZERO_DATE,NO_ENGINE_SUBSTITUTION";

    @Container
    protected static final MariaDBContainer<?> MARIADB =
            new MariaDBContainer<>(DockerImageName.parse(IMAGE))
                    .withDatabaseName(DATABASE)
                    .withUsername("sandbox")
                    .withPassword("sandbox")
                    .withEnv("TZ", "Asia/Shanghai")
                    .withCommand(
                            "--character-set-server=utf8mb4",
                            "--collation-server=utf8mb4_general_ci",
                            "--sql-mode=" + SOURCE_SQL_MODE,
                            "--default-time-zone=+08:00"
                    );

    @DynamicPropertySource
    static void sandboxDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MARIADB::getJdbcUrl);
        registry.add("spring.datasource.username", MARIADB::getUsername);
        registry.add("spring.datasource.password", MARIADB::getPassword);
        registry.add("spring.datasource.driver-class-name", MARIADB::getDriverClassName);
    }

    @BeforeAll
    static void rejectUnsafeDatabaseTarget() {
        SandboxDatabaseSafety.requireEphemeralSandbox(MARIADB.getJdbcUrl());
    }
}
