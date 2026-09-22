-- ============================================================
-- Existing four processing chains -> DAG-compatible definitions
-- ============================================================
-- Scope:
--   * update only the 15 existing processing_stage rows belonging to the
--     four legacy chain codes below;
--   * insert one named input for each stage (15 rows);
--   * insert one edge between each adjacent stage (11 rows);
--   * do not insert/delete processing_chain or processing_stage rows;
--   * do not touch shipment, assignment, vehicle, evaluation or runtime
--     production tables.
--
-- Preconditions for the current vehicle_scheduler baseline:
--   * the four chain codes already exist exactly once;
--   * their stage counts are 4, 4, 4 and 3;
--   * processing_stage_input and processing_stage_edge contain no rows for
--     these stages.
--
-- The input and edge inserts are duplicate-safe. The stage UPDATE is an
-- intentional one-time normalization and refreshes updated_at, so do not
-- treat repeated execution as a bit-for-bit no-op after later manual edits.

SET NAMES utf8mb4;

START TRANSACTION;

DROP TEMPORARY TABLE IF EXISTS dag_stage_compat_map;
CREATE TEMPORARY TABLE dag_stage_compat_map (
    -- The legacy baseline uses utf8mb4_general_ci even on MySQL 8.4, whose
    -- database default may be utf8mb4_0900_ai_ci. Match joined columns here.
    chain_code VARCHAR(50) COLLATE utf8mb4_general_ci NOT NULL,
    stage_order INT NOT NULL,
    stage_key VARCHAR(50) COLLATE utf8mb4_general_ci NOT NULL,
    input_sku VARCHAR(100) COLLATE utf8mb4_general_ci NOT NULL,
    output_sku VARCHAR(100) COLLATE utf8mb4_general_ci NOT NULL,
    PRIMARY KEY (chain_code, stage_order),
    UNIQUE KEY uk_dag_stage_compat_key (chain_code, stage_key)
);

INSERT INTO dag_stage_compat_map
    (chain_code, stage_order, stage_key, input_sku, output_sku)
VALUES
    ('CHAIN_WOOD_FURNITURE_001', 1, 'timber_yard',        'LOG',           'LOG'),
    ('CHAIN_WOOD_FURNITURE_001', 2, 'sawmill',            'LOG',           'PLANK'),
    ('CHAIN_WOOD_FURNITURE_001', 3, 'board_factory',      'PLANK',         'PANEL'),
    ('CHAIN_WOOD_FURNITURE_001', 4, 'furniture_factory',  'PANEL',         'FURNITURE'),

    ('CHAIN_METAL_FURNITURE_002', 1, 'iron_mine',         'IRON_ORE',      'IRON_ORE'),
    ('CHAIN_METAL_FURNITURE_002', 2, 'steel_mill',        'IRON_ORE',      'STEEL_BILLET'),
    ('CHAIN_METAL_FURNITURE_002', 3, 'steel_processing',  'STEEL_BILLET',  'STEEL_PRODUCT'),
    ('CHAIN_METAL_FURNITURE_002', 4, 'furniture_factory', 'STEEL_PRODUCT', 'FURNITURE'),

    ('CHAIN_METAL_CAR_003', 1, 'iron_mine',               'IRON_ORE',      'IRON_ORE'),
    ('CHAIN_METAL_CAR_003', 2, 'steel_mill',              'IRON_ORE',      'STEEL_BILLET'),
    ('CHAIN_METAL_CAR_003', 3, 'steel_processing',        'STEEL_BILLET',  'STEEL_PRODUCT'),
    ('CHAIN_METAL_CAR_003', 4, 'auto_assembly',           'STEEL_PRODUCT', 'AUTO_PARTS'),

    ('CHAIN_RUBBER_CAR_004', 1, 'rubber_processing',      'RUBBER_RAW',    'RUBBER_SEMI'),
    ('CHAIN_RUBBER_CAR_004', 2, 'tire_manufacturing',     'RUBBER_SEMI',   'TIRE'),
    ('CHAIN_RUBBER_CAR_004', 3, 'auto_assembly',          'TIRE',          'AUTO_PARTS');

-- Reuse the existing stage identities. Normalize SKU text to the Goods row
-- when one exists, and keep final products as text-only SKUs until their
-- physical Goods parameters are explicitly defined.
UPDATE processing_stage AS ps
JOIN processing_chain AS pc
  ON pc.id = ps.chain_id
JOIN dag_stage_compat_map AS map
  ON map.chain_code = pc.chain_code
 AND map.stage_order = ps.stage_order
LEFT JOIN goods AS input_goods
  ON input_goods.sku = map.input_sku
LEFT JOIN goods AS output_goods
  ON output_goods.sku = map.output_sku
SET ps.stage_key = map.stage_key,
    ps.input_goods_id = input_goods.id,
    ps.input_goods_sku = map.input_sku,
    ps.output_goods_id = output_goods.id,
    ps.output_goods_sku = map.output_sku,
    ps.processing_time_minutes = CASE
        WHEN ps.processing_time_minutes IS NULL OR ps.processing_time_minutes <= 0 THEN 60
        ELSE ps.processing_time_minutes
    END,
    ps.updated_at = NOW();

-- Add exactly one named input to each migrated stage. A missing Goods row is
-- allowed by the schema, but all transported intermediate inputs in this
-- mapping resolve to the current Goods master data.
INSERT INTO processing_stage_input
    (stage_id, input_key, goods_id, sku, input_share, created_at, updated_at)
SELECT
    ps.id,
    'input',
    ps.input_goods_id,
    map.input_sku,
    1.0,
    NOW(),
    NOW()
FROM dag_stage_compat_map AS map
JOIN processing_chain AS pc
  ON pc.chain_code = map.chain_code
JOIN processing_stage AS ps
  ON ps.chain_id = pc.id
 AND ps.stage_order = map.stage_order
WHERE NOT EXISTS (
    SELECT 1
    FROM processing_stage_input AS existing_input
    WHERE existing_input.stage_id = ps.id
      AND existing_input.input_key = 'input'
);

-- Reconstruct the four legacy linear chains as explicit DAG edges. The
-- resulting model is DAG-compatible; it does not invent an unsupported
-- output split or merge that was absent from the legacy definitions.
-- MySQL cannot join the same temporary table twice in one statement.
DROP TEMPORARY TABLE IF EXISTS dag_stage_compat_map_to;
CREATE TEMPORARY TABLE dag_stage_compat_map_to LIKE dag_stage_compat_map;
INSERT INTO dag_stage_compat_map_to
    SELECT * FROM dag_stage_compat_map;

INSERT INTO processing_stage_edge
    (chain_id, from_stage_id, to_stage_id, to_stage_input_id, created_at)
SELECT
    pc.id,
    from_stage.id,
    to_stage.id,
    to_input.id,
    NOW()
FROM dag_stage_compat_map AS from_map
JOIN dag_stage_compat_map_to AS to_map
  ON to_map.chain_code = from_map.chain_code
 AND to_map.stage_order = from_map.stage_order + 1
JOIN processing_chain AS pc
  ON pc.chain_code = from_map.chain_code
JOIN processing_stage AS from_stage
  ON from_stage.chain_id = pc.id
 AND from_stage.stage_order = from_map.stage_order
JOIN processing_stage AS to_stage
  ON to_stage.chain_id = pc.id
 AND to_stage.stage_order = to_map.stage_order
JOIN processing_stage_input AS to_input
  ON to_input.stage_id = to_stage.id
 AND to_input.input_key = 'input'
WHERE NOT EXISTS (
    SELECT 1
    FROM processing_stage_edge AS existing_edge
    WHERE existing_edge.chain_id = pc.id
      AND existing_edge.from_stage_id = from_stage.id
      AND existing_edge.to_stage_id = to_stage.id
      AND existing_edge.to_stage_input_id = to_input.id
);

DROP TEMPORARY TABLE IF EXISTS dag_stage_compat_map;
DROP TEMPORARY TABLE IF EXISTS dag_stage_compat_map_to;

COMMIT;
