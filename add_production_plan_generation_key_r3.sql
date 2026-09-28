-- R3: 自动生产计划的数据库级幂等键。人工计划的两个字段保持 NULL。
SET @run_column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'production_plan'
      AND column_name = 'simulation_run_id'
);
SET @ddl = IF(
    @run_column_exists = 0,
    'ALTER TABLE production_plan ADD COLUMN simulation_run_id VARCHAR(64) NULL AFTER random_seed',
    'SELECT 1'
);
PREPARE statement FROM @ddl;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SET @round_column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'production_plan'
      AND column_name = 'generation_round'
);
SET @ddl = IF(
    @round_column_exists = 0,
    'ALTER TABLE production_plan ADD COLUMN generation_round INT NULL AFTER simulation_run_id',
    'SELECT 1'
);
PREPARE statement FROM @ddl;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SET @constraint_exists = (
    SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE table_schema = DATABASE()
      AND table_name = 'production_plan'
      AND constraint_name = 'uk_production_plan_generation'
      AND constraint_type = 'UNIQUE'
);
SET @ddl = IF(
    @constraint_exists = 0,
    'ALTER TABLE production_plan ADD CONSTRAINT uk_production_plan_generation UNIQUE (simulation_run_id, generation_round, chain_id)',
    'SELECT 1'
);
PREPARE statement FROM @ddl;
EXECUTE statement;
DEALLOCATE PREPARE statement;
