-- ============================================
-- 跨 POI 加工链系统数据库初始化脚本
-- 创建时间：2026-03-20
-- 说明：创建加工链、工序、订单、任务相关表
-- ============================================

-- 1. 加工链主表
CREATE TABLE IF NOT EXISTS processing_chain (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID',
    chain_code VARCHAR(50) NOT NULL UNIQUE COMMENT '加工链编码',
    chain_name VARCHAR(100) NOT NULL COMMENT '加工链名称',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE,INACTIVE,MAINTENANCE',
    description VARCHAR(500) COMMENT '描述',
    total_processing_time_minutes INT COMMENT '总加工时间 (分钟)',
    input_weight_per_cycle DECIMAL(10,2) COMMENT '单次输入重量 (吨)',
    output_weight_per_cycle DECIMAL(10,2) COMMENT '单次输出重量 (吨)',
    yield_rate DECIMAL(5,4) DEFAULT 0.95 COMMENT '综合产出率',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_chain_status (status),
    INDEX idx_chain_code (chain_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='加工链主表';

-- 2. 加工工序表
CREATE TABLE IF NOT EXISTS processing_stage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID',
    chain_id BIGINT NOT NULL COMMENT '加工链 ID',
    stage_order INT NOT NULL COMMENT '工序顺序',
    stage_name VARCHAR(100) NOT NULL COMMENT '工序名称',
    description VARCHAR(500) COMMENT '描述',
    poi_id BIGINT NOT NULL COMMENT '加工点 POI ID',
    input_goods_id BIGINT COMMENT '输入货物 ID',
    input_goods_sku VARCHAR(50) COMMENT '输入货物 SKU',
    input_weight_ratio DECIMAL(10,4) DEFAULT 1.0 COMMENT '输入重量比例',
    output_goods_id BIGINT COMMENT '输出货物 ID',
    output_goods_sku VARCHAR(50) COMMENT '输出货物 SKU',
    output_weight_ratio DECIMAL(10,4) DEFAULT 1.0 COMMENT '输出重量比例',
    processing_time_minutes INT NOT NULL DEFAULT 60 COMMENT '加工时间 (分钟)',
    max_capacity_per_cycle DECIMAL(10,2) COMMENT '单工序最大处理能力 (吨)',
    min_batch_size DECIMAL(10,2) COMMENT '最小加工批量 (吨)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (chain_id) REFERENCES processing_chain(id) ON DELETE CASCADE,
    FOREIGN KEY (poi_id) REFERENCES POI(id),
    FOREIGN KEY (input_goods_id) REFERENCES goods(id),
    FOREIGN KEY (output_goods_id) REFERENCES goods(id),
    INDEX idx_stage_chain (chain_id),
    INDEX idx_stage_poi (poi_id),
    INDEX idx_stage_order (chain_id, stage_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='加工工序表';

-- 3. 加工订单表
CREATE TABLE IF NOT EXISTS processing_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID',
    order_no VARCHAR(50) NOT NULL UNIQUE COMMENT '订单号',
    chain_id BIGINT NOT NULL COMMENT '加工链 ID',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING,IN_PROCESS,COMPLETED,CANCELLED',
    input_goods_id BIGINT COMMENT '输入货物 ID',
    input_weight DECIMAL(10,2) COMMENT '输入重量 (吨)',
    input_volume DECIMAL(10,2) COMMENT '输入体积 (m³)',
    output_goods_id BIGINT COMMENT '输出货物 ID',
    expected_output_weight DECIMAL(10,2) COMMENT '预期输出重量 (吨)',
    actual_output_weight DECIMAL(10,2) COMMENT '实际输出重量 (吨)',
    order_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
    start_time DATETIME COMMENT '开始加工时间',
    expected_finish_time DATETIME COMMENT '预期完成时间',
    actual_finish_time DATETIME COMMENT '实际完成时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (chain_id) REFERENCES processing_chain(id),
    FOREIGN KEY (input_goods_id) REFERENCES goods(id),
    FOREIGN KEY (output_goods_id) REFERENCES goods(id),
    INDEX idx_order_chain (chain_id),
    INDEX idx_order_status (status),
    INDEX idx_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='加工订单表';

-- 4. 加工任务表
CREATE TABLE IF NOT EXISTS processing_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID',
    order_id BIGINT NOT NULL COMMENT '加工订单 ID',
    stage_id BIGINT NOT NULL COMMENT '工序 ID',
    status VARCHAR(20) NOT NULL DEFAULT 'WAITING' COMMENT '状态：WAITING,PROCESSING,COMPLETED,BLOCKED,FAILED',
    progress_percent INT DEFAULT 0 COMMENT '加工进度 (0-100)',
    processed_weight DECIMAL(10,2) COMMENT '已处理重量 (吨)',
    start_time DATETIME COMMENT '开始时间',
    end_time DATETIME COMMENT '结束时间',
    inbound_assignment_id BIGINT COMMENT '原料运入任务 ID',
    outbound_assignment_id BIGINT COMMENT '成品运出任务 ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (order_id) REFERENCES processing_order(id) ON DELETE CASCADE,
    FOREIGN KEY (stage_id) REFERENCES processing_stage(id),
    FOREIGN KEY (inbound_assignment_id) REFERENCES assignment(id),
    FOREIGN KEY (outbound_assignment_id) REFERENCES assignment(id),
    INDEX idx_task_order (order_id),
    INDEX idx_task_stage (stage_id),
    INDEX idx_task_status (status),
    INDEX idx_task_inbound (inbound_assignment_id),
    INDEX idx_task_outbound (outbound_assignment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='加工任务表';

-- ============================================
-- 测试数据 - 钢铁家具加工链
-- ============================================

-- 注意：以下插入语句需要 POI 和 Goods 表中有对应的数据
-- 请先确保以下 POI 存在：铁矿矿山、冶炼厂、钢材加工厂、家具厂
-- 请先确保以下 Goods 存在：铁矿石、钢坯、钢材、家具

-- 插入加工链
INSERT INTO processing_chain (chain_code, chain_name, status, description, yield_rate)
VALUES ('CHAIN_STEEL_FURNITURE_001', '钢铁→家具加工链', 'ACTIVE', 
        '铁矿→冶炼→钢材加工→家具制造', 0.60)
ON DUPLICATE KEY UPDATE chain_name = VALUES(chain_name);

-- 插入工序（需要先有 POI 和 Goods 数据）
-- 这里使用子查询来获取 POI 和 Goods 的 ID
INSERT INTO processing_stage (chain_id, stage_order, stage_name, poi_id, input_goods_sku, output_goods_sku, 
                              output_weight_ratio, processing_time_minutes, max_capacity_per_cycle)
SELECT 
    pc.id as chain_id,
    1 as stage_order,
    '铁矿开采' as stage_name,
    (SELECT id FROM POI WHERE poi_type = 'IRON_MINE' LIMIT 1) as poi_id,
    NULL as input_goods_sku,
    'RAW_IRON_ORE' as output_goods_sku,
    1.0 as output_weight_ratio,
    0 as processing_time_minutes,
    100.0 as max_capacity_per_cycle
FROM processing_chain pc
WHERE pc.chain_code = 'CHAIN_STEEL_FURNITURE_001'
ON DUPLICATE KEY UPDATE stage_name = VALUES(stage_name);

INSERT INTO processing_stage (chain_id, stage_order, stage_name, poi_id, input_goods_sku, output_goods_sku, 
                              output_weight_ratio, processing_time_minutes, max_capacity_per_cycle)
SELECT 
    pc.id as chain_id,
    2 as stage_order,
    '钢铁冶炼' as stage_name,
    (SELECT id FROM POI WHERE poi_type = 'STEEL_MILL' LIMIT 1) as poi_id,
    'RAW_IRON_ORE' as input_goods_sku,
    'SEMIF_STEEL_BILLET' as output_goods_sku,
    0.80 as output_weight_ratio,
    90 as processing_time_minutes,
    100.0 as max_capacity_per_cycle
FROM processing_chain pc
WHERE pc.chain_code = 'CHAIN_STEEL_FURNITURE_001'
ON DUPLICATE KEY UPDATE stage_name = VALUES(stage_name);

INSERT INTO processing_stage (chain_id, stage_order, stage_name, poi_id, input_goods_sku, output_goods_sku, 
                              output_weight_ratio, processing_time_minutes, max_capacity_per_cycle)
SELECT 
    pc.id as chain_id,
    3 as stage_order,
    '钢材加工' as stage_name,
    (SELECT id FROM POI WHERE poi_type = 'STEEL_PROCESSING_PLANT' LIMIT 1) as poi_id,
    'SEMIF_STEEL_BILLET' as input_goods_sku,
    'SEMIF_STEEL_MATERIAL' as output_goods_sku,
    0.75 as output_weight_ratio,
    60 as processing_time_minutes,
    80.0 as max_capacity_per_cycle
FROM processing_chain pc
WHERE pc.chain_code = 'CHAIN_STEEL_FURNITURE_001'
ON DUPLICATE KEY UPDATE stage_name = VALUES(stage_name);

INSERT INTO processing_stage (chain_id, stage_order, stage_name, poi_id, input_goods_sku, output_goods_sku, 
                              output_weight_ratio, processing_time_minutes, max_capacity_per_cycle)
SELECT 
    pc.id as chain_id,
    4 as stage_order,
    '家具制造' as stage_name,
    (SELECT id FROM POI WHERE poi_type = 'FURNITURE_FACTORY' LIMIT 1) as poi_id,
    'SEMIF_STEEL_MATERIAL' as input_goods_sku,
    'PROD_FURNITURE' as output_goods_sku,
    1.0 as output_weight_ratio,
    45 as processing_time_minutes,
    60.0 as max_capacity_per_cycle
FROM processing_chain pc
WHERE pc.chain_code = 'CHAIN_STEEL_FURNITURE_001'
ON DUPLICATE KEY UPDATE stage_name = VALUES(stage_name);

-- ============================================
-- 查询语句示例
-- ============================================

-- 查看所有加工链
-- SELECT * FROM processing_chain;

-- 查看某加工链的工序
-- SELECT ps.*, p.name as poi_name 
-- FROM processing_stage ps 
-- JOIN POI p ON ps.poi_id = p.id 
-- WHERE ps.chain_id = 1 
-- ORDER BY ps.stage_order;

-- 查看加工订单状态
-- SELECT po.*, pc.chain_name 
-- FROM processing_order po 
-- JOIN processing_chain pc ON po.chain_id = pc.id 
-- ORDER BY po.order_time DESC;

-- 查看订单的加工进度
-- SELECT pt.*, ps.stage_name, p.name as poi_name 
-- FROM processing_task pt 
-- JOIN processing_stage ps ON pt.stage_id = ps.id 
-- JOIN POI p ON ps.poi_id = p.id 
-- WHERE pt.order_id = 1 
-- ORDER BY ps.stage_order;
