package org.example.roadsimulation.sandbox.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.roadsimulation.sandbox.baseline.EffectiveBaseData;
import org.example.roadsimulation.sandbox.baseline.EffectiveBaseDataCodec;
import org.example.roadsimulation.sandbox.baseline.LoadedSandboxBaseline;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselineLoader;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledIfEnvironmentVariable(named = "SANDBOX_XAMPP_IT", matches = "(?i)true")
class SandboxWorkspaceXamppIT {

    private static final String SANDBOX_URL = "jdbc:mysql://localhost:3306/vehicle_scheduler_sandbox"
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
            + "&characterEncoding=utf8&useUnicode=true&zeroDateTimeBehavior=CONVERT_TO_NULL";
    private static final String SOURCE_URL = "jdbc:mysql://localhost:3306/vehicle_scheduler"
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
            + "&characterEncoding=utf8&useUnicode=true&zeroDateTimeBehavior=CONVERT_TO_NULL";
    private static final List<String> SOURCE_TABLES = List.of(
            "poi", "goods", "vehicle", "processing_chain", "processing_stage",
            "processing_stage_input", "processing_stage_edge", "enrollment",
            "driver", "driver_vehicle");

    @Test
    void preparesActualBaselineRepeatedlyWithoutChangingSource() throws Exception {
        String password = requiredEnvironment("SANDBOX_DB_PASSWORD");
        Map<String, Long> sourceBefore = sourceCounts();
        String sourceFactsBefore = sourceFacts();
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        SandboxWorkspacePreparer preparer = new SandboxWorkspacePreparer(
                SANDBOX_URL, "road_sandbox_runtime", password,
                objectMapper);
        ClassPathResource baseline = new ClassPathResource("sandbox/baseline/baseline-v1.json");
        SandboxBaselineLoader loader = new SandboxBaselineLoader(objectMapper);
        LoadedSandboxBaseline loaded = loader.load(baseline);
        EffectiveBaseData full = loader.selectAllEligible(loaded);

        SandboxPreparationReport first = preparer.prepare(loaded, full);
        SandboxPreparationReport second = preparer.prepare(loaded, full);
        SandboxPreparationReport verified = preparer.verify(baseline);

        assertEquals(SandboxWorkspaceState.BASE_DATA_READY, first.state());
        assertEquals(first.effectiveBaseDataSha256(), second.effectiveBaseDataSha256());
        assertEquals(second.effectiveBaseDataSha256(), verified.effectiveBaseDataSha256());

        EffectiveBaseData minimal = minimalData(loaded, full, objectMapper);
        SandboxPreparationReport b = preparer.prepare(loaded, minimal);
        assertEquals(4L, b.rowCounts().get("poi"));
        assertEquals(3L, b.rowCounts().get("goods"));
        assertEquals(1L, b.rowCounts().get("vehicle"));

        SandboxPreparationReport restoredA = preparer.prepare(loaded, full);
        assertEquals(first.effectiveBaseDataSha256(), restoredA.effectiveBaseDataSha256());
        assertNeutralVehicleStateAndAutoIncrement();

        SandboxWorkspacePreparer broken = new SandboxWorkspacePreparer(
                SANDBOX_URL, "road_sandbox_runtime", password, objectMapper,
                new ByteArrayResource("CREATE TABLE broken(".getBytes(StandardCharsets.UTF_8)));
        assertThrows(SandboxWorkspaceException.class, () -> broken.prepare(loaded, full));
        assertEquals("FAILED", markerState());
        assertEquals(SandboxWorkspaceState.BASE_DATA_READY, preparer.prepare(loaded, full).state());

        assertEquals(sourceBefore, sourceCounts());
        assertEquals(sourceFactsBefore, sourceFacts());
    }

    private EffectiveBaseData minimalData(
            LoadedSandboxBaseline loaded,
            EffectiveBaseData full,
            ObjectMapper objectMapper
    ) {
        var chain = full.data().processingChains().get(0);
        Set<Long> poiIds = chain.stages().stream()
                .map(SandboxBaselinePackageV1.ProcessingStage::processingPoiId)
                .collect(java.util.stream.Collectors.toSet());
        Set<Long> goodsIds = new java.util.HashSet<>();
        chain.stages().forEach(stage -> {
            if (stage.legacyInputGoodsId() != null) goodsIds.add(stage.legacyInputGoodsId());
            if (stage.outputGoodsId() != null) goodsIds.add(stage.outputGoodsId());
            stage.inputs().forEach(input -> goodsIds.add(input.goodsId()));
        });
        var vehicle = full.data().vehicles().get(0);
        var bindings = full.data().driverVehicleBindings().stream()
                .filter(binding -> binding.vehicleId() == vehicle.id()).toList();
        Set<Long> driverIds = bindings.stream()
                .map(SandboxBaselinePackageV1.DriverVehicleBinding::driverId)
                .collect(java.util.stream.Collectors.toSet());
        var data = new SandboxBaselinePackageV1.Data(
                full.data().pois().stream().filter(value -> poiIds.contains(value.id())).toList(),
                full.data().goods().stream().filter(value -> goodsIds.contains(value.id())).toList(),
                List.of(vehicle), List.of(chain), List.of(),
                full.data().drivers().stream().filter(driver -> driverIds.contains(driver.id())).toList(),
                bindings);
        EffectiveBaseDataCodec codec = new EffectiveBaseDataCodec(objectMapper);
        String selection = "TEST_MINIMAL";
        String policy = loaded.baseline().eligibilityPolicy().policyVersion();
        return new EffectiveBaseData(selection, policy, codec.normalize(data), codec.hash(selection, policy, data));
    }

    private void assertNeutralVehicleStateAndAutoIncrement() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                SANDBOX_URL, "road_sandbox_runtime", requiredEnvironment("SANDBOX_DB_PASSWORD"));
             Statement statement = connection.createStatement()) {
            try (ResultSet rows = statement.executeQuery("""
                    SELECT COUNT(*) FROM vehicle
                    WHERE current_status<>'IDLE' OR current_poi_id IS NOT NULL
                       OR current_longitude IS NOT NULL OR current_latitude IS NOT NULL
                       OR current_load<>0 OR `current-volumn`<>0
                    """)) {
                rows.next();
                assertEquals(0L, rows.getLong(1));
            }
            try (ResultSet rows = statement.executeQuery("""
                    SELECT TABLE_NAME,AUTO_INCREMENT FROM information_schema.TABLES
                    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('vehicle','processing_stage')
                    ORDER BY TABLE_NAME
                    """)) {
                Map<String, Long> nextIds = new LinkedHashMap<>();
                while (rows.next()) nextIds.put(rows.getString(1), rows.getLong(2));
                assertEquals(Map.of("processing_stage", 5104L, "vehicle", 89L), nextIds);
            }
        }
    }

    private String markerState() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                SANDBOX_URL, "road_sandbox_runtime", requiredEnvironment("SANDBOX_DB_PASSWORD"));
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT workspace_state FROM sandbox_workspace_marker WHERE marker_id=1")) {
            rows.next();
            return rows.getString(1);
        }
    }

    private Map<String, Long> sourceCounts() throws Exception {
        Map<String, Long> result = new LinkedHashMap<>();
        try (Connection connection = DriverManager.getConnection(SOURCE_URL, "root", "")) {
            for (String table : SOURCE_TABLES) {
                try (Statement statement = connection.createStatement();
                     ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM `" + table + "`")) {
                    rows.next();
                    result.put(table, rows.getLong(1));
                }
            }
        }
        return result;
    }

    private String sourceFacts() throws Exception {
        try (Connection connection = DriverManager.getConnection(SOURCE_URL, "root", "");
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT CONCAT(
                       (SELECT CONCAT(poi_id,':',input_weight_ratio,':',output_weight_ratio)
                        FROM processing_stage WHERE id=5102), '|',
                       (SELECT COUNT(*) FROM processing_stage
                        WHERE input_weight_ratio=1.0 AND output_weight_ratio=1.0)
                     )
                     """)) {
            rows.next();
            return rows.getString(1);
        }
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set");
        }
        return value;
    }
}
