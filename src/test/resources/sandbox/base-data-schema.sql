SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS vehicle_goods_match;
DROP TABLE IF EXISTS enrollment;
DROP TABLE IF EXISTS processing_chain_predecessors;
DROP TABLE IF EXISTS processing_stage;
DROP TABLE IF EXISTS vehicle;
DROP TABLE IF EXISTS processing_chain;
DROP TABLE IF EXISTS goods;
DROP TABLE IF EXISTS poi;
SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE poi (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    longitude DECIMAL(9, 6) NOT NULL,
    latitude DECIMAL(10, 6) NOT NULL,
    poi_type VARCHAR(30),
    PRIMARY KEY (id)
) ENGINE=InnoDB AUTO_INCREMENT=40 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE goods (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL,
    sku VARCHAR(100),
    weight_per_unit DOUBLE,
    volume_per_unit DOUBLE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_goods_sku (sku)
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE processing_chain (
    id BIGINT NOT NULL AUTO_INCREMENT,
    chain_code VARCHAR(50) NOT NULL,
    chain_name VARCHAR(100) NOT NULL,
    status ENUM('ACTIVE', 'INACTIVE', 'MAINTENANCE'),
    yield_rate DOUBLE,
    merge_stage_id BIGINT,
    PRIMARY KEY (id),
    UNIQUE KEY uk_processing_chain_code (chain_code)
) ENGINE=InnoDB AUTO_INCREMENT=120 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE processing_stage (
    id BIGINT NOT NULL AUTO_INCREMENT,
    chain_id BIGINT NOT NULL,
    stage_order INT NOT NULL,
    stage_name VARCHAR(100) NOT NULL,
    poi_id BIGINT NOT NULL,
    input_goods_id BIGINT,
    input_goods_sku VARCHAR(50),
    input_weight_ratio DOUBLE,
    output_goods_id BIGINT,
    output_goods_sku VARCHAR(50),
    output_weight_ratio DOUBLE,
    processing_time_minutes INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_sandbox_stage_chain FOREIGN KEY (chain_id) REFERENCES processing_chain (id),
    CONSTRAINT fk_sandbox_stage_poi FOREIGN KEY (poi_id) REFERENCES poi (id),
    CONSTRAINT fk_sandbox_stage_input_goods FOREIGN KEY (input_goods_id) REFERENCES goods (id),
    CONSTRAINT fk_sandbox_stage_output_goods FOREIGN KEY (output_goods_id) REFERENCES goods (id)
) ENGINE=InnoDB AUTO_INCREMENT=1100 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE processing_chain_predecessors (
    chain_id BIGINT NOT NULL,
    predecessor_chain_id BIGINT,
    KEY idx_sandbox_predecessor_owner (chain_id),
    CONSTRAINT fk_sandbox_predecessor_owner FOREIGN KEY (chain_id) REFERENCES processing_chain (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE vehicle (
    id BIGINT NOT NULL AUTO_INCREMENT,
    license_plate VARCHAR(25) NOT NULL,
    current_status ENUM('BREAKDOWN', 'IDLE', 'LOADING', 'ORDER_DRIVING', 'TRANSPORT_DRIVING', 'UNLOADING', 'WAITING'),
    current_poi_id BIGINT,
    current_load DOUBLE,
    max_load_capacity DOUBLE,
    cargo_volume DOUBLE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sandbox_vehicle_plate (license_plate),
    CONSTRAINT fk_sandbox_vehicle_poi FOREIGN KEY (current_poi_id) REFERENCES poi (id)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE enrollment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    quantity INT,
    goods_id BIGINT,
    poi_id BIGINT,
    version INT,
    PRIMARY KEY (id),
    UNIQUE KEY uk_poi_goods (poi_id, goods_id),
    CONSTRAINT fk_sandbox_enrollment_goods FOREIGN KEY (goods_id) REFERENCES goods (id),
    CONSTRAINT fk_sandbox_enrollment_poi FOREIGN KEY (poi_id) REFERENCES poi (id)
) ENGINE=InnoDB AUTO_INCREMENT=300 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- The production table currently has no physical foreign keys for these IDs.
-- The integration test therefore verifies these references as logical constraints.
CREATE TABLE vehicle_goods_match (
    id BIGINT NOT NULL AUTO_INCREMENT,
    goods_id BIGINT NOT NULL,
    vehicle_id BIGINT NOT NULL,
    match_status ENUM('CANCELLED', 'COMPLETED', 'CONFIRMED', 'PENDING', 'REJECTED') NOT NULL,
    match_score DECIMAL(5, 2),
    PRIMARY KEY (id)
) ENGINE=InnoDB AUTO_INCREMENT=500 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
