-- ============================================================
-- Y-shape processing-chain graph migration
-- ============================================================
-- Adds stage keys, named stage inputs, graph edges, and runtime
-- material flows. This script is idempotent for MySQL 8.

SET NAMES utf8mb4;

-- Add processing_stage.stage_key when it does not exist.
SET @stage_key_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'processing_stage'
      AND COLUMN_NAME = 'stage_key'
);
SET @stage_key_ddl := IF(
    @stage_key_exists = 0,
    'ALTER TABLE processing_stage ADD COLUMN stage_key VARCHAR(50) NULL AFTER stage_name',
    'SELECT 1'
);
PREPARE stage_key_stmt FROM @stage_key_ddl;
EXECUTE stage_key_stmt;
DEALLOCATE PREPARE stage_key_stmt;

-- Enforce one non-null stage key per chain. MySQL permits multiple NULL values.
SET @stage_key_index_exists := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'processing_stage'
      AND INDEX_NAME = 'uk_processing_stage_key'
);
SET @stage_key_index_ddl := IF(
    @stage_key_index_exists = 0,
    'ALTER TABLE processing_stage ADD UNIQUE KEY uk_processing_stage_key (chain_id, stage_key)',
    'SELECT 1'
);
PREPARE stage_key_index_stmt FROM @stage_key_index_ddl;
EXECUTE stage_key_index_stmt;
DEALLOCATE PREPARE stage_key_index_stmt;

CREATE TABLE IF NOT EXISTS processing_stage_input (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    stage_id BIGINT NOT NULL,
    input_key VARCHAR(50) NOT NULL,
    goods_id BIGINT NULL,
    sku VARCHAR(100) NOT NULL,
    input_share DOUBLE PRECISION NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT uk_processing_stage_input UNIQUE (stage_id, input_key),
    CONSTRAINT fk_processing_stage_input_stage FOREIGN KEY (stage_id) REFERENCES processing_stage(id),
    CONSTRAINT fk_processing_stage_input_goods FOREIGN KEY (goods_id) REFERENCES goods(id),
    INDEX idx_processing_stage_input_stage (stage_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS processing_stage_edge (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    chain_id BIGINT NOT NULL,
    from_stage_id BIGINT NOT NULL,
    to_stage_id BIGINT NOT NULL,
    to_stage_input_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT uk_processing_stage_edge_input UNIQUE (to_stage_input_id),
    CONSTRAINT uk_processing_stage_edge UNIQUE (from_stage_id, to_stage_id, to_stage_input_id),
    CONSTRAINT fk_processing_stage_edge_chain FOREIGN KEY (chain_id) REFERENCES processing_chain(id),
    CONSTRAINT fk_processing_stage_edge_from FOREIGN KEY (from_stage_id) REFERENCES processing_stage(id),
    CONSTRAINT fk_processing_stage_edge_to FOREIGN KEY (to_stage_id) REFERENCES processing_stage(id),
    CONSTRAINT fk_processing_stage_edge_input FOREIGN KEY (to_stage_input_id) REFERENCES processing_stage_input(id),
    INDEX idx_processing_stage_edge_chain (chain_id),
    INDEX idx_processing_stage_edge_from (from_stage_id),
    INDEX idx_processing_stage_edge_to (to_stage_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS production_plan_flow (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    plan_id BIGINT NOT NULL,
    from_node_id BIGINT NULL,
    to_node_id BIGINT NOT NULL,
    stage_input_id BIGINT NULL,
    input_key VARCHAR(50) NOT NULL,
    sku VARCHAR(100) NOT NULL,
    planned_weight DOUBLE PRECISION NOT NULL,
    source_poi_id BIGINT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT uk_production_plan_flow UNIQUE (plan_id, to_node_id, input_key),
    CONSTRAINT fk_production_plan_flow_plan FOREIGN KEY (plan_id) REFERENCES production_plan(id),
    CONSTRAINT fk_production_plan_flow_from FOREIGN KEY (from_node_id) REFERENCES production_plan_node(id),
    CONSTRAINT fk_production_plan_flow_to FOREIGN KEY (to_node_id) REFERENCES production_plan_node(id),
    CONSTRAINT fk_production_plan_flow_input FOREIGN KEY (stage_input_id) REFERENCES processing_stage_input(id),
    CONSTRAINT fk_production_plan_flow_source FOREIGN KEY (source_poi_id) REFERENCES poi(id),
    INDEX idx_production_plan_flow_plan (plan_id),
    INDEX idx_production_plan_flow_from (from_node_id),
    INDEX idx_production_plan_flow_to (to_node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS processing_execution_flow (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    plan_flow_id BIGINT NOT NULL,
    from_execution_id BIGINT NULL,
    to_execution_id BIGINT NOT NULL,
    input_key VARCHAR(50) NOT NULL,
    sku VARCHAR(100) NOT NULL,
    planned_weight DOUBLE PRECISION NOT NULL,
    actual_weight DOUBLE PRECISION NULL,
    shipment_id BIGINT NULL,
    status VARCHAR(30) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT uk_processing_execution_flow UNIQUE (batch_id, to_execution_id, input_key),
    CONSTRAINT fk_processing_execution_flow_batch FOREIGN KEY (batch_id) REFERENCES production_batch(id),
    CONSTRAINT fk_processing_execution_flow_plan FOREIGN KEY (plan_flow_id) REFERENCES production_plan_flow(id),
    CONSTRAINT fk_processing_execution_flow_from FOREIGN KEY (from_execution_id) REFERENCES processing_stage_execution(id),
    CONSTRAINT fk_processing_execution_flow_to FOREIGN KEY (to_execution_id) REFERENCES processing_stage_execution(id),
    CONSTRAINT fk_processing_execution_flow_shipment FOREIGN KEY (shipment_id) REFERENCES shipment(id),
    INDEX idx_processing_execution_flow_batch (batch_id),
    INDEX idx_processing_execution_flow_from (from_execution_id),
    INDEX idx_processing_execution_flow_to (to_execution_id),
    INDEX idx_processing_execution_flow_shipment (shipment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
