-- ============================================================
-- Legacy processing-chain data -> Y-shape DAG seed data
-- Source: C:/Users/marsh/Downloads/processing_chain.json
-- ============================================================
-- Prerequisite:
--   Run create_y_shape_dag_tables.sql first.
--
-- Notes:
-- 1. The legacy JSON only contains chain-level records. Stage rows are
--    reconstructed from each chain description.
-- 2. Legacy input_materials values are normalized to existing goods SKUs:
--      WOOD   -> TIMBER
--      METAL  -> IRON_ORE / STEEL
--      RUBBER -> RUBBER
-- 3. Missing numeric fields use safe defaults:
--      output_weight_ratio = 1.0
--      processing_time_minutes = 60
-- 4. Legacy auto-increment IDs are not preserved. chain_code is used as the
--    stable business key, which is safer when the target database already has
--    processing-chain rows.

SET NAMES utf8mb4;

START TRANSACTION;

-- ------------------------------------------------------------
-- 1. Legacy chain headers
-- ------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS legacy_processing_chain;
CREATE TEMPORARY TABLE legacy_processing_chain (
    chain_code VARCHAR(50) NOT NULL,
    chain_name VARCHAR(100) NOT NULL,
    description VARCHAR(500)
);

INSERT INTO legacy_processing_chain
    (chain_code, chain_name, description)
VALUES
    ('CHAIN_WOOD_FURNITURE_001', '木材→家具加工链', '原木厂→锯木厂→板材厂→家具制造厂'),
    ('CHAIN_METAL_FURNITURE_002', '金属→家具加工链', '矿山/铁矿厂→冶钢厂→钢材加工厂→家具制造厂'),
    ('CHAIN_METAL_CAR_003', '金属→汽车加工链', '矿山/铁矿厂→冶钢厂→钢材加工厂→汽车总装厂'),
    ('CHAIN_RUBBER_CAR_004', '橡胶→汽车加工链', '橡胶加工厂→轮胎厂→汽车总装厂');

-- ------------------------------------------------------------
-- 2. Reconstructed stage definitions
-- ------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS legacy_processing_stage;
CREATE TEMPORARY TABLE legacy_processing_stage (
    chain_code VARCHAR(50) NOT NULL,
    stage_order INT NOT NULL,
    stage_key VARCHAR(50) NOT NULL,
    stage_name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    poi_type VARCHAR(30) NOT NULL,
    input_sku VARCHAR(100) NOT NULL,
    output_sku VARCHAR(100) NOT NULL,
    output_weight_ratio DOUBLE NOT NULL,
    processing_time_minutes INT NOT NULL
);

INSERT INTO legacy_processing_stage
    (chain_code, stage_order, stage_key, stage_name, description, poi_type,
     input_sku, output_sku, output_weight_ratio, processing_time_minutes)
VALUES
    -- 木材 → 家具
    ('CHAIN_WOOD_FURNITURE_001', 1, 'timber_yard', '原木厂', '原木收储与预处理', 'TIMBER_YARD', 'TIMBER', 'TIMBER', 1.0, 60),
    ('CHAIN_WOOD_FURNITURE_001', 2, 'sawmill', '锯木厂', '原木锯切加工', 'SAWMILL', 'TIMBER', 'BOARD', 1.0, 60),
    ('CHAIN_WOOD_FURNITURE_001', 3, 'board_factory', '板材厂', '板材精加工', 'BOARD_FACTORY', 'BOARD', 'BOARD', 1.0, 60),
    ('CHAIN_WOOD_FURNITURE_001', 4, 'furniture_factory', '家具制造厂', '家具制造', 'FURNITURE_FACTORY', 'BOARD', 'FURNITURE', 1.0, 60),

    -- 金属 → 家具
    ('CHAIN_METAL_FURNITURE_002', 1, 'iron_mine', '铁矿厂', '铁矿石开采与预处理', 'IRON_MINE', 'IRON_ORE', 'IRON_ORE', 1.0, 60),
    ('CHAIN_METAL_FURNITURE_002', 2, 'steel_mill', '冶钢厂', '铁矿石冶炼', 'STEEL_MILL', 'IRON_ORE', 'STEEL', 1.0, 60),
    ('CHAIN_METAL_FURNITURE_002', 3, 'steel_processing', '钢材加工厂', '钢材加工', 'STEEL_PROCESSING_PLANT', 'STEEL', 'STEEL', 1.0, 60),
    ('CHAIN_METAL_FURNITURE_002', 4, 'furniture_factory', '家具制造厂', '金属家具制造', 'FURNITURE_FACTORY', 'STEEL', 'FURNITURE', 1.0, 60),

    -- 金属 → 汽车
    ('CHAIN_METAL_CAR_003', 1, 'iron_mine', '铁矿厂', '铁矿石开采与预处理', 'IRON_MINE', 'IRON_ORE', 'IRON_ORE', 1.0, 60),
    ('CHAIN_METAL_CAR_003', 2, 'steel_mill', '冶钢厂', '铁矿石冶炼', 'STEEL_MILL', 'IRON_ORE', 'STEEL', 1.0, 60),
    ('CHAIN_METAL_CAR_003', 3, 'steel_processing', '钢材加工厂', '钢材加工', 'STEEL_PROCESSING_PLANT', 'STEEL', 'STEEL', 1.0, 60),
    ('CHAIN_METAL_CAR_003', 4, 'auto_assembly', '汽车总装厂', '汽车总装', 'AUTO_ASSEMBLY_PLANT', 'STEEL', 'AUTO_PARTS', 1.0, 60),

    -- 橡胶 → 汽车
    ('CHAIN_RUBBER_CAR_004', 1, 'rubber_processing', '橡胶加工厂', '橡胶原料加工', 'RUBBER_PROCESSING_PLANT', 'RUBBER', 'RUBBER', 1.0, 60),
    ('CHAIN_RUBBER_CAR_004', 2, 'tire_manufacturing', '轮胎厂', '轮胎制造', 'TIRE_MANUFACTURING_PLANT', 'RUBBER', 'TIRE', 1.0, 60),
    ('CHAIN_RUBBER_CAR_004', 3, 'auto_assembly', '汽车总装厂', '汽车总装', 'AUTO_ASSEMBLY_PLANT', 'TIRE', 'AUTO_PARTS', 1.0, 60);

-- ------------------------------------------------------------
-- 3. Insert processing chains
-- ------------------------------------------------------------
INSERT INTO processing_chain
    (chain_code, chain_name, status, description, created_at, updated_at)
SELECT
    lc.chain_code,
    lc.chain_name,
    'ACTIVE',
    lc.description,
    NOW(),
    NOW()
FROM legacy_processing_chain AS lc
WHERE NOT EXISTS (
    SELECT 1
    FROM processing_chain AS pc
    WHERE pc.chain_code = lc.chain_code
);

-- ------------------------------------------------------------
-- 4. Insert processing stages
-- ------------------------------------------------------------
INSERT INTO processing_stage
    (chain_id, stage_order, stage_key, stage_name, description, poi_id,
     input_goods_id, input_goods_sku, input_weight_ratio,
     output_goods_id, output_goods_sku, output_weight_ratio,
     processing_time_minutes, created_at, updated_at)
SELECT
    pc.id,
    ls.stage_order,
    ls.stage_key,
    ls.stage_name,
    ls.description,
    poi.id,
    gin.id,
    ls.input_sku,
    1.0,
    gout.id,
    ls.output_sku,
    ls.output_weight_ratio,
    ls.processing_time_minutes,
    NOW(),
    NOW()
FROM legacy_processing_stage AS ls
JOIN processing_chain AS pc
  ON pc.chain_code = ls.chain_code
JOIN (
    SELECT poi_type, MIN(id) AS id
    FROM poi
    GROUP BY poi_type
) AS poi
  ON poi.poi_type = ls.poi_type
LEFT JOIN goods AS gin
  ON gin.sku = ls.input_sku
LEFT JOIN goods AS gout
  ON gout.sku = ls.output_sku
WHERE NOT EXISTS (
    SELECT 1
    FROM processing_stage AS ps
    WHERE ps.chain_id = pc.id
      AND ps.stage_key = ls.stage_key
);

-- ------------------------------------------------------------
-- 5. Insert named stage inputs
-- ------------------------------------------------------------
INSERT INTO processing_stage_input
    (stage_id, input_key, goods_id, sku, input_share, created_at, updated_at)
SELECT
    ps.id,
    'input',
    g.id,
    ls.input_sku,
    1.0,
    NOW(),
    NOW()
FROM legacy_processing_stage AS ls
JOIN processing_chain AS pc
  ON pc.chain_code = ls.chain_code
JOIN processing_stage AS ps
  ON ps.chain_id = pc.id
 AND ps.stage_key = ls.stage_key
LEFT JOIN goods AS g
  ON g.sku = ls.input_sku
WHERE NOT EXISTS (
    SELECT 1
    FROM processing_stage_input AS psi
    WHERE psi.stage_id = ps.id
      AND psi.input_key = 'input'
);

-- ------------------------------------------------------------
-- 6. Insert graph edges
-- ------------------------------------------------------------
INSERT INTO processing_stage_edge
    (chain_id, from_stage_id, to_stage_id, to_stage_input_id, created_at)
SELECT
    pc.id,
    ps_from.id,
    ps_to.id,
    psi_to.id,
    NOW()
FROM legacy_processing_stage AS ls_from
JOIN legacy_processing_stage AS ls_to
  ON ls_to.chain_code = ls_from.chain_code
 AND ls_to.stage_order = ls_from.stage_order + 1
JOIN processing_chain AS pc
  ON pc.chain_code = ls_from.chain_code
JOIN processing_stage AS ps_from
  ON ps_from.chain_id = pc.id
 AND ps_from.stage_key = ls_from.stage_key
JOIN processing_stage AS ps_to
  ON ps_to.chain_id = pc.id
 AND ps_to.stage_key = ls_to.stage_key
JOIN processing_stage_input AS psi_to
  ON psi_to.stage_id = ps_to.id
 AND psi_to.input_key = 'input'
WHERE NOT EXISTS (
    SELECT 1
    FROM processing_stage_edge AS pse
    WHERE pse.chain_id = pc.id
      AND pse.from_stage_id = ps_from.id
      AND pse.to_stage_id = ps_to.id
      AND pse.to_stage_input_id = psi_to.id
);

-- ------------------------------------------------------------
-- 7. Cleanup
-- ------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS legacy_processing_stage;
DROP TEMPORARY TABLE IF EXISTS legacy_processing_chain;

COMMIT;
