-- ============================================================
-- Demand-driven production domain
-- ============================================================
-- This migration introduces the new production planning model.
-- Existing processing-chain tables remain untouched for backward compatibility.

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
