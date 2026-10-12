package org.example.roadsimulation.sandbox.workspace;

import org.example.roadsimulation.RoadSimulationApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "sandbox.management.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = RoadSimulationApplication.class)
@EnabledIfEnvironmentVariable(named = "SANDBOX_XAMPP_IT", matches = "(?i)true")
class SandboxJpaSchemaValidationXamppIT {

    @DynamicPropertySource
    static void sandboxDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () ->
                "jdbc:mysql://localhost:3306/vehicle_scheduler_sandbox"
                        + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
                        + "&characterEncoding=utf8&useUnicode=true&zeroDateTimeBehavior=CONVERT_TO_NULL");
        registry.add("spring.datasource.username", () -> environmentOr(
                "SANDBOX_DB_USER", "road_sandbox_runtime"));
        registry.add("spring.datasource.password", () -> requiredEnvironment("SANDBOX_DB_PASSWORD"));
    }

    @Test
    void currentJpaMappingValidatesAgainstVersionedSandboxSchema() {
        assertTrue(true);
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set");
        }
        return value;
    }

    private static String environmentOr(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
