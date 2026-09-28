-- R2: 显式记录计划节点角色。旧运行数据应先通过仿真 reset 清理。
SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'production_plan_node'
      AND column_name = 'node_role'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE production_plan_node ADD COLUMN node_role VARCHAR(20) NOT NULL DEFAULT ''PROCESSING'' AFTER planned_output_weight',
    'SELECT 1'
);
PREPARE statement FROM @ddl;
EXECUTE statement;
DEALLOCATE PREPARE statement;
