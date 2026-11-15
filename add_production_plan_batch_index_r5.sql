-- Apply after R3, while simulation workers are stopped. Repeatable on MySQL/MariaDB.
SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'production_plan'
      AND column_name = 'generation_batch_index'
);
SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE production_plan ADD COLUMN generation_batch_index INT NULL AFTER generation_round',
    'SELECT 1');
PREPARE statement FROM @ddl;
EXECUTE statement;
DEALLOCATE PREPARE statement;

UPDATE production_plan SET generation_batch_index = 0
WHERE simulation_run_id IS NOT NULL AND generation_round IS NOT NULL AND generation_batch_index IS NULL;

SET @index_columns = (
    SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'production_plan'
      AND index_name = 'uk_production_plan_generation' AND non_unique = 0
);
SET @ddl = CASE
    WHEN @index_columns = 'simulation_run_id,generation_round,chain_id,generation_batch_index' THEN 'SELECT 1'
    WHEN @index_columns IS NULL THEN
        'ALTER TABLE production_plan ADD UNIQUE KEY uk_production_plan_generation (simulation_run_id,generation_round,chain_id,generation_batch_index)'
    ELSE
        'ALTER TABLE production_plan DROP INDEX uk_production_plan_generation, ADD UNIQUE KEY uk_production_plan_generation (simulation_run_id,generation_round,chain_id,generation_batch_index)'
END;
PREPARE statement FROM @ddl;
EXECUTE statement;
DEALLOCATE PREPARE statement;
