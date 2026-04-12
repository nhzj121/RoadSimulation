-- ============================================================
-- Y 形加工链（多链合并）数据库迁移脚本
-- 执行日期：2026-03-28
-- ============================================================

-- 使用数据库
USE vehicle_scheduler;

-- ============================================================
-- 1. 创建加工链前驱关系表
-- ============================================================
CREATE TABLE IF NOT EXISTS processing_chain_predecessors (
    chain_id BIGINT NOT NULL COMMENT '加工链 ID',
    predecessor_chain_id BIGINT NOT NULL COMMENT '前驱加工链 ID',
    PRIMARY KEY (chain_id, predecessor_chain_id),
    FOREIGN KEY (chain_id) REFERENCES processing_chain(id) ON DELETE CASCADE,
    FOREIGN KEY (predecessor_chain_id) REFERENCES processing_chain(id) ON DELETE CASCADE,
    INDEX idx_predecessor_chain (predecessor_chain_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='加工链前驱关系表（Y 形加工链）';

-- ============================================================
-- 2. 创建运单上游关系表
-- ============================================================
CREATE TABLE IF NOT EXISTS shipment_upstream_relations (
    shipment_id BIGINT NOT NULL COMMENT '运单 ID',
    upstream_shipment_id BIGINT NOT NULL COMMENT '上游运单 ID',
    PRIMARY KEY (shipment_id, upstream_shipment_id),
    FOREIGN KEY (shipment_id) REFERENCES shipment(id) ON DELETE CASCADE,
    FOREIGN KEY (upstream_shipment_id) REFERENCES shipment(id) ON DELETE CASCADE,
    INDEX idx_upstream_shipment (upstream_shipment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运单上游关系表（Y 形加工链）';

-- ============================================================
-- 3. 加工链表新增字段
-- ============================================================
ALTER TABLE processing_chain 
ADD COLUMN IF NOT EXISTS merge_stage_id BIGINT COMMENT '合并工序 ID' AFTER predecessor_chain_ids,
ADD COLUMN IF NOT EXISTS input_materials TEXT COMMENT '输入物料 JSON' AFTER merge_stage_id;

-- ============================================================
-- 4. 添加索引（如果不存在）
-- ============================================================
-- 为 processing_chain 表添加索引
CREATE INDEX IF NOT EXISTS idx_chain_predecessor ON processing_chain_predecessors(chain_id);
CREATE INDEX IF NOT EXISTS idx_predecessor ON processing_chain_predecessors(predecessor_chain_id);

-- 为 shipment_upstream_relations 表添加索引
CREATE INDEX IF NOT EXISTS idx_shipment_upstream ON shipment_upstream_relations(shipment_id);
CREATE INDEX IF NOT EXISTS idx_upstream ON shipment_upstream_relations(upstream_shipment_id);

-- ============================================================
-- 5. 数据验证查询
-- ============================================================
-- 查询所有合并加工链
SELECT 
    pc.id,
    pc.chain_code,
    pc.chain_name,
    COUNT(pcp.predecessor_chain_id) as predecessor_count
FROM processing_chain pc
LEFT JOIN processing_chain_predecessors pcp ON pc.id = pcp.chain_id
GROUP BY pc.id, pc.chain_code, pc.chain_name
HAVING predecessor_count > 0;

-- 查询所有合并运单
SELECT 
    s.id,
    s.ref_no,
    s.chain_code,
    COUNT(sur.upstream_shipment_id) as upstream_count
FROM shipment s
LEFT JOIN shipment_upstream_relations sur ON s.id = sur.shipment_id
WHERE s.is_processing_shipment = 1
GROUP BY s.id, s.ref_no, s.chain_code
HAVING upstream_count > 0;

-- ============================================================
-- 迁移完成
-- ============================================================
