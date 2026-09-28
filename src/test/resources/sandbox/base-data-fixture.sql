INSERT INTO poi (id, name, longitude, latitude, poi_type) VALUES
    (30, 'P30', 116.300000, 39.900000, 'WAREHOUSE'),
    (10, 'P10', 116.100000, 39.700000, 'DISTRIBUTION_CENTER');

INSERT INTO goods (id, name, sku, weight_per_unit, volume_per_unit) VALUES
    (11, 'Goods 11', 'G11', 2.5, 3.0),
    (7, 'Goods 07', 'G07', 1.5, 2.0);

INSERT INTO processing_chain (id, chain_code, chain_name, status, yield_rate, merge_stage_id) VALUES
    (100, 'C100', 'Merge chain', 'ACTIVE', 0.90, 1005),
    (90, 'C090', 'Upstream chain B', 'ACTIVE', 0.95, NULL),
    (80, 'C080', 'Upstream chain A', 'ACTIVE', 0.96, NULL);

INSERT INTO processing_chain_predecessors (chain_id, predecessor_chain_id) VALUES
    (100, 90),
    (100, 80);

INSERT INTO processing_stage (
    id, chain_id, stage_order, stage_name, poi_id,
    input_goods_id, input_goods_sku, input_weight_ratio,
    output_goods_id, output_goods_sku, output_weight_ratio,
    processing_time_minutes
) VALUES
    (1005, 100, 2, 'Merge stage 2', 30, 11, 'G11', 1.0, NULL, NULL, 0.90, 60),
    (1000, 100, 1, 'Merge stage 1', 10, 7, 'G07', 1.0, 11, 'G11', 0.95, 45),
    (905, 90, 1, 'Upstream B', 30, 7, 'G07', 1.0, 11, 'G11', 0.96, 30),
    (805, 80, 1, 'Upstream A', 10, 7, 'G07', 1.0, 11, 'G11', 0.97, 30);

INSERT INTO vehicle (
    id, license_plate, current_status, current_poi_id,
    current_load, max_load_capacity, cargo_volume
) VALUES
    (88, 'V-088', 'IDLE', 30, 0.0, 35.0, 80.0),
    (42, 'V-042', 'IDLE', 10, 0.0, 20.0, 45.0);

INSERT INTO enrollment (id, quantity, goods_id, poi_id, version) VALUES
    (202, 80, 11, 30, 0),
    (201, 100, 7, 10, 0);

INSERT INTO vehicle_goods_match (id, goods_id, vehicle_id, match_status, match_score) VALUES
    (401, 7, 42, 'CONFIRMED', 98.50);
