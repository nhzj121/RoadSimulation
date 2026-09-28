-- R1: 为运单增加显式需求来源。执行前请确认当前 schema。
-- 旧数据无法完整区分人工与旧随机任务，因此先保守归为 MANUAL，再按可识别前缀修正。
SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'shipment'
      AND column_name = 'demand_source'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE shipment ADD COLUMN demand_source VARCHAR(20) NOT NULL DEFAULT ''MANUAL'' AFTER status',
    'SELECT 1'
);
PREPARE statement FROM @ddl;
EXECUTE statement;
DEALLOCATE PREPARE statement;

UPDATE shipment SET demand_source = 'PRODUCTION' WHERE ref_no LIKE 'PROD-%';
UPDATE shipment SET demand_source = 'EXPERIMENT' WHERE ref_no LIKE 'EXP\_%' OR ref_no LIKE 'VIS\_%';
