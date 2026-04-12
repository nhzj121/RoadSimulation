-- ============================================
-- 加工链架构 - 方案 A：直接修改 Shipment 表
-- 版本：V3.0 (简化版)
-- 日期：2026-03-28
-- 说明：直接在 Shipment 和 ShipmentItem 表中添加加工字段
-- ============================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================
-- 1. 修改 shipment 表，添加加工字段
-- ============================================

ALTER TABLE `shipment` 
ADD COLUMN `is_processing_shipment` TINYINT(1) DEFAULT 0 COMMENT '是否为加工运单',
ADD COLUMN `processing_chain_id` BIGINT COMMENT '加工链 ID',
ADD COLUMN `chain_code` VARCHAR(50) COMMENT '加工链编码',
ADD COLUMN `chain_name` VARCHAR(100) COMMENT '加工链名称',
ADD COLUMN `expected_yield_rate` DECIMAL(5,4) COMMENT '预期产出率',
ADD COLUMN `expected_output_weight` DECIMAL(10,2) COMMENT '预期输出重量',
ADD COLUMN `actual_output_weight` DECIMAL(10,2) COMMENT '实际输出重量',
ADD COLUMN `processing_status` VARCHAR(20) DEFAULT 'PENDING' COMMENT '加工状态：PENDING/IN_PROCESS/COMPLETED/CANCELLED',
ADD COLUMN `processing_start_time` DATETIME COMMENT '开始加工时间',
ADD COLUMN `processing_expected_finish_time` DATETIME COMMENT '预期完成时间',
ADD COLUMN `processing_actual_finish_time` DATETIME COMMENT '实际完成时间';

-- 添加索引
ALTER TABLE `shipment`
ADD INDEX `idx_processing_status` (`processing_status`),
ADD INDEX `idx_processing_chain` (`processing_chain_id`);

-- 添加外键
ALTER TABLE `shipment`
ADD CONSTRAINT `fk_shipment_processing_chain` 
FOREIGN KEY (`processing_chain_id`) REFERENCES `processing_chain` (`id`);

-- ============================================
-- 2. 修改 shipment_item 表，添加加工字段
-- ============================================

ALTER TABLE `shipment_item`
ADD COLUMN `stage_id` BIGINT COMMENT '工序 ID',
ADD COLUMN `stage_order` INT COMMENT '工序顺序',
ADD COLUMN `stage_name` VARCHAR(100) COMMENT '工序名称',
ADD COLUMN `processing_poi_id` BIGINT COMMENT '加工点 POI ID',
ADD COLUMN `processing_status` VARCHAR(20) DEFAULT 'WAITING' COMMENT '加工状态：WAITING/PROCESSING/COMPLETED',
ADD COLUMN `processed_weight` DECIMAL(10,2) COMMENT '已加工重量',
ADD COLUMN `progress_percent` INT DEFAULT 0 COMMENT '加工进度 0-100',
ADD COLUMN `processing_start_time` DATETIME COMMENT '开始加工时间',
ADD COLUMN `processing_end_time` DATETIME COMMENT '完成加工时间',
ADD COLUMN `inbound_assignment_id` BIGINT COMMENT '原料运入任务 ID',
ADD COLUMN `outbound_assignment_id` BIGINT COMMENT '成品运出任务 ID';

-- 添加索引
ALTER TABLE `shipment_item`
ADD INDEX `idx_item_stage` (`stage_id`),
ADD INDEX `idx_item_processing_status` (`processing_status`),
ADD INDEX `idx_inbound_assignment` (`inbound_assignment_id`),
ADD INDEX `idx_outbound_assignment` (`outbound_assignment_id`);

-- 添加外键
ALTER TABLE `shipment_item`
ADD CONSTRAINT `fk_item_processing_stage` 
FOREIGN KEY (`stage_id`) REFERENCES `processing_stage` (`id`),
ADD CONSTRAINT `fk_item_processing_poi` 
FOREIGN KEY (`processing_poi_id`) REFERENCES `poi` (`id`),
ADD CONSTRAINT `fk_item_inbound_assignment` 
FOREIGN KEY (`inbound_assignment_id`) REFERENCES `assignment` (`id`),
ADD CONSTRAINT `fk_item_outbound_assignment` 
FOREIGN KEY (`outbound_assignment_id`) REFERENCES `assignment` (`id`);

-- ============================================
-- 3. 删除旧的扩展表（如果存在）
-- ============================================

DROP TABLE IF EXISTS `processing_shipment_ext`;
DROP TABLE IF EXISTS `processing_item_ext`;

-- ============================================
-- 4. 创建视图（可选）
-- ============================================

-- 加工运单视图
DROP VIEW IF EXISTS `v_processing_shipment`;
CREATE VIEW `v_processing_shipment` AS
SELECT 
    s.id,
    s.ref_no,
    s.cargo_type,
    s.status AS shipment_status,
    s.is_processing_shipment,
    s.processing_chain_id,
    s.chain_code,
    s.chain_name,
    s.expected_yield_rate,
    s.expected_output_weight,
    s.actual_output_weight,
    s.processing_status,
    s.processing_start_time,
    s.processing_expected_finish_time,
    s.processing_actual_finish_time,
    s.origin_poi_id,
    s.dest_poi_id,
    s.total_weight,
    s.created_at
FROM shipment s
WHERE s.is_processing_shipment = 1 OR s.cargo_type = 'PROCESSING';

-- 加工物料项视图
DROP VIEW IF EXISTS `v_processing_item`;
CREATE VIEW `v_processing_item` AS
SELECT 
    si.id,
    si.shipment_id,
    si.name,
    si.sku,
    si.weight,
    si.status AS shipment_item_status,
    si.stage_id,
    si.stage_order,
    si.stage_name,
    si.processing_poi_id,
    poi.name AS poi_name,
    si.processing_status,
    si.processed_weight,
    si.progress_percent,
    si.processing_start_time,
    si.processing_end_time,
    si.inbound_assignment_id,
    si.outbound_assignment_id,
    a1.status AS inbound_status,
    a2.status AS outbound_status
FROM shipment_item si
LEFT JOIN poi poi ON si.processing_poi_id = poi.id
LEFT JOIN assignment a1 ON si.inbound_assignment_id = a1.id
LEFT JOIN assignment a2 ON si.outbound_assignment_id = a2.id
WHERE si.stage_id IS NOT NULL;

-- ============================================
-- 完成提示
-- ============================================
SELECT '加工链表结构修改完成（方案 A：直接添加字段）' AS message;
SELECT 'shipment 表已添加加工字段' AS message;
SELECT 'shipment_item 表已添加加工字段' AS message;
SELECT '旧扩展表已删除' AS message;

SET FOREIGN_KEY_CHECKS = 1;
