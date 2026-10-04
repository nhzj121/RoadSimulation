package org.example.roadsimulation.sandbox.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceException;

import java.sql.*;
import java.util.*;

/** Full SQL facts, plus a separately documented deterministic business projection. */
public final class SandboxBusinessFactCapture {
    public static final String LEGACY_VERSION = "sandbox-business-facts/v1";
    public static final String VERSION = "sandbox-business-facts/v2";
    private static final Map<String, Set<String>> JSON_OBJECT_COLUMNS = Map.of(
            "weather_scenario", Set.of("definition_json"),
            "weather_run", Set.of("frozen_scenario_json", "frozen_event_configuration_json"));
    private static final Set<String> AUDIT = Set.of("created_at", "created_time", "updated_at", "updated_time", "created_by", "updated_by");
    private static final Map<String, Set<String>> KEEP_CREATION = Map.of(
            "shipment", Set.of("created_at"), "shipment_item", Set.of("created_time"), "assignment", Set.of("created_time"));
    private static final Map<String, Set<String>> DISPLAY = Map.of(
            "production_plan", Set.of("plan_no"), "production_batch", Set.of("batch_no"), "shipment", Set.of("ref_no"));
    private static final Set<String> WEATHER_RUN_TABLES = Set.of("weather_run", "driving_progress", "transport_random_event",
            "vehicle_replacement_attempt", "transport_execution_segment");
    private final ObjectMapper json;
    private final ObjectMapper embeddedJson = new ObjectMapper()
            .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .setNodeFactory(com.fasterxml.jackson.databind.node.JsonNodeFactory.withExactBigDecimals(true));
    private final SandboxExecutionJson canonical;
    private final SandboxBusinessFactSchema schema = new SandboxBusinessFactSchema();

    public SandboxBusinessFactCapture(ObjectMapper json) { this.json = json; canonical = new SandboxExecutionJson(json); }

    public ObjectNode readDatabase(Connection connection) throws SQLException {
        Set<String> expectedTables = new HashSet<>(schema.tables().keySet());
        expectedTables.addAll(Set.of("sandbox_workspace_marker", "sandbox_scenario", "sandbox_scenario_revision",
                "sandbox_run_spec", "sandbox_run_spec_revision", "sandbox_run_vehicle_initial_state",
                "sandbox_execution", "sandbox_execution_tick"));
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                "SELECT control_schema_version FROM sandbox_workspace_marker WHERE marker_id=1")) {
            if (rows.next() && java.util.Set.of("sandbox-control-schema/v7", "sandbox-control-schema/v8", "sandbox-control-schema/v9").contains(rows.getString(1))) expectedTables.add("sandbox_management_job");
        }
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE'")) {
            Set<String> actualTables = new HashSet<>();
            while (rows.next()) actualTables.add(rows.getString(1));
            if (!actualTables.equals(expectedTables)) throw new SandboxWorkspaceException("FACT_SCHEMA_MISMATCH", "Business/control table set differs from the journal contract");
        }
        ObjectNode result = json.createObjectNode();
        for (var entry : schema.tables().entrySet()) {
            String table = entry.getKey();
            ArrayNode data = result.putArray(table);
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT * FROM `" + table + "`")) {
                var metadata = rows.getMetaData();
                Set<String> actual = new HashSet<>();
                for (int i = 1; i <= metadata.getColumnCount(); i++) actual.add(metadata.getColumnLabel(i));
                if (!actual.equals(new HashSet<>(entry.getValue()))) {
                    throw new SandboxWorkspaceException("FACT_SCHEMA_MISMATCH", "Fact columns differ from versioned DDL: " + table);
                }
                List<ObjectNode> records = new ArrayList<>();
                while (rows.next()) {
                    ObjectNode row = json.createObjectNode();
                    for (int i = 1; i <= metadata.getColumnCount(); i++) {
                        String column = metadata.getColumnLabel(i);
                        Object value = rows.getObject(i);
                        if (value instanceof Timestamp time) value = time.toLocalDateTime().toString();
                        else if (value != null && (metadata.getColumnType(i) == Types.BIT || metadata.getColumnType(i) == Types.BOOLEAN)) value = rows.getBoolean(i);
                        else if (value instanceof byte[]) throw new SandboxWorkspaceException("UNSUPPORTED_FACT_TYPE", "Unexpected binary fact: " + table + "." + column);
                        if ((value instanceof Double number && !Double.isFinite(number))
                                || (value instanceof Float floatNumber && !Float.isFinite(floatNumber))) {
                            throw new SandboxWorkspaceException("INVALID_FACT_NUMBER", "Nonfinite fact: " + table + "." + column);
                        }
                        row.set(column, json.valueToTree(value));
                    }
                    records.add(row);
                }
                sort(records); records.forEach(data::add);
            }
        }
        return result;
    }

    public ObjectNode project(ObjectNode database) {
        ObjectNode result = json.createObjectNode();
        if (database.path("weather_run").size() > 1) throw new SandboxWorkspaceException("FACT_RUN_AMBIGUOUS", "One controlled execution may contain only one weather run");
        String weatherId = database.path("weather_run").isEmpty() ? null : database.path("weather_run").get(0).path("id").asText();
        var fields = database.fields();
        while (fields.hasNext()) {
            var table = fields.next();
            // The immutable evaluation is recorded separately, not the SQL history's technical row ID.
            if (table.getKey().equals("evaluation_snapshot_history")) continue;
            List<ObjectNode> records = new ArrayList<>();
            for (JsonNode value : table.getValue()) {
                ObjectNode row = value.deepCopy();
                for (String column : AUDIT) {
                    if (!KEEP_CREATION.getOrDefault(table.getKey(), Set.of()).contains(column)) row.remove(column);
                }
                for (String column : DISPLAY.getOrDefault(table.getKey(), Set.of())) row.remove(column);
                if (weatherId != null && WEATHER_RUN_TABLES.contains(table.getKey())) {
                    for (String column : List.of("run_id", "phase_key", "original_driving_phase_key", "id")) {
                        JsonNode field = row.get(column);
                        if (field != null && field.isTextual() && field.asText().contains(weatherId)) {
                            row.put(column, field.asText().replace(weatherId, "WEATHER_RUN"));
                        }
                    }
                }
                records.add(row);
            }
            sort(records); ArrayNode array = result.putArray(table.getKey()); records.forEach(array::add);
        }
        return result;
    }

    public ObjectNode projectFacts(ObjectNode facts) {
        String version = facts.path("artifactVersion").asText();
        if (!LEGACY_VERSION.equals(version) && !VERSION.equals(version)) {
            throw new SandboxWorkspaceException("UNSUPPORTED_FACT_VERSION", "Unknown business fact projection: " + version);
        }
        ObjectNode result = facts.deepCopy();
        result.set("database", project((ObjectNode) facts.path("database")));
        if (VERSION.equals(version)) normalizeEmbeddedObjects((ObjectNode) result.path("database"));
        // These cost-window timestamps are display/audit only; the arithmetic uses
        // item scale and cost differences, never elapsed wall-clock duration.
        JsonNode window = result.path("runtime").path("normalizationWindow");
        if (window.isObject()) ((ObjectNode) window).remove(List.of("windowStartTime", "windowEndTime"));
        return result;
    }

    private void normalizeEmbeddedObjects(ObjectNode database) {
        for (var entry : JSON_OBJECT_COLUMNS.entrySet()) {
            for (JsonNode row : database.path(entry.getKey())) {
                for (String column : entry.getValue()) {
                    JsonNode value = row.get(column);
                    if (value == null || value.isNull()) continue;
                    if (!value.isTextual()) throw new SandboxWorkspaceException("INVALID_EMBEDDED_FACT_JSON", "SQL JSON fact is not text: " + entry.getKey() + "." + column);
                    try {
                        JsonNode parsed = embeddedJson.readTree(value.asText());
                        if (parsed == null || !parsed.isObject()) throw new IllegalArgumentException("Expected JSON object");
                        requireFinite(parsed);
                        ((ObjectNode) row).set(column, parsed);
                    } catch (Exception failure) {
                        throw new SandboxWorkspaceException("INVALID_EMBEDDED_FACT_JSON", "Invalid JSON object: " + entry.getKey() + "." + column, failure);
                    }
                }
            }
        }
    }

    static void requireFinite(JsonNode value) {
        if ((value.isDouble() || value.isFloat()) && !Double.isFinite(value.doubleValue())) {
            throw new SandboxWorkspaceException("INVALID_FACT_NUMBER", "A runtime fact is NaN or infinite");
        }
        if (value.isContainerNode()) value.forEach(SandboxBusinessFactCapture::requireFinite);
    }

    private void sort(List<ObjectNode> records) {
        records.sort((left, right) -> {
            if (left.path("id").isIntegralNumber() && right.path("id").isIntegralNumber()) {
                int compared = Long.compare(left.path("id").asLong(), right.path("id").asLong());
                if (compared != 0) return compared;
            }
            return Arrays.compareUnsigned(canonical.canonicalBytes(left), canonical.canonicalBytes(right));
        });
    }
}
