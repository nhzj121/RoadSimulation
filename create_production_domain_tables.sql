-- ============================================================
-- Demand-driven production domain
-- ============================================================
-- This migration introduces the new production planning model.
-- The processing-chain definition tables remain; legacy shipment processing
-- columns are cleaned up separately by drop_legacy_processing_execution.sql.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS production_plan (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    plan_no VARCHAR(50) NOT NULL,
    chain_id BIGINT NOT NULL,
    final_sku VARCHAR(100),
    final_demand_weight DOUBLE PRECISION NOT NULL,
    source_poi_id BIGINT,
    status VARCHAR(20) NOT NULL,
    random_seed BIGINT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT uk_production_plan_no UNIQUE (plan_no),
    CONSTRAINT fk_production_plan_chain FOREIGN KEY (chain_id) REFERENCES processing_chain(id),
    CONSTRAINT fk_production_plan_source_poi FOREIGN KEY (source_poi_id) REFERENCES poi(id),
    INDEX idx_production_plan_chain (chain_id),
    INDEX idx_production_plan_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS production_plan_node (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    plan_id BIGINT NOT NULL,
    stage_id BIGINT NOT NULL,
    stage_order INT NOT NULL,
    input_sku VARCHAR(100),
    output_sku VARCHAR(100),
    planned_input_weight DOUBLE PRECISION NOT NULL,
    planned_output_weight DOUBLE PRECISION NOT NULL,
    status VARCHAR(25) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT uk_production_plan_node_stage UNIQUE (plan_id, stage_order),
    CONSTRAINT fk_production_plan_node_plan FOREIGN KEY (plan_id) REFERENCES production_plan(id),
    CONSTRAINT fk_production_plan_node_stage FOREIGN KEY (stage_id) REFERENCES processing_stage(id),
    INDEX idx_production_plan_node_plan (plan_id),
    INDEX idx_production_plan_node_stage (stage_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS production_batch (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_no VARCHAR(50) NOT NULL,
    plan_id BIGINT NOT NULL,
    chain_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    planned_final_output_weight DOUBLE PRECISION,
    actual_final_output_weight DOUBLE PRECISION,
    started_at DATETIME,
    completed_at DATETIME,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT uk_production_batch_no UNIQUE (batch_no),
    CONSTRAINT fk_production_batch_plan FOREIGN KEY (plan_id) REFERENCES production_plan(id),
    CONSTRAINT fk_production_batch_chain FOREIGN KEY (chain_id) REFERENCES processing_chain(id),
    INDEX idx_production_batch_plan (plan_id),
    INDEX idx_production_batch_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS processing_stage_execution (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    plan_node_id BIGINT NOT NULL,
    stage_id BIGINT NOT NULL,
    stage_order INT NOT NULL,
    processing_poi_id BIGINT NOT NULL,
    status VARCHAR(25) NOT NULL,
    actual_input_weight DOUBLE PRECISION,
    actual_output_weight DOUBLE PRECISION,
    progress_percent INT,
    started_at DATETIME,
    completed_at DATETIME,
    inbound_shipment_id BIGINT,
    outbound_shipment_id BIGINT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT uk_processing_stage_execution UNIQUE (batch_id, stage_order),
    CONSTRAINT fk_stage_execution_batch FOREIGN KEY (batch_id) REFERENCES production_batch(id),
    CONSTRAINT fk_stage_execution_plan_node FOREIGN KEY (plan_node_id) REFERENCES production_plan_node(id),
    CONSTRAINT fk_stage_execution_stage FOREIGN KEY (stage_id) REFERENCES processing_stage(id),
    CONSTRAINT fk_stage_execution_poi FOREIGN KEY (processing_poi_id) REFERENCES poi(id),
    CONSTRAINT fk_stage_execution_inbound FOREIGN KEY (inbound_shipment_id) REFERENCES shipment(id),
    CONSTRAINT fk_stage_execution_outbound FOREIGN KEY (outbound_shipment_id) REFERENCES shipment(id),
    INDEX idx_stage_execution_batch (batch_id),
    INDEX idx_stage_execution_stage (stage_id),
    INDEX idx_stage_execution_status (status),
    INDEX idx_stage_execution_inbound (inbound_shipment_id),
    INDEX idx_stage_execution_outbound (outbound_shipment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Y-shape DAG processing-chain extension
-- ============================================================
-- For an existing database, create_y_shape_dag_tables.sql is the
-- standalone idempotent migration. The definitions below keep this
-- consolidated installation script aligned with the JPA model.

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
