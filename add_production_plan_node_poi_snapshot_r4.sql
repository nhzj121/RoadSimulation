-- R4: 固化每个计划节点本次选中的 POI，避免后续修改加工链模板影响历史计划。
SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'production_plan_node'
      AND column_name = 'selected_poi_id'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE production_plan_node ADD COLUMN selected_poi_id BIGINT NULL AFTER stage_id',
    'SELECT 1'
);
PREPARE statement FROM @ddl;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SET @constraint_exists = (
    SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE table_schema = DATABASE()
      AND table_name = 'production_plan_node'
      AND constraint_name = 'fk_production_plan_node_selected_poi'
      AND constraint_type = 'FOREIGN KEY'
);
SET @ddl = IF(
    @constraint_exists = 0,
    'ALTER TABLE production_plan_node ADD CONSTRAINT fk_production_plan_node_selected_poi FOREIGN KEY (selected_poi_id) REFERENCES poi(id)',
    'SELECT 1'
);
PREPARE statement FROM @ddl;
EXECUTE statement;
DEALLOCATE PREPARE statement;

-- 旧计划只能回填当时加工链模板上的 POI；新计划由选择器写入真实快照。
UPDATE production_plan_node n
JOIN processing_stage s ON s.id = n.stage_id
SET n.selected_poi_id = s.poi_id
WHERE n.selected_poi_id IS NULL;

SET @index_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'production_plan_node'
      AND index_name = 'idx_production_plan_node_selected_poi'
);
SET @ddl = IF(
    @index_exists = 0,
    'CREATE INDEX idx_production_plan_node_selected_poi ON production_plan_node(selected_poi_id)',
    'SELECT 1'
);
PREPARE statement FROM @ddl;
EXECUTE statement;
DEALLOCATE PREPARE statement;
