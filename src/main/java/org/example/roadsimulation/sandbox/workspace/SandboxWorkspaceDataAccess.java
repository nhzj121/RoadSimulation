package org.example.roadsimulation.sandbox.workspace;

import org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1.Data;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1.Driver;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1.DriverVehicleBinding;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1.Goods;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1.InitialInventory;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1.Poi;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1.ProcessingChain;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1.ProcessingEdge;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1.ProcessingInput;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1.ProcessingStage;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1.Vehicle;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JDBC mapping for the phase-one static base-data boundary. */
final class SandboxWorkspaceDataAccess {

    private static final ZoneId CONTRACT_ZONE = ZoneId.of("Asia/Shanghai");

    void restore(Connection connection, Data data, Instant capturedAtUtc) throws SQLException {
        restore(connection, data, capturedAtUtc, Map.of());
    }

    void restore(
            Connection connection,
            Data data,
            Instant capturedAtUtc,
            Map<Long, Long> fixedVehiclePois
    ) throws SQLException {
        Timestamp fixedTimestamp = Timestamp.valueOf(LocalDateTime.ofInstant(capturedAtUtc, CONTRACT_ZONE));
        insertPois(connection, data.pois());
        insertGoods(connection, data.goods());
        insertVehicles(connection, data.vehicles(), fixedTimestamp, fixedVehiclePois);
        insertDrivers(connection, data.drivers(), fixedTimestamp);
        insertDriverVehicleBindings(connection, data.driverVehicleBindings());
        insertChains(connection, data.processingChains());
        insertStages(connection, data.processingChains());
        insertInputs(connection, data.processingChains(), fixedTimestamp);
        insertEdges(connection, data.processingChains(), fixedTimestamp);
        insertInventories(connection, data.initialInventories(), fixedTimestamp);
        setDeterministicAutoIncrements(connection);
    }

    Data read(Connection connection) throws SQLException {
        List<Poi> pois = readPois(connection);
        List<Goods> goods = readGoods(connection);
        List<Vehicle> vehicles = readVehicles(connection);
        List<Driver> drivers = readDrivers(connection);
        List<DriverVehicleBinding> driverVehicleBindings = readDriverVehicleBindings(connection);
        List<ProcessingChain> chains = readChains(connection);
        List<InitialInventory> inventories = readInventories(connection);
        return new Data(pois, goods, vehicles, chains, inventories, drivers, driverVehicleBindings);
    }

    Map<String, Long> counts(Connection connection) throws SQLException {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : List.of(
                "poi", "goods", "vehicle", "processing_chain", "processing_stage",
                "processing_stage_input", "processing_stage_edge", "enrollment",
                "driver", "driver_vehicle")) {
            try (Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM `" + table + "`")) {
                rows.next();
                counts.put(table, rows.getLong(1));
            }
        }
        return counts;
    }

    private void insertPois(Connection connection, List<Poi> values) throws SQLException {
        String sql = "INSERT INTO poi(id,name,longitude,latitude,poi_type) VALUES (?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Poi value : values) {
                statement.setLong(1, value.id());
                statement.setString(2, value.name());
                statement.setBigDecimal(3, value.longitude());
                statement.setBigDecimal(4, value.latitude());
                statement.setString(5, value.poiType());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertGoods(Connection connection, List<Goods> values) throws SQLException {
        String sql = """
                INSERT INTO goods(
                    id,category,description,hazmat_level,name,require_temp,shelf_life_days,sku,
                    volume_per_unit,weight_per_unit,vehicle_fit
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Goods value : values) {
                statement.setLong(1, value.id());
                statement.setString(2, value.category());
                statement.setString(3, value.description());
                statement.setString(4, value.hazmatLevel());
                statement.setString(5, value.name());
                setNullableBoolean(statement, 6, value.requiresTemperatureControl());
                setNullableInteger(statement, 7, value.shelfLifeDays());
                statement.setString(8, value.sku());
                setNullableDecimal(statement, 9, value.volumePerUnitCubicMeters());
                setNullableDecimal(statement, 10, value.weightPerUnitTonnes());
                statement.setString(11, value.vehicleFit());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    Map<Long, Long> readFixedVehiclePois(Connection connection) throws SQLException {
        Map<Long, Long> result = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT id,current_poi_id FROM vehicle WHERE current_poi_id IS NOT NULL ORDER BY id")) {
            while (rows.next()) {
                result.put(rows.getLong(1), rows.getLong(2));
            }
        }
        return Map.copyOf(result);
    }

    private void insertVehicles(
            Connection connection,
            List<Vehicle> values,
            Timestamp fixedTimestamp,
            Map<Long, Long> fixedVehiclePois
    )
            throws SQLException {
        String sql = """
                INSERT INTO vehicle(
                    id,brand,cargo_volume,created_time,current_load,current_status,`current-volumn`,
                    height,length,license_plate,max_load_capacity,model_type,status_duration_seconds,
                    `suitable-goods`,vehicle_type,width,`loop-count`,empty_driving_distance,
                    empty_driving_time,loading_wait_time,total_driving_distance,total_driving_time,
                    has_temp_control,hazmat_qualification,special_vehicle_type,unloading_wait_time,
                    waiting_assignment_time,empty_distance_meters,empty_driving_seconds,
                    loaded_distance_meters,loaded_driving_seconds,loading_wait_seconds,
                    total_distance_meters,total_driving_seconds,unloading_wait_seconds,
                    waiting_assignment_seconds,current_poi_id
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Vehicle value : values) {
                int index = 1;
                statement.setLong(index++, value.id());
                statement.setString(index++, value.brand());
                setNullableDecimal(statement, index++, value.cargoVolumeCubicMeters());
                statement.setTimestamp(index++, fixedTimestamp);
                statement.setBigDecimal(index++, BigDecimal.ZERO);
                statement.setString(index++, "IDLE");
                statement.setBigDecimal(index++, BigDecimal.ZERO);
                setNullableDecimal(statement, index++, value.heightMeters());
                setNullableDecimal(statement, index++, value.lengthMeters());
                statement.setString(index++, value.licensePlate());
                setNullableDecimal(statement, index++, value.maxLoadCapacityTonnes());
                statement.setString(index++, value.modelType());
                statement.setLong(index++, 0L);
                statement.setString(index++, value.suitableGoods());
                statement.setString(index++, value.vehicleType());
                setNullableDecimal(statement, index++, value.widthMeters());
                statement.setInt(index++, 0);
                for (int ignored = 0; ignored < 5; ignored++) {
                    statement.setBigDecimal(index++, BigDecimal.ZERO);
                }
                setNullableBoolean(statement, index++, value.hasTemperatureControl());
                statement.setString(index++, value.hazmatQualification());
                statement.setString(index++, value.specialVehicleType());
                for (int ignored = 0; ignored < 11; ignored++) {
                    statement.setBigDecimal(index++, BigDecimal.ZERO);
                }
                Long fixedPoiId = fixedVehiclePois.get(value.id());
                if (fixedPoiId == null) {
                    statement.setNull(index++, java.sql.Types.BIGINT);
                } else {
                    statement.setLong(index++, fixedPoiId);
                }
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertChains(Connection connection, List<ProcessingChain> chains) throws SQLException {
        String sql = """
                INSERT INTO processing_chain(id,chain_code,chain_name,description,status)
                VALUES (?,?,?,?,?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (ProcessingChain chain : chains) {
                statement.setLong(1, chain.id());
                statement.setString(2, chain.chainCode());
                statement.setString(3, chain.chainName());
                statement.setString(4, chain.description());
                statement.setString(5, chain.status());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertDrivers(Connection connection, List<Driver> values, Timestamp fixedTimestamp)
            throws SQLException {
        String sql = """
                INSERT INTO driver(
                    id,created_time,current_status,driver_name,driver_phone,updated_by,updated_time,
                    pref_cargo,pref_max_distance_km,pref_max_weight_tons
                ) VALUES (?,?,?,?,?,?,?,?,?,?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Driver value : values) {
                statement.setLong(1, value.id());
                statement.setTimestamp(2, fixedTimestamp);
                statement.setString(3, "IDLE");
                statement.setString(4, value.driverName());
                statement.setString(5, value.driverPhone());
                statement.setString(6, "SANDBOX_BASELINE_RESTORE");
                statement.setTimestamp(7, fixedTimestamp);
                statement.setString(8, value.preferredCargoType());
                statement.setBigDecimal(9, value.preferredMaxDistanceKm());
                statement.setBigDecimal(10, value.preferredMaxWeightTons());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertDriverVehicleBindings(Connection connection, List<DriverVehicleBinding> values)
            throws SQLException {
        String sql = "INSERT INTO driver_vehicle(driver_id,vehicle_id) VALUES (?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (DriverVehicleBinding value : values) {
                statement.setLong(1, value.driverId());
                statement.setLong(2, value.vehicleId());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertStages(Connection connection, List<ProcessingChain> chains) throws SQLException {
        String sql = """
                INSERT INTO processing_stage(
                    id,chain_id,stage_order,stage_key,stage_name,description,poi_id,input_goods_id,
                    input_goods_sku,input_weight_ratio,output_goods_id,output_goods_sku,
                    output_weight_ratio,processing_time_minutes,min_batch_size,max_capacity_per_cycle
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (ProcessingChain chain : chains) {
                for (ProcessingStage stage : chain.stages()) {
                    int index = 1;
                    statement.setLong(index++, stage.id());
                    statement.setLong(index++, chain.id());
                    statement.setInt(index++, stage.stageOrder());
                    statement.setString(index++, stage.stageKey());
                    statement.setString(index++, stage.stageName());
                    statement.setString(index++, stage.description());
                    statement.setLong(index++, stage.processingPoiId());
                    setNullableLong(statement, index++, stage.legacyInputGoodsId());
                    statement.setString(index++, stage.legacyInputGoodsSku());
                    setNullableDecimal(statement, index++, stage.legacyInputWeightRatio());
                    setNullableLong(statement, index++, stage.outputGoodsId());
                    statement.setString(index++, stage.outputGoodsSku());
                    setNullableDecimal(statement, index++, stage.outputWeightRatio());
                    statement.setInt(index++, stage.processingTimeMinutes());
                    setNullableDecimal(statement, index++, stage.minBatchSize());
                    setNullableDecimal(statement, index, stage.maxCapacityPerCycle());
                    statement.addBatch();
                }
            }
            statement.executeBatch();
        }
    }

    private void insertInputs(Connection connection, List<ProcessingChain> chains, Timestamp fixedTimestamp)
            throws SQLException {
        String sql = """
                INSERT INTO processing_stage_input(
                    id,created_at,input_key,input_share,sku,updated_at,goods_id,stage_id
                ) VALUES (?,?,?,?,?,?,?,?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (ProcessingChain chain : chains) {
                for (ProcessingStage stage : chain.stages()) {
                    for (ProcessingInput input : stage.inputs()) {
                        statement.setLong(1, input.id());
                        statement.setTimestamp(2, fixedTimestamp);
                        statement.setString(3, input.inputKey());
                        statement.setBigDecimal(4, input.inputShare());
                        statement.setString(5, input.sku());
                        statement.setTimestamp(6, fixedTimestamp);
                        setNullableLong(statement, 7, input.goodsId());
                        statement.setLong(8, stage.id());
                        statement.addBatch();
                    }
                }
            }
            statement.executeBatch();
        }
    }

    private void insertEdges(Connection connection, List<ProcessingChain> chains, Timestamp fixedTimestamp)
            throws SQLException {
        String sql = """
                INSERT INTO processing_stage_edge(
                    id,created_at,chain_id,from_stage_id,to_stage_id,to_stage_input_id
                ) VALUES (?,?,?,?,?,?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (ProcessingChain chain : chains) {
                for (ProcessingEdge edge : chain.edges()) {
                    statement.setLong(1, edge.id());
                    statement.setTimestamp(2, fixedTimestamp);
                    statement.setLong(3, chain.id());
                    statement.setLong(4, edge.fromStageId());
                    statement.setLong(5, edge.toStageId());
                    statement.setLong(6, edge.toStageInputId());
                    statement.addBatch();
                }
            }
            statement.executeBatch();
        }
    }

    private void insertInventories(Connection connection, List<InitialInventory> values, Timestamp fixedTimestamp)
            throws SQLException {
        String sql = """
                INSERT INTO enrollment(created_at,quantity,goods_id,poi_id,version)
                VALUES (?,?,?,?,0)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (InitialInventory value : values) {
                statement.setTimestamp(1, fixedTimestamp);
                statement.setInt(2, value.quantity());
                statement.setLong(3, value.goodsId());
                statement.setLong(4, value.poiId());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void setDeterministicAutoIncrements(Connection connection) throws SQLException {
        for (String table : List.of(
                "poi", "goods", "vehicle", "processing_chain", "processing_stage",
                "processing_stage_input", "processing_stage_edge", "enrollment", "driver")) {
            long next;
            try (Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery("SELECT COALESCE(MAX(id),0)+1 FROM `" + table + "`")) {
                rows.next();
                next = rows.getLong(1);
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE `" + table + "` AUTO_INCREMENT = " + next);
            }
        }
    }

    private List<Poi> readPois(Connection connection) throws SQLException {
        List<Poi> result = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT id,name,longitude,latitude,poi_type FROM poi ORDER BY id")) {
            while (rows.next()) {
                result.add(new Poi(rows.getLong(1), rows.getString(2), rows.getBigDecimal(3),
                        rows.getBigDecimal(4), rows.getString(5)));
            }
        }
        return result;
    }

    private List<Goods> readGoods(Connection connection) throws SQLException {
        List<Goods> result = new ArrayList<>();
        String sql = """
                SELECT id,sku,name,category,description,weight_per_unit,volume_per_unit,
                       require_temp,hazmat_level,shelf_life_days,vehicle_fit
                FROM goods ORDER BY id
                """;
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                result.add(new Goods(
                        rows.getLong(1), rows.getString(2), rows.getString(3), rows.getString(4),
                        rows.getString(5), rows.getBigDecimal(6), rows.getBigDecimal(7),
                        nullableBoolean(rows, 8), rows.getString(9), nullableInteger(rows, 10), rows.getString(11)));
            }
        }
        return result;
    }

    private List<Vehicle> readVehicles(Connection connection) throws SQLException {
        List<Vehicle> result = new ArrayList<>();
        String sql = """
                SELECT id,license_plate,max_load_capacity,cargo_volume,brand,model_type,vehicle_type,
                       has_temp_control,hazmat_qualification,special_vehicle_type,length,width,height,
                       `suitable-goods`
                FROM vehicle ORDER BY id
                """;
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                result.add(new Vehicle(
                        rows.getLong(1), rows.getString(2), rows.getBigDecimal(3), rows.getBigDecimal(4),
                        rows.getString(5), rows.getString(6), rows.getString(7), nullableBoolean(rows, 8),
                        rows.getString(9), rows.getString(10), rows.getBigDecimal(11), rows.getBigDecimal(12),
                        rows.getBigDecimal(13), rows.getString(14)));
            }
        }
        return result;
    }

    private List<Driver> readDrivers(Connection connection) throws SQLException {
        List<Driver> result = new ArrayList<>();
        String sql = """
                SELECT id,driver_name,driver_phone,pref_cargo,pref_max_distance_km,pref_max_weight_tons
                FROM driver ORDER BY id
                """;
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                result.add(new Driver(
                        rows.getLong(1), rows.getString(2), rows.getString(3), rows.getString(4),
                        rows.getBigDecimal(5), rows.getBigDecimal(6)));
            }
        }
        return result;
    }

    private List<DriverVehicleBinding> readDriverVehicleBindings(Connection connection) throws SQLException {
        List<DriverVehicleBinding> result = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT driver_id,vehicle_id FROM driver_vehicle ORDER BY vehicle_id,driver_id")) {
            while (rows.next()) {
                result.add(new DriverVehicleBinding(rows.getLong(1), rows.getLong(2)));
            }
        }
        return result;
    }

    private List<ProcessingChain> readChains(Connection connection) throws SQLException {
        List<ProcessingChain> result = new ArrayList<>();
        String sql = "SELECT id,chain_code,chain_name,status,description FROM processing_chain ORDER BY id";
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                long chainId = rows.getLong(1);
                result.add(new ProcessingChain(
                        chainId, rows.getString(2), rows.getString(3), rows.getString(4), rows.getString(5),
                        readStages(connection, chainId), readEdges(connection, chainId)));
            }
        }
        return result;
    }

    private List<ProcessingStage> readStages(Connection connection, long chainId) throws SQLException {
        List<ProcessingStage> result = new ArrayList<>();
        String sql = """
                SELECT s.id,s.stage_order,s.stage_key,s.stage_name,s.description,s.poi_id,p.poi_type,
                       s.input_goods_id,s.input_goods_sku,s.input_weight_ratio,s.output_goods_id,
                       s.output_goods_sku,s.output_weight_ratio,s.processing_time_minutes,
                       s.min_batch_size,s.max_capacity_per_cycle
                FROM processing_stage s JOIN poi p ON p.id=s.poi_id
                WHERE s.chain_id=? ORDER BY s.stage_order,s.id
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, chainId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    long stageId = rows.getLong(1);
                    result.add(new ProcessingStage(
                            stageId, rows.getInt(2), rows.getString(3), rows.getString(4), rows.getString(5),
                            rows.getLong(6), rows.getString(7), nullableLong(rows, 8), rows.getString(9),
                            rows.getBigDecimal(10), nullableLong(rows, 11), rows.getString(12),
                            rows.getBigDecimal(13), rows.getInt(14), rows.getBigDecimal(15),
                            rows.getBigDecimal(16), readInputs(connection, stageId)));
                }
            }
        }
        return result;
    }

    private List<ProcessingInput> readInputs(Connection connection, long stageId) throws SQLException {
        List<ProcessingInput> result = new ArrayList<>();
        String sql = """
                SELECT id,input_key,goods_id,sku,input_share FROM processing_stage_input
                WHERE stage_id=? ORDER BY input_key,id
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, stageId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new ProcessingInput(
                            rows.getLong(1), rows.getString(2), nullableLong(rows, 3),
                            rows.getString(4), rows.getBigDecimal(5)));
                }
            }
        }
        return result;
    }

    private List<ProcessingEdge> readEdges(Connection connection, long chainId) throws SQLException {
        List<ProcessingEdge> result = new ArrayList<>();
        String sql = """
                SELECT id,from_stage_id,to_stage_id,to_stage_input_id FROM processing_stage_edge
                WHERE chain_id=? ORDER BY from_stage_id,to_stage_id,to_stage_input_id,id
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, chainId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new ProcessingEdge(
                            rows.getLong(1), rows.getLong(2), rows.getLong(3), rows.getLong(4)));
                }
            }
        }
        return result;
    }

    private List<InitialInventory> readInventories(Connection connection) throws SQLException {
        List<InitialInventory> result = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT poi_id,goods_id,quantity FROM enrollment ORDER BY poi_id,goods_id")) {
            while (rows.next()) {
                result.add(new InitialInventory(rows.getLong(1), rows.getLong(2), rows.getInt(3)));
            }
        }
        return result;
    }

    private void setNullableBoolean(PreparedStatement statement, int index, Boolean value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.BIT);
        } else {
            statement.setBoolean(index, value);
        }
    }

    private void setNullableInteger(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    private void setNullableDecimal(PreparedStatement statement, int index, BigDecimal value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.DOUBLE);
        } else {
            statement.setBigDecimal(index, value);
        }
    }

    private Boolean nullableBoolean(ResultSet rows, int index) throws SQLException {
        boolean value = rows.getBoolean(index);
        return rows.wasNull() ? null : value;
    }

    private Integer nullableInteger(ResultSet rows, int index) throws SQLException {
        int value = rows.getInt(index);
        return rows.wasNull() ? null : value;
    }

    private Long nullableLong(ResultSet rows, int index) throws SQLException {
        long value = rows.getLong(index);
        return rows.wasNull() ? null : value;
    }
}
