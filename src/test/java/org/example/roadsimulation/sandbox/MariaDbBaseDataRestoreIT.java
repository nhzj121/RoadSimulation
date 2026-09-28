package org.example.roadsimulation.sandbox;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MariaDbBaseDataRestoreIT extends SandboxMariaDbIntegrationTestSupport {

    private static final List<TableOrder> TABLES = List.of(
            new TableOrder("poi", "id"),
            new TableOrder("goods", "id"),
            new TableOrder("vehicle", "id"),
            new TableOrder("processing_chain", "id"),
            new TableOrder("processing_chain_predecessors", "chain_id, predecessor_chain_id"),
            new TableOrder("processing_stage", "id"),
            new TableOrder("enrollment", "id"),
            new TableOrder("vehicle_goods_match", "id")
    );

    private static final Map<String, Long> EXPECTED_NEXT_IDS = Map.of(
            "poi", 40L,
            "goods", 20L,
            "vehicle", 100L,
            "processing_chain", 120L,
            "processing_stage", 1100L,
            "enrollment", 300L,
            "vehicle_goods_match", 500L
    );

    @Test
    void restoresStableIdsRelationshipsAndAutoIncrementTwice() throws Exception {
        try (Connection connection = openConnection()) {
            assertPinnedEnvironment(connection);

            restoreFixture(connection);
            String firstFingerprint = fingerprint(connection);
            Map<String, Long> firstNextIds = autoIncrementState(connection);
            assertFixtureIntegrity(connection);

            restoreFixture(connection);
            String secondFingerprint = fingerprint(connection);
            Map<String, Long> secondNextIds = autoIncrementState(connection);
            assertFixtureIntegrity(connection);

            assertEquals(firstFingerprint, secondFingerprint);
            assertEquals(EXPECTED_NEXT_IDS, firstNextIds);
            assertEquals(firstNextIds, secondNextIds);

            assertGeneratedIds(connection);
        }
    }

    private Connection openConnection() throws SQLException {
        SandboxDatabaseSafety.requireEphemeralSandbox(MARIADB.getJdbcUrl());
        Connection connection = DriverManager.getConnection(
                MARIADB.getJdbcUrl(),
                MARIADB.getUsername(),
                MARIADB.getPassword()
        );
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET SESSION sql_mode = '" + SOURCE_SQL_MODE + "'");
        }
        return connection;
    }

    private void assertPinnedEnvironment(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT VERSION(), @@character_set_server, @@collation_server, " +
                             "@@time_zone, @@GLOBAL.sql_mode, @@SESSION.sql_mode"
             )) {
            assertTrue(result.next());
            assertTrue(result.getString(1).startsWith("10.4.32-MariaDB"));
            assertEquals("utf8mb4", result.getString(2));
            assertEquals("utf8mb4_general_ci", result.getString(3));
            assertEquals("+08:00", result.getString(4));
            List<String> expectedModes = List.of(
                    "NO_ENGINE_SUBSTITUTION", "NO_ZERO_DATE", "NO_ZERO_IN_DATE"
            );
            assertEquals(expectedModes, sortedSqlModes(result.getString(5)));
            assertEquals(expectedModes, sortedSqlModes(result.getString(6)));
        }
    }

    private List<String> sortedSqlModes(String sqlModes) {
        return List.of(sqlModes.split(",")).stream().sorted().toList();
    }

    private void restoreFixture(Connection connection) throws SQLException {
        ScriptUtils.executeSqlScript(
                connection,
                new ClassPathResource("sandbox/base-data-schema.sql")
        );
        ScriptUtils.executeSqlScript(
                connection,
                new ClassPathResource("sandbox/base-data-fixture.sql")
        );
    }

    private String fingerprint(Connection connection) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (TableOrder table : TABLES) {
            updateDigest(digest, "TABLE:" + table.tableName());
            try (Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery(
                         "SELECT * FROM " + table.tableName() + " ORDER BY " + table.orderBy()
                 )) {
                ResultSetMetaData metadata = rows.getMetaData();
                while (rows.next()) {
                    for (int column = 1; column <= metadata.getColumnCount(); column++) {
                        updateDigest(digest, metadata.getColumnLabel(column));
                        Object value = rows.getObject(column);
                        updateDigest(digest, value == null ? "<NULL>" : value.toString());
                    }
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private void updateDigest(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private Map<String, Long> autoIncrementState(Connection connection) throws SQLException {
        Map<String, Long> state = new LinkedHashMap<>();
        String sql = """
                SELECT TABLE_NAME, AUTO_INCREMENT
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME IN (
                    'poi', 'goods', 'vehicle', 'processing_chain',
                    'processing_stage', 'enrollment', 'vehicle_goods_match'
                  )
                ORDER BY TABLE_NAME
                """;
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                state.put(rows.getString(1), rows.getLong(2));
            }
        }
        return state;
    }

    private void assertFixtureIntegrity(Connection connection) throws SQLException {
        assertEquals(List.of(10L, 30L), ids(connection, "poi"));
        assertEquals(List.of(7L, 11L), ids(connection, "goods"));
        assertEquals(List.of(42L, 88L), ids(connection, "vehicle"));
        assertEquals(List.of(80L, 90L, 100L), ids(connection, "processing_chain"));
        assertEquals(List.of(805L, 905L, 1000L, 1005L), ids(connection, "processing_stage"));
        assertEquals(List.of(201L, 202L), ids(connection, "enrollment"));
        assertEquals(List.of(401L), ids(connection, "vehicle_goods_match"));

        assertEquals(0L, scalar(connection, """
                SELECT COUNT(*)
                FROM processing_stage s
                LEFT JOIN processing_chain c ON c.id = s.chain_id
                LEFT JOIN poi p ON p.id = s.poi_id
                LEFT JOIN goods gi ON gi.id = s.input_goods_id
                LEFT JOIN goods go ON go.id = s.output_goods_id
                WHERE c.id IS NULL OR p.id IS NULL
                   OR (s.input_goods_id IS NOT NULL AND gi.id IS NULL)
                   OR (s.output_goods_id IS NOT NULL AND go.id IS NULL)
                """));
        assertEquals(0L, scalar(connection, """
                SELECT COUNT(*)
                FROM processing_chain_predecessors p
                LEFT JOIN processing_chain owner_chain ON owner_chain.id = p.chain_id
                LEFT JOIN processing_chain predecessor ON predecessor.id = p.predecessor_chain_id
                WHERE owner_chain.id IS NULL OR predecessor.id IS NULL
                """));
        assertEquals(0L, scalar(connection, """
                SELECT COUNT(*)
                FROM processing_chain c
                LEFT JOIN processing_stage s
                  ON s.id = c.merge_stage_id AND s.chain_id = c.id
                WHERE c.merge_stage_id IS NOT NULL AND s.id IS NULL
                """));
        assertEquals(0L, scalar(connection, """
                SELECT COUNT(*)
                FROM enrollment e
                LEFT JOIN poi p ON p.id = e.poi_id
                LEFT JOIN goods g ON g.id = e.goods_id
                WHERE p.id IS NULL OR g.id IS NULL
                """));
        assertEquals(0L, scalar(connection, """
                SELECT COUNT(*)
                FROM vehicle_goods_match m
                LEFT JOIN vehicle v ON v.id = m.vehicle_id
                LEFT JOIN goods g ON g.id = m.goods_id
                WHERE v.id IS NULL OR g.id IS NULL
                """));
    }

    private List<Long> ids(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT id FROM " + table + " ORDER BY id")) {
            java.util.ArrayList<Long> ids = new java.util.ArrayList<>();
            while (rows.next()) {
                ids.add(rows.getLong(1));
            }
            return ids;
        }
    }

    private long scalar(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    private void assertGeneratedIds(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try {
            long poiId = insertAndReturnId(connection,
                    "INSERT INTO poi(name, longitude, latitude, poi_type) VALUES ('AUTO', 0, 0, 'TEST')");
            long goodsId = insertAndReturnId(connection,
                    "INSERT INTO goods(name, sku) VALUES ('AUTO', 'AUTO')");
            long vehicleId = insertAndReturnId(connection,
                    "INSERT INTO vehicle(license_plate, current_status, current_poi_id) " +
                            "VALUES ('AUTO', 'IDLE', " + poiId + ")");
            long chainId = insertAndReturnId(connection,
                    "INSERT INTO processing_chain(chain_code, chain_name, status) " +
                            "VALUES ('AUTO', 'AUTO', 'ACTIVE')");
            long stageId = insertAndReturnId(connection,
                    "INSERT INTO processing_stage(" +
                            "chain_id, stage_order, stage_name, poi_id, input_goods_id, output_goods_id, " +
                            "input_weight_ratio, output_weight_ratio, processing_time_minutes" +
                            ") VALUES (" + chainId + ", 1, 'AUTO', " + poiId + ", " + goodsId + ", " +
                            goodsId + ", 1.0, 1.0, 1)");
            long enrollmentId = insertAndReturnId(connection,
                    "INSERT INTO enrollment(quantity, goods_id, poi_id, version) VALUES (1, " +
                            goodsId + ", " + poiId + ", 0)");
            long matchId = insertAndReturnId(connection,
                    "INSERT INTO vehicle_goods_match(goods_id, vehicle_id, match_status) VALUES (" +
                            goodsId + ", " + vehicleId + ", 'PENDING')");

            assertEquals(EXPECTED_NEXT_IDS.get("poi"), poiId);
            assertEquals(EXPECTED_NEXT_IDS.get("goods"), goodsId);
            assertEquals(EXPECTED_NEXT_IDS.get("vehicle"), vehicleId);
            assertEquals(EXPECTED_NEXT_IDS.get("processing_chain"), chainId);
            assertEquals(EXPECTED_NEXT_IDS.get("processing_stage"), stageId);
            assertEquals(EXPECTED_NEXT_IDS.get("enrollment"), enrollmentId);
            assertEquals(EXPECTED_NEXT_IDS.get("vehicle_goods_match"), matchId);
        } finally {
            connection.rollback();
            connection.setAutoCommit(true);
        }
    }

    private long insertAndReturnId(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                sql,
                Statement.RETURN_GENERATED_KEYS
        )) {
            assertEquals(1, statement.executeUpdate());
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getLong(1);
            }
        }
    }

    private record TableOrder(String tableName, String orderBy) {
    }
}
