-- ============================================
-- 车辆 - 货物匹配功能数据库初始化脚本
-- 创建时间：2026-03-20
-- ============================================

-- 1. 创建车辆 - 货物匹配记录表
CREATE TABLE IF NOT EXISTS vehicle_goods_match (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID',
    
    -- 货物信息
    goods_id BIGINT NOT NULL COMMENT '货物 ID',
    goods_name VARCHAR(200) COMMENT '货物名称',
    goods_sku VARCHAR(100) COMMENT '货物 SKU',
    
    -- 车辆信息
    vehicle_id BIGINT NOT NULL COMMENT '车辆 ID',
    license_plate VARCHAR(25) COMMENT '车牌号',
    
    -- 匹配评分
    match_score DECIMAL(5,2) COMMENT '匹配评分 (0-100)',
    is_fully_matched TINYINT(1) DEFAULT 0 COMMENT '是否完全匹配',
    
    -- 匹配状态
    match_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '匹配状态：PENDING,CONFIRMED,REJECTED,CANCELLED,COMPLETED',
    
    -- 货物需求
    required_weight DECIMAL(10,2) COMMENT '货物重量需求 (吨)',
    required_volume DECIMAL(10,2) COMMENT '货物体积需求 (m³)',
    
    -- 车辆能力
    vehicle_load_capacity DECIMAL(10,2) COMMENT '车辆载重能力 (吨)',
    vehicle_cargo_volume DECIMAL(10,2) COMMENT '车辆容积能力 (m³)',
    
    -- 利用率
    weight_utilization DECIMAL(5,4) COMMENT '重量利用率',
    volume_utilization DECIMAL(5,4) COMMENT '容积利用率',
    
    -- 特殊要求
    require_temp_control TINYINT(1) DEFAULT 0 COMMENT '是否需要温控',
    hazmat_level VARCHAR(20) COMMENT '危险品等级',
    
    -- 路线信息
    origin_poi_id BIGINT COMMENT '出发地 POI ID',
    origin_poi_name VARCHAR(200) COMMENT '出发地名称',
    destination_poi_id BIGINT COMMENT '目的地 POI ID',
    destination_poi_name VARCHAR(200) COMMENT '目的地名称',
    
    -- 距离信息
    distance_km DECIMAL(10,2) COMMENT '距离 (公里)',
    
    -- 描述信息
    match_description VARCHAR(1000) COMMENT '匹配描述',
    
    -- 时间信息
    match_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '匹配时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    -- 操作人
    created_by VARCHAR(50) COMMENT '创建人',
    updated_by VARCHAR(50) COMMENT '更新人',
    
    -- 索引
    INDEX idx_match_goods_id (goods_id),
    INDEX idx_match_vehicle_id (vehicle_id),
    INDEX idx_match_time (match_time),
    INDEX idx_match_status (match_status),
    INDEX idx_origin_poi_id (origin_poi_id),
    INDEX idx_destination_poi_id (destination_poi_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车辆 - 货物匹配记录表';


-- 2. 为 vehicle 表添加新字段（如果不存在）
-- 温控设备支持
ALTER TABLE vehicle 
ADD COLUMN IF NOT EXISTS has_temp_control TINYINT(1) DEFAULT 0 COMMENT '是否有温控设备' AFTER current_volumn;

-- 危险品运输资质级别
ALTER TABLE vehicle 
ADD COLUMN IF NOT EXISTS hazmat_qualification VARCHAR(20) COMMENT '危险品运输资质级别' AFTER has_temp_control;

-- 专用车型标识
ALTER TABLE vehicle 
ADD COLUMN IF NOT EXISTS special_vehicle_type VARCHAR(50) COMMENT '专用车型标识' AFTER hazmat_qualification;


-- 3. 插入测试数据 - 车辆匹配记录示例
INSERT INTO vehicle_goods_match (
    goods_id, goods_name, goods_sku,
    vehicle_id, license_plate,
    match_score, is_fully_matched, match_status,
    required_weight, required_volume,
    vehicle_load_capacity, vehicle_cargo_volume,
    weight_utilization, volume_utilization,
    require_temp_control, hazmat_level,
    match_description, match_time
) VALUES 
(1, '水泥', 'CEMENT', 1, '甘 A·12345', 
 85.50, 1, 'PENDING',
 10.00, 5.00,
 15.00, 20.00,
 0.67, 0.25,
 0, '',
 '载重匹配 (需要 10.00 吨，车辆 15.00 吨); 容积匹配 (需要 5.00m³, 车辆 20.00m³)',
 NOW()),
 
(2, '蔬菜', 'VEGETABLE', 2, '甘 A·67890',
 92.00, 1, 'CONFIRMED',
 5.00, 2.00,
 8.00, 10.00,
 0.625, 0.20,
 1, '',
 '载重匹配 (需要 5.00 吨，车辆 8.00 吨); 车辆支持温控，满足要求',
 NOW()),
 
(3, '汽油', 'GASOLINE', 3, '甘 A·11111',
 88.00, 1, 'PENDING',
 5.00, 6.00,
 10.00, 15.00,
 0.50, 0.40,
 0, '3 类',
 '载重匹配 (需要 5.00 吨，车辆 10.00 吨); 车辆具有危险品运输资质：3 类',
 NOW());


-- 4. 查询语句示例
-- 查看所有匹配记录
-- SELECT * FROM vehicle_goods_match ORDER BY match_time DESC;

-- 查看待确认的匹配记录
-- SELECT * FROM vehicle_goods_match WHERE match_status = 'PENDING';

-- 查看某货物的匹配历史
-- SELECT * FROM vehicle_goods_match WHERE goods_id = 1 ORDER BY match_time DESC;

-- 查看某车辆的匹配历史
-- SELECT * FROM vehicle_goods_match WHERE vehicle_id = 1 ORDER BY match_time DESC;

-- 统计匹配成功率
-- SELECT 
--     COUNT(*) as total_matches,
--     SUM(CASE WHEN is_fully_matched = 1 THEN 1 ELSE 0 END) as fully_matched_count,
--     AVG(match_score) as avg_score
-- FROM vehicle_goods_match;
